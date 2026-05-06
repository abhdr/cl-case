package com.inghubscl.ticketing.idempotency;

import com.inghubscl.ticketing.exception.IdempotencyConflictException;
import com.inghubscl.ticketing.exception.IdempotencyMismatchException;
import com.inghubscl.ticketing.exception.InvalidRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingResponseWrapper;

@Component
public class IdempotencyInterceptor implements HandlerInterceptor {

  static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
  static final String ENDPOINT_NORMALIZED = "POST /api/events/*/reservations";
  static final String ATTR_RECORD_ID = "ticketing.idem.recordId";
  static final String ATTR_HANDLED = "ticketing.idem.handled";

  private final IdempotencyService idempotencyService;

  public IdempotencyInterceptor(IdempotencyService idempotencyService) {
    this.idempotencyService = idempotencyService;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws IOException {
    if (!"POST".equalsIgnoreCase(request.getMethod())) {
      return true;
    }
    String key = request.getHeader(IDEMPOTENCY_KEY_HEADER);
    if (key == null || key.isBlank()) {
      throw new InvalidRequestException("Idempotency-Key header is required for this endpoint");
    }

    byte[] body = readRequestBody(request);
    String requestHash = idempotencyService.hash(body);

    IdempotencyService.ClaimResult result =
        idempotencyService.claim(key, ENDPOINT_NORMALIZED, requestHash);

    if (result instanceof IdempotencyService.ClaimResult.Granted granted) {
      request.setAttribute(ATTR_RECORD_ID, granted.id());
      request.setAttribute(ATTR_HANDLED, Boolean.TRUE);
      return true;
    }
    if (result instanceof IdempotencyService.ClaimResult.InProgress) {
      throw new IdempotencyConflictException(
          "A request with this Idempotency-Key is currently in progress");
    }
    if (result instanceof IdempotencyService.ClaimResult.Mismatch) {
      throw new IdempotencyMismatchException(
          "Idempotency-Key was previously used with a different request body");
    }
    if (result instanceof IdempotencyService.ClaimResult.Replay replay) {
      response.setStatus(replay.responseStatus());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
      response.setHeader("Idempotent-Replayed", "true");
      response.getWriter().write(replay.responseBody());
      response.getWriter().flush();
      return false;
    }
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    Object handled = request.getAttribute(ATTR_HANDLED);
    if (!Boolean.TRUE.equals(handled)) {
      return;
    }
    Object idObj = request.getAttribute(ATTR_RECORD_ID);
    if (!(idObj instanceof UUID id)) {
      return;
    }
    int status = response.getStatus();
    if (status >= 400 || ex != null) {
      idempotencyService.deleteById(id);
      return;
    }
    byte[] responseBytes = extractResponseBody(response);
    String body = responseBytes == null ? "" : new String(responseBytes, StandardCharsets.UTF_8);
    String responseHash = idempotencyService.hash(responseBytes);
    idempotencyService.complete(id, status, body, responseHash);
  }

  private static byte[] readRequestBody(HttpServletRequest request) throws IOException {
    if (request instanceof CachedBodyHttpServletRequest cached) {
      return cached.getCachedBody();
    }
    try (var in = request.getInputStream()) {
      return in.readAllBytes();
    }
  }

  private static byte[] extractResponseBody(HttpServletResponse response) {
    if (response instanceof ContentCachingResponseWrapper wrapper) {
      return wrapper.getContentAsByteArray();
    }
    return null;
  }
}
