package com.inghubscl.ticketing.reservation.dto;

import com.inghubscl.ticketing.reservation.ReservationStatus;
import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(
    UUID id, UUID eventId, UUID userId, ReservationStatus status, int seats, Instant createdAt) {}
