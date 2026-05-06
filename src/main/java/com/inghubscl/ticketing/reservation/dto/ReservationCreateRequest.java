package com.inghubscl.ticketing.reservation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReservationCreateRequest(@NotNull @Min(1) @Max(100) Integer seats) {}
