package com.inghubscl.ticketing.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.inghubscl.ticketing.user.Role;
import jakarta.servlet.FilterChain;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

  private static final String SECRET =
      "test-secret-must-be-at-least-256-bits-long-for-hs256-algorithm-padding-1234567890";

  private final JwtServiceImpl jwtService =
      new JwtServiceImpl(
          new JwtProperties(SECRET, Duration.ofMinutes(15), Duration.ofDays(7), "issuer"));

  private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void populatesSecurityContextForValidBearerToken() throws Exception {
    UUID userId = UUID.randomUUID();
    TokenPair pair = jwtService.issue(userId, "u@e.com", Set.of(Role.ADMIN, Role.CUSTOMER));
    var req = new MockHttpServletRequest();
    req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + pair.accessToken());
    var res = new MockHttpServletResponse();
    FilterChain chain = (r, s) -> {};

    filter.doFilter(req, res, chain);

    var auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isNotNull();
    assertThat(auth.getName()).isEqualTo(userId.toString());
    assertThat(auth.getAuthorities())
        .extracting(a -> a.getAuthority())
        .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_CUSTOMER");
  }

  @Test
  void clearsContextOnInvalidToken() throws Exception {
    var req = new MockHttpServletRequest();
    req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-jwt");
    var res = new MockHttpServletResponse();

    filter.doFilter(req, res, (r, s) -> {});

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void ignoresMissingHeader() throws Exception {
    var req = new MockHttpServletRequest();
    var res = new MockHttpServletResponse();

    filter.doFilter(req, res, (r, s) -> {});

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void ignoresNonBearerHeader() throws Exception {
    var req = new MockHttpServletRequest();
    req.addHeader(HttpHeaders.AUTHORIZATION, "Basic abc");
    var res = new MockHttpServletResponse();

    filter.doFilter(req, res, (r, s) -> {});

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }
}
