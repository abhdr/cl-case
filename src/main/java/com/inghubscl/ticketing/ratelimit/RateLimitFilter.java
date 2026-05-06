package com.inghubscl.ticketing.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.inghubscl.ticketing.common.ProblemDetailWriter;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

  private final RateLimitProperties properties;
  private final ProblemDetailWriter writer;
  private final Cache<String, Bucket> authBuckets;
  private final Cache<String, Bucket> userBuckets;

  public RateLimitFilter(RateLimitProperties properties, ProblemDetailWriter writer) {
    this.properties = properties;
    this.writer = writer;
    this.authBuckets =
        Caffeine.newBuilder().expireAfterAccess(10, TimeUnit.MINUTES).maximumSize(10_000).build();
    this.userBuckets =
        Caffeine.newBuilder().expireAfterAccess(10, TimeUnit.MINUTES).maximumSize(10_000).build();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    String uri = request.getRequestURI();
    Bucket bucket;
    if (uri != null && (uri.equals("/api/auth/login") || uri.equals("/api/auth/register"))) {
      bucket = authBuckets.get(clientIp(request), ip -> newBucket(properties.authPerMinute()));
    } else {
      String key = currentUser();
      if (key == null) {
        chain.doFilter(request, response);
        return;
      }
      bucket = userBuckets.get(key, k -> newBucket(properties.userPerMinute()));
    }

    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    if (probe.isConsumed()) {
      response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
      chain.doFilter(request, response);
      return;
    }
    long waitSec = Math.max(1L, probe.getNanosToWaitForRefill() / 1_000_000_000L);
    response.setHeader("Retry-After", String.valueOf(waitSec));
    writer.write(
        response,
        HttpStatus.TOO_MANY_REQUESTS,
        "Rate Limit Exceeded",
        "Too many requests, retry after " + waitSec + " second(s)");
  }

  private static Bucket newBucket(int requestsPerMinute) {
    return Bucket.builder()
        .addLimit(
            Bandwidth.builder()
                .capacity(requestsPerMinute)
                .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                .build())
        .build();
  }

  private static String clientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    String remote = request.getRemoteAddr();
    return remote == null ? "unknown" : remote;
  }

  private static String currentUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
      return null;
    }
    return auth.getName();
  }
}
