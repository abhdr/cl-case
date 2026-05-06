package com.inghubscl.ticketing.event.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record EventCreateRequest(
    @NotBlank @Size(max = 255) String title,
    @NotBlank @Size(max = 255) String venue,
    @NotNull @Future Instant startsAt,
    @NotNull @Future Instant endsAt,
    @NotNull @Min(1) Integer capacity) {}
