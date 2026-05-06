package com.inghubscl.ticketing.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.inghubscl.ticketing.event.Event;
import com.inghubscl.ticketing.event.EventRepository;
import com.inghubscl.ticketing.exception.CapacityExceededException;
import com.inghubscl.ticketing.user.Role;
import com.inghubscl.ticketing.user.User;
import com.inghubscl.ticketing.user.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies that {@link ReservationService#create} is safe under concurrent load: with capacity=10
 * and 15 parallel reservation attempts, exactly 10 succeed and the remaining 5 fail with {@link
 * CapacityExceededException}. Backed by Event.tryReserve atomic UPDATE.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReservationConcurrencyIT {

  private static final int CAPACITY = 10;
  private static final int THREADS = 15;

  @Autowired ReservationService reservationService;
  @Autowired EventRepository eventRepository;
  @Autowired ReservationRepository reservationRepository;
  @Autowired UserRepository userRepository;

  @Test
  void overSubscribedEventAllowsOnlyTenReservations() throws Exception {
    Setup setup = seed();

    AtomicInteger successes = new AtomicInteger();
    AtomicInteger capacityFailures = new AtomicInteger();
    AtomicInteger otherFailures = new AtomicInteger();

    ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(THREADS);

    try {
      for (int i = 0; i < THREADS; i++) {
        final UUID userId = setup.userIds[i];
        pool.submit(
            () -> {
              try {
                start.await();
                reservationService.create(setup.eventId, 1, userId);
                successes.incrementAndGet();
              } catch (CapacityExceededException ex) {
                capacityFailures.incrementAndGet();
              } catch (Exception ex) {
                otherFailures.incrementAndGet();
              } finally {
                done.countDown();
              }
            });
      }

      start.countDown(); // race start
      assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
    } finally {
      pool.shutdownNow();
    }

    assertThat(otherFailures.get()).as("unexpected failures").isZero();
    assertThat(successes.get()).isEqualTo(CAPACITY);
    assertThat(capacityFailures.get()).isEqualTo(THREADS - CAPACITY);

    Event reloaded = eventRepository.findById(setup.eventId).orElseThrow();
    assertThat(reloaded.getReservedSeats()).isEqualTo(CAPACITY);
    assertThat(
            reservationRepository.countByEventIdAndStatus(setup.eventId, ReservationStatus.PENDING))
        .isEqualTo(CAPACITY);
  }

  @Transactional
  Setup seed() {
    UUID[] userIds = new UUID[THREADS];
    for (int i = 0; i < THREADS; i++) {
      User u =
          new User(
              "concurrency-user-" + i + "-" + UUID.randomUUID() + "@example.com",
              "x",
              Set.of(Role.CUSTOMER));
      userIds[i] = userRepository.saveAndFlush(u).getId();
    }
    UUID ownerId = userIds[0];
    Event event =
        new Event(
            ownerId,
            "Concurrent Concert",
            "Test Arena",
            Instant.now().plus(1, ChronoUnit.DAYS),
            Instant.now().plus(2, ChronoUnit.DAYS),
            CAPACITY);
    event.publish();
    Event savedEvent = eventRepository.saveAndFlush(event);
    return new Setup(savedEvent.getId(), userIds);
  }

  private record Setup(UUID eventId, UUID[] userIds) {}
}
