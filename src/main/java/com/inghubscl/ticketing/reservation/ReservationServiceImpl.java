package com.inghubscl.ticketing.reservation;

import com.inghubscl.ticketing.audit.Audited;
import com.inghubscl.ticketing.event.Event;
import com.inghubscl.ticketing.event.EventRepository;
import com.inghubscl.ticketing.exception.CapacityExceededException;
import com.inghubscl.ticketing.exception.EventNotPublishedException;
import com.inghubscl.ticketing.exception.ForbiddenException;
import com.inghubscl.ticketing.exception.ResourceNotFoundException;
import com.inghubscl.ticketing.reservation.dto.ReservationResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationServiceImpl implements ReservationService {

  private final ReservationRepository reservationRepository;
  private final EventRepository eventRepository;
  private final ReservationMapper reservationMapper;

  public ReservationServiceImpl(
      ReservationRepository reservationRepository,
      EventRepository eventRepository,
      ReservationMapper reservationMapper) {
    this.reservationRepository = reservationRepository;
    this.eventRepository = eventRepository;
    this.reservationMapper = reservationMapper;
  }

  @Override
  @Transactional
  @Audited(action = "reservation.create", resourceType = "reservation")
  public ReservationResponse create(UUID eventId, int seats, UUID userId) {
    int updated = eventRepository.tryReserve(eventId, seats);
    if (updated == 0) {
      // No row matched: distinguish the reason for the caller.
      Event event =
          eventRepository
              .findById(eventId)
              .orElseThrow(() -> new ResourceNotFoundException("Event", eventId));
      if (!event.isPublished()) {
        throw new EventNotPublishedException(eventId);
      }
      int available = event.getCapacity() - event.getReservedSeats();
      throw new CapacityExceededException(seats, available);
    }
    Reservation reservation = new Reservation(eventId, userId, seats);
    Reservation saved = reservationRepository.save(reservation);
    return reservationMapper.toResponse(saved);
  }

  @Override
  @Transactional
  @Audited(action = "reservation.confirm", resourceType = "reservation")
  public ReservationResponse confirm(UUID reservationId, UUID callerId, boolean callerIsAdmin) {
    Reservation reservation = loadOrThrow(reservationId);
    requireOwnerOrAdmin(reservation, callerId, callerIsAdmin);
    if (reservation.getStatus() == ReservationStatus.CANCELLED) {
      throw new IllegalStateTransition("Cannot confirm a cancelled reservation");
    }
    if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
      reservation.confirm();
    }
    return reservationMapper.toResponse(reservation);
  }

  @Override
  @Transactional
  @Audited(action = "reservation.cancel", resourceType = "reservation")
  public ReservationResponse cancel(UUID reservationId, UUID callerId, boolean callerIsAdmin) {
    Reservation reservation = loadOrThrow(reservationId);
    requireOwnerOrAdmin(reservation, callerId, callerIsAdmin);
    if (reservation.getStatus() == ReservationStatus.CANCELLED) {
      // Idempotent — do not release seats again.
      return reservationMapper.toResponse(reservation);
    }
    reservation.cancel();
    eventRepository.releaseSeats(reservation.getEventId(), reservation.getSeats());
    return reservationMapper.toResponse(reservation);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ReservationResponse> listForUser(UUID userId) {
    return reservationRepository.findAllByUserId(userId).stream()
        .map(reservationMapper::toResponse)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public ReservationResponse findById(UUID id, UUID callerId, boolean callerIsAdmin) {
    Reservation reservation = loadOrThrow(id);
    requireOwnerOrAdmin(reservation, callerId, callerIsAdmin);
    return reservationMapper.toResponse(reservation);
  }

  private Reservation loadOrThrow(UUID id) {
    return reservationRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Reservation", id));
  }

  private void requireOwnerOrAdmin(Reservation reservation, UUID callerId, boolean callerIsAdmin) {
    if (callerIsAdmin) {
      return;
    }
    if (!reservation.getUserId().equals(callerId)) {
      throw new ForbiddenException(
          "Only the reservation owner or an admin may modify this reservation");
    }
  }

  private static class IllegalStateTransition
      extends com.inghubscl.ticketing.exception.BusinessException {
    IllegalStateTransition(String message) {
      super(org.springframework.http.HttpStatus.CONFLICT, "Illegal State Transition", message);
    }
  }
}
