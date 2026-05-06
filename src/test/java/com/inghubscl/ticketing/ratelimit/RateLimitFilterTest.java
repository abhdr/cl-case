package com.inghubscl.ticketing.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.inghubscl.ticketing.common.ProblemDetailWriter;
import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class RateLimitFilterTest {

  private final ProblemDetailWriter writer = mock(ProblemDetailWriter.class);

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void allowsRequestsUpToCapPerIpForLogin() throws Exception {
    var props = new RateLimitProperties(3, 100);
    var filter = new RateLimitFilter(props, writer);

    for (int i = 0; i < 3; i++) {
      var req = loginRequest("203.0.113.1");
      var res = new MockHttpServletResponse();
      FilterChain chain = mockChain();
      filter.doFilter(req, res, chain);
      assertThat(res.getStatus()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }
  }

  @Test
  void blocksFourthRequestForLoginPerIp() throws Exception {
    var props = new RateLimitProperties(3, 100);
    var filter = new RateLimitFilter(props, writer);

    for (int i = 0; i < 3; i++) {
      filter.doFilter(loginRequest("203.0.113.99"), new MockHttpServletResponse(), mockChain());
    }
    var res = new MockHttpServletResponse();
    filter.doFilter(loginRequest("203.0.113.99"), res, mockChain());

    verify(writer, atLeastOnce()).write(any(), any(HttpStatus.class), anyString(), anyString());
    assertThat(res.getHeader("Retry-After")).isNotNull();
  }

  @Test
  void usesXForwardedForWhenPresent() throws Exception {
    var props = new RateLimitProperties(2, 100);
    var filter = new RateLimitFilter(props, writer);
    var req = loginRequest(null);
    req.addHeader("X-Forwarded-For", "198.51.100.42, 10.0.0.1");

    filter.doFilter(req, new MockHttpServletResponse(), mockChain());
  }

  @Test
  void skipsAnonymousNonAuthEndpoints() throws Exception {
    var props = new RateLimitProperties(10, 100);
    var filter = new RateLimitFilter(props, writer);
    var req = new MockHttpServletRequest("GET", "/api/events/public");
    var res = new MockHttpServletResponse();

    filter.doFilter(req, res, mockChain());

    assertThat(res.getStatus()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
  }

  @Test
  void appliesUserBucketForAuthenticatedRequests() throws Exception {
    var props = new RateLimitProperties(10, 2);
    var filter = new RateLimitFilter(props, writer);
    UUID id = UUID.randomUUID();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                id.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));

    for (int i = 0; i < 2; i++) {
      filter.doFilter(
          new MockHttpServletRequest("GET", "/api/events"),
          new MockHttpServletResponse(),
          mockChain());
    }
    var res = new MockHttpServletResponse();
    filter.doFilter(new MockHttpServletRequest("GET", "/api/events"), res, mockChain());

    verify(writer, atLeastOnce()).write(any(), any(HttpStatus.class), anyString(), anyString());
  }

  private static MockHttpServletRequest loginRequest(String remoteIp) {
    var req = new MockHttpServletRequest("POST", "/api/auth/login");
    if (remoteIp != null) {
      req.setRemoteAddr(remoteIp);
    }
    return req;
  }

  private static FilterChain mockChain() {
    return (req, res) -> {};
  }
}
