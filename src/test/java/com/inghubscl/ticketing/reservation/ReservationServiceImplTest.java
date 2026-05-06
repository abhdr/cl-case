package com.inghubscl.ticketing.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.inghubscl.ticketing.event.Event;
import com.inghubscl.ticketing.event.EventRepository;
import com.inghubscl.ticketing.exception.CapacityExceededException;
import com.inghubscl.ticketing.exception.EventNotPublishedException;
import com.inghubscl.ticketing.exception.ForbiddenException;
import com.inghubscl.ticketing.exception.ResourceNotFoundException;
import com.inghubscl.ticketing.reservation.dto.ReservationResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationServiceImplTest {

  @Mock ReservationRepository reservationRepository;
  @Mock EventRepository eventRepository;
  @Mock ReservationMapper reservationMapper;

  ReservationServiceImpl service;

  UUID userId = UUID.randomUUID();
  UUID otherUser = UUID.randomUUID();
  UUID eventId = UUID.randomUUID();

  @BeforeEach
  void init() {
    service = new ReservationServiceImpl(reservationRepository, eventRepository, reservationMapper);
    lenient()
        .when(reservationMapper.toResponse(any(Reservation.class)))
        .thenReturn(
            new ReservationResponse(
                UUID.randomUUID(), eventId, userId, ReservationStatus.PENDING, 1, Instant.now()));
  }

  @Test
  void createSucceedsWhenAtomicUpdateReturnsOne() {
    when(eventRepository.tryReserve(eq(eventId), eq(2))).thenReturn(1);
    when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

    service.create(eventId, 2, userId);

    verify(reservationRepository).save(any(Reservation.class));
  }

  @Test
  void createThrowsResourceNotFoundWhenEventMissing() {
    when(eventRepository.tryReserve(any(UUID.class), anyInt())).thenReturn(0);
    when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.create(eventId, 1, userId))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(reservationRepository, never()).save(any());
  }

  @Test
  void createThrowsEventNotPublishedWhenDraft() {
    Event event = publishedEvent(false, 100, 0);
    when(eventRepository.tryReserve(any(UUID.class), anyInt())).thenReturn(0);
    when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

    assertThatThrownBy(() -> service.create(eventId, 1, userId))
        .isInstanceOf(EventNotPublishedException.class);
  }

  @Test
  void createThrowsCapacityExceededWhenFull() {
    Event event = publishedEvent(true, 10, 10);
    when(eventRepository.tryReserve(any(UUID.class), anyInt())).thenReturn(0);
    when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

    assertThatThrownBy(() -> service.create(eventId, 1, userId))
        .isInstanceOf(CapacityExceededException.class);
  }

  @Test
  void confirmTransitionsPendingToConfirmed() {
    Reservation r = new Reservation(eventId, userId, 1);
    when(reservationRepository.findById(any(UUID.class))).thenReturn(Optional.of(r));

    service.confirm(UUID.randomUUID(), userId, false);

    assertThat(r.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
  }

  @Test
  void confirmIsNoOpWhenAlreadyConfirmed() {
    Reservation r = new Reservation(eventId, userId, 1);
    r.confirm();
    when(reservationRepository.findById(any(UUID.class))).thenReturn(Optional.of(r));

    service.confirm(UUID.randomUUID(), userId, false);

    assertThat(r.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
  }

  @Test
  void confirmRejectsCancelledReservation() {
    Reservation r = new Reservation(eventId, userId, 1);
    r.cancel();
    when(reservationRepository.findById(any(UUID.class))).thenReturn(Optional.of(r));

    assertThatThrownBy(() -> service.confirm(UUID.randomUUID(), userId, false))
        .hasMessageContaining("cancelled");
  }

  @Test
  void cancelReleasesSeatsForActiveReservation() {
    Reservation r = new Reservation(eventId, userId, 3);
    when(reservationRepository.findById(any(UUID.class))).thenReturn(Optional.of(r));

    service.cancel(UUID.randomUUID(), userId, false);

    assertThat(r.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    verify(eventRepository, times(1)).releaseSeats(eventId, 3);
  }

  @Test
  void cancelIsIdempotentForAlreadyCancelled() {
    Reservation r = new Reservation(eventId, userId, 3);
    r.cancel();
    when(reservationRepository.findById(any(UUID.class))).thenReturn(Optional.of(r));

    service.cancel(UUID.randomUUID(), userId, false);

    verify(eventRepository, never()).releaseSeats(any(UUID.class), anyInt());
  }

  @Test
  void cancelRejectsForeignCaller() {
    Reservation r = new Reservation(eventId, userId, 1);
    when(reservationRepository.findById(any(UUID.class))).thenReturn(Optional.of(r));

    assertThatThrownBy(() -> service.cancel(UUID.randomUUID(), otherUser, false))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void cancelAllowsAdminEvenForForeignReservation() {
    Reservation r = new Reservation(eventId, userId, 2);
    when(reservationRepository.findById(any(UUID.class))).thenReturn(Optional.of(r));

    service.cancel(UUID.randomUUID(), otherUser, true);

    assertThat(r.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
  }

  @Test
  void findByIdReturnsResponseForOwner() {
    Reservation r = new Reservation(eventId, userId, 1);
    when(reservationRepository.findById(any(UUID.class))).thenReturn(Optional.of(r));

    ReservationResponse response = service.findById(UUID.randomUUID(), userId, false);

    assertThat(response).isNotNull();
  }

  @Test
  void findByIdRejectsForeignCaller() {
    Reservation r = new Reservation(eventId, userId, 1);
    when(reservationRepository.findById(any(UUID.class))).thenReturn(Optional.of(r));

    assertThatThrownBy(() -> service.findById(UUID.randomUUID(), otherUser, false))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void listForUserDelegatesToRepository() {
    when(reservationRepository.findAllByUserId(userId)).thenReturn(java.util.List.of());

    var result = service.listForUser(userId);

    assertThat(result).isEmpty();
  }

  private Event publishedEvent(boolean published, int capacity, int reserved) {
    Event event =
        new Event(
            UUID.randomUUID(),
            "t",
            "v",
            Instant.now().plus(1, ChronoUnit.DAYS),
            Instant.now().plus(2, ChronoUnit.DAYS),
            capacity);
    if (published) {
      event.publish();
    }
    try {
      var f = Event.class.getDeclaredField("reservedSeats");
      f.setAccessible(true);
      f.setInt(event, reserved);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
    return event;
  }
}
