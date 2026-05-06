package com.inghubscl.ticketing.event;

import com.inghubscl.ticketing.audit.Audited;
import com.inghubscl.ticketing.common.PagedResponse;
import com.inghubscl.ticketing.event.dto.EventCreateRequest;
import com.inghubscl.ticketing.event.dto.EventResponse;
import com.inghubscl.ticketing.event.dto.EventUpdateRequest;
import com.inghubscl.ticketing.event.dto.PublicEventResponse;
import com.inghubscl.ticketing.exception.ForbiddenException;
import com.inghubscl.ticketing.exception.InvalidRequestException;
import com.inghubscl.ticketing.exception.ResourceNotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventServiceImpl implements EventService {

  private final EventRepository eventRepository;
  private final EventMapper eventMapper;

  public EventServiceImpl(EventRepository eventRepository, EventMapper eventMapper) {
    this.eventRepository = eventRepository;
    this.eventMapper = eventMapper;
  }

  @Override
  @Transactional
  @Audited(action = "event.create", resourceType = "event")
  public EventResponse create(EventCreateRequest request, UUID ownerId) {
    if (!request.endsAt().isAfter(request.startsAt())) {
      throw new InvalidRequestException("endsAt must be after startsAt");
    }
    Event event =
        new Event(
            ownerId,
            request.title(),
            request.venue(),
            request.startsAt(),
            request.endsAt(),
            request.capacity());
    Event saved = eventRepository.save(event);
    return eventMapper.toResponse(saved);
  }

  @Override
  @Transactional
  @Audited(action = "event.update", resourceType = "event")
  public EventResponse update(
      UUID id, EventUpdateRequest request, UUID callerId, boolean callerIsAdmin) {
    Event event = loadOrThrow(id);
    requireOwnerOrAdmin(event, callerId, callerIsAdmin);
    if (!request.endsAt().isAfter(request.startsAt())) {
      throw new InvalidRequestException("endsAt must be after startsAt");
    }
    if (request.capacity() < event.getReservedSeats()) {
      throw new InvalidRequestException(
          "Cannot reduce capacity below reserved seats (" + event.getReservedSeats() + ")");
    }
    event.setTitle(request.title());
    event.setVenue(request.venue());
    event.setStartsAt(request.startsAt());
    event.setEndsAt(request.endsAt());
    event.setCapacity(request.capacity());
    return eventMapper.toResponse(event);
  }

  @Override
  @Transactional
  @Audited(action = "event.publish", resourceType = "event")
  public EventResponse publish(UUID id, UUID callerId, boolean callerIsAdmin) {
    Event event = loadOrThrow(id);
    requireOwnerOrAdmin(event, callerId, callerIsAdmin);
    if (!event.isPublished()) {
      event.publish();
    }
    return eventMapper.toResponse(event);
  }

  @Override
  @Transactional(readOnly = true)
  public EventResponse findById(UUID id) {
    return eventMapper.toResponse(loadOrThrow(id));
  }

  @Override
  @Transactional(readOnly = true)
  public PagedResponse<EventResponse> list(UUID ownerId, Pageable pageable) {
    Specification<Event> spec = (root, q, cb) -> cb.conjunction();
    if (ownerId != null) {
      spec = spec.and((root, q, cb) -> cb.equal(root.get("ownerId"), ownerId));
    }
    Page<EventResponse> page = eventRepository.findAll(spec, pageable).map(eventMapper::toResponse);
    return PagedResponse.from(page);
  }

  @Override
  @Transactional(readOnly = true)
  public PagedResponse<PublicEventResponse> searchPublic(
      Instant from, Instant to, String query, Pageable pageable) {
    Specification<Event> spec =
        (root, q, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          predicates.add(cb.isTrue(root.get("published")));
          if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("startsAt"), from));
          }
          if (to != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("startsAt"), to));
          }
          if (query != null && !query.isBlank()) {
            String pattern = "%" + query.toLowerCase() + "%";
            predicates.add(
                cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("venue")), pattern)));
          }
          return cb.and(predicates.toArray(new Predicate[0]));
        };
    Page<PublicEventResponse> page =
        eventRepository.findAll(spec, pageable).map(eventMapper::toPublicResponse);
    return PagedResponse.from(page);
  }

  private Event loadOrThrow(UUID id) {
    return eventRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Event", id));
  }

  private void requireOwnerOrAdmin(Event event, UUID callerId, boolean callerIsAdmin) {
    if (callerIsAdmin) {
      return;
    }
    if (!event.getOwnerId().equals(callerId)) {
      throw new ForbiddenException("Only the event owner or an admin may modify this event");
    }
  }
}
