package com.inghubscl.ticketing.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.inghubscl.ticketing.user.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private static final String SECRET =
      "test-secret-must-be-at-least-256-bits-long-for-hs256-algorithm-padding-1234567890";
  private static final String OTHER_SECRET =
      "different-secret-also-at-least-256-bits-long-for-hs256-algorithm-padding-987654";

  private JwtServiceImpl service;

  @BeforeEach
  void setUp() {
    JwtProperties props =
        new JwtProperties(SECRET, Duration.ofMinutes(15), Duration.ofDays(7), "test-issuer");
    service = new JwtServiceImpl(props);
  }

  @Test
  void issuesValidAccessTokenWithExpectedClaims() {
    UUID userId = UUID.randomUUID();
    String email = "user@example.com";
    Set<Role> roles = Set.of(Role.ADMIN, Role.ORGANIZER);

    TokenPair pair = service.issue(userId, email, roles);
    Claims claims = service.parseAccessToken(pair.accessToken());

    assertThat(claims.getSubject()).isEqualTo(userId.toString());
    assertThat(claims.getIssuer()).isEqualTo("test-issuer");
    assertThat(claims.get("email", String.class)).isEqualTo(email);
    @SuppressWarnings("unchecked")
    List<String> claimedRoles = claims.get("roles", List.class);
    assertThat(claimedRoles).containsExactlyInAnyOrder("ADMIN", "ORGANIZER");
    assertThat(pair.expiresInSeconds()).isEqualTo(15 * 60L);
    assertThat(pair.refreshToken()).isNotBlank();
  }

  @Test
  void rejectsExpiredToken() {
    JwtProperties shortLived =
        new JwtProperties(SECRET, Duration.ofMillis(1), Duration.ofDays(7), "test-issuer");
    JwtServiceImpl shortService = new JwtServiceImpl(shortLived);
    TokenPair pair = shortService.issue(UUID.randomUUID(), "u@e.com", Set.of(Role.CUSTOMER));

    try {
      Thread.sleep(50);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    assertThatThrownBy(() -> shortService.parseAccessToken(pair.accessToken()))
        .isInstanceOf(ExpiredJwtException.class);
  }

  @Test
  void rejectsTokenSignedWithDifferentSecret() {
    JwtProperties otherProps =
        new JwtProperties(OTHER_SECRET, Duration.ofMinutes(15), Duration.ofDays(7), "other");
    JwtServiceImpl otherService = new JwtServiceImpl(otherProps);
    TokenPair pair = otherService.issue(UUID.randomUUID(), "u@e.com", Set.of(Role.CUSTOMER));

    assertThatThrownBy(() -> service.parseAccessToken(pair.accessToken()))
        .isInstanceOf(SignatureException.class);
  }

  @Test
  void refreshTokenIsHashedConsistently() {
    String raw = service.generateRefreshToken();
    String h1 = service.hashRefreshToken(raw);
    String h2 = service.hashRefreshToken(raw);

    assertThat(h1).isEqualTo(h2);
    assertThat(h1).hasSize(64); // SHA-256 hex
    assertThat(h1).isNotEqualTo(raw);
  }

  @Test
  void differentRefreshTokensProduceDifferentHashes() {
    String h1 = service.hashRefreshToken(service.generateRefreshToken());
    String h2 = service.hashRefreshToken(service.generateRefreshToken());

    assertThat(h1).isNotEqualTo(h2);
  }
}
