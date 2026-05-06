package com.inghubscl.ticketing.event.dto;

import java.time.Instant;
import java.util.UUID;

public record EventResponse(
    UUID id,
    UUID ownerId,
    String title,
    String venue,
    Instant startsAt,
    Instant endsAt,
    int capacity,
    int reservedSeats,
    int availableSeats,
    boolean published,
    long version,
    Instant createdAt) {}
