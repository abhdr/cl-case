package com.inghubscl.ticketing.reservation;

import com.inghubscl.ticketing.reservation.dto.ReservationResponse;
import java.util.List;
import java.util.UUID;

public interface ReservationService {

  ReservationResponse create(UUID eventId, int seats, UUID userId);

  ReservationResponse confirm(UUID reservationId, UUID callerId, boolean callerIsAdmin);

  ReservationResponse cancel(UUID reservationId, UUID callerId, boolean callerIsAdmin);

  List<ReservationResponse> listForUser(UUID userId);

  ReservationResponse findById(UUID id, UUID callerId, boolean callerIsAdmin);
}
