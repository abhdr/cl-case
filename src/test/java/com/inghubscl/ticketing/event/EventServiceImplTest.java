package com.inghubscl.ticketing.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.inghubscl.ticketing.event.dto.EventCreateRequest;
import com.inghubscl.ticketing.event.dto.EventResponse;
import com.inghubscl.ticketing.event.dto.EventUpdateRequest;
import com.inghubscl.ticketing.event.dto.PublicEventResponse;
import com.inghubscl.ticketing.exception.ForbiddenException;
import com.inghubscl.ticketing.exception.InvalidRequestException;
import com.inghubscl.ticketing.exception.ResourceNotFoundException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class EventServiceImplTest {

  @Mock EventRepository eventRepository;
  @Mock EventMapper eventMapper;

  EventServiceImpl service;

  UUID owner = UUID.randomUUID();
  UUID otherUser = UUID.randomUUID();
  Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
  Instant end = Instant.now().plus(2, ChronoUnit.DAYS);

  @BeforeEach
  void init() {
    service = new EventServiceImpl(eventRepository, eventMapper);
    lenient().when(eventMapper.toResponse(any(Event.class))).thenReturn(sampleResponse());
    lenient()
        .when(eventMapper.toPublicResponse(any(Event.class)))
        .thenReturn(samplePublicResponse());
  }

  @Test
  void createPersistsAndMaps() {
    when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

    EventResponse response =
        service.create(new EventCreateRequest("t", "v", start, end, 50), owner);

    assertThat(response).isNotNull();
  }

  @Test
  void createRejectsEndBeforeStart() {
    assertThatThrownBy(
            () -> service.create(new EventCreateRequest("t", "v", end, start, 50), owner))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("endsAt must be after startsAt");
  }

  @Test
  void updateAllowsOwner() {
    Event event = new Event(owner, "old", "old", start, end, 100);
    when(eventRepository.findById(any(UUID.class))).thenReturn(Optional.of(event));

    service.update(
        UUID.randomUUID(),
        new EventUpdateRequest("new", "new venue", start, end, 100),
        owner,
        false);

    assertThat(event.getTitle()).isEqualTo("new");
    assertThat(event.getVenue()).isEqualTo("new venue");
  }

  @Test
  void updateAllowsAdminOverride() {
    Event event = new Event(owner, "old", "old", start, end, 100);
    when(eventRepository.findById(any(UUID.class))).thenReturn(Optional.of(event));

    service.update(
        UUID.randomUUID(), new EventUpdateRequest("new", "v", start, end, 100), otherUser, true);

    assertThat(event.getTitle()).isEqualTo("new");
  }

  @Test
  void updateRejectsForeignCaller() {
    Event event = new Event(owner, "t", "v", start, end, 100);
    when(eventRepository.findById(any(UUID.class))).thenReturn(Optional.of(event));

    assertThatThrownBy(
            () ->
                service.update(
                    UUID.randomUUID(),
                    new EventUpdateRequest("x", "y", start, end, 100),
                    otherUser,
                    false))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void updateRejectsCapacityBelowReserved() {
    Event event = new Event(owner, "t", "v", start, end, 100);
    setReservedSeats(event, 30);
    when(eventRepository.findById(any(UUID.class))).thenReturn(Optional.of(event));

    assertThatThrownBy(
            () ->
                service.update(
                    UUID.randomUUID(),
                    new EventUpdateRequest("t", "v", start, end, 20),
                    owner,
                    false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Cannot reduce capacity");
  }

  @Test
  void publishMarksEventPublished() {
    Event event = new Event(owner, "t", "v", start, end, 50);
    when(eventRepository.findById(any(UUID.class))).thenReturn(Optional.of(event));

    service.publish(UUID.randomUUID(), owner, false);

    assertThat(event.isPublished()).isTrue();
  }

  @Test
  void publishIsIdempotentWhenAlreadyPublished() {
    Event event = new Event(owner, "t", "v", start, end, 50);
    event.publish();
    when(eventRepository.findById(any(UUID.class))).thenReturn(Optional.of(event));

    service.publish(UUID.randomUUID(), owner, false);

    assertThat(event.isPublished()).isTrue();
  }

  @Test
  void findByIdThrowsWhenMissing() {
    when(eventRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findById(UUID.randomUUID()))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void listFiltersByOwnerWhenProvided() {
    when(eventRepository.findAll(any(Specification.class), any(PageRequest.class)))
        .thenReturn(new PageImpl<>(java.util.List.of()));

    service.list(owner, PageRequest.of(0, 10));
    service.list(null, PageRequest.of(0, 10));
  }

  @Test
  void searchPublicAppliesFilters() {
    when(eventRepository.findAll(any(Specification.class), any(PageRequest.class)))
        .thenReturn(new PageImpl<>(java.util.List.of()));

    service.searchPublic(
        Instant.now(), Instant.now().plusSeconds(3600), "music", PageRequest.of(0, 10));
    service.searchPublic(null, null, null, PageRequest.of(0, 10));
    service.searchPublic(null, null, "  ", PageRequest.of(0, 10));
  }

  private static EventResponse sampleResponse() {
    return new EventResponse(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "t",
        "v",
        Instant.now(),
        Instant.now().plusSeconds(3600),
        100,
        0,
        100,
        false,
        0L,
        Instant.now());
  }

  private static PublicEventResponse samplePublicResponse() {
    return new PublicEventResponse(
        UUID.randomUUID(), "t", "v", Instant.now(), Instant.now().plusSeconds(3600), 100, 100);
  }

  // Test helper: bypass package-private setter absence by reflecting reservedSeats.
  private static void setReservedSeats(Event event, int value) {
    try {
      var f = Event.class.getDeclaredField("reservedSeats");
      f.setAccessible(true);
      f.setInt(event, value);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }
}
