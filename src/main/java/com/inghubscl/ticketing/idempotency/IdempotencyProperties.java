package com.inghubscl.ticketing.idempotency;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.idempotency")
public record IdempotencyProperties(@NotNull Duration ttl, @NotNull Duration cleanupInterval) {}
