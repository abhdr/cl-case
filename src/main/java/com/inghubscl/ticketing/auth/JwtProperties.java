package com.inghubscl.ticketing.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.jwt")
public record JwtProperties(
    @NotBlank String secret,
    @NotNull Duration accessTtl,
    @NotNull Duration refreshTtl,
    @NotBlank String issuer) {}
