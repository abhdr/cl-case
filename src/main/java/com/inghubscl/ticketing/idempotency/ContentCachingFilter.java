package com.inghubscl.ticketing.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Wraps reservation-create requests in a re-readable body wrapper and a {@link
 * ContentCachingResponseWrapper} so the {@link IdempotencyInterceptor} can hash the request body
 * and capture the response payload.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class ContentCachingFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    if (!isReservationCreate(request)) {
      chain.doFilter(request, response);
      return;
    }

    CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
    ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
    try {
      chain.doFilter(wrappedRequest, wrappedResponse);
    } finally {
      wrappedResponse.copyBodyToResponse();
    }
  }

  private static boolean isReservationCreate(HttpServletRequest request) {
    if (!"POST".equalsIgnoreCase(request.getMethod())) {
      return false;
    }
    String uri = request.getRequestURI();
    return uri != null && uri.matches("^/api/events/[^/]+/reservations/?$");
  }
}
