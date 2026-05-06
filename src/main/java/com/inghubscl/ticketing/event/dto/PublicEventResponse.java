package com.inghubscl.ticketing.event.dto;

import java.time.Instant;
import java.util.UUID;

public record PublicEventResponse(
    UUID id,
    String title,
    String venue,
    Instant startsAt,
    Instant endsAt,
    int capacity,
    int availableSeats) {}
