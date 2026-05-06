package com.inghubscl.ticketing.exception;

import com.inghubscl.ticketing.common.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private static final String TYPE_BASE = "https://api/errors/";

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ProblemDetail> handleBusiness(
      BusinessException ex, HttpServletRequest req) {
    log.warn("Business error [{}]: {}", ex.getClass().getSimpleName(), ex.getMessage());
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
    problem.setTitle(ex.getTitle());
    problem.setType(URI.create(TYPE_BASE + ex.getTypeSlug()));
    problem.setInstance(URI.create(req.getRequestURI()));
    addTraceId(problem);
    return ResponseEntity.status(ex.getStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest req) {
    List<Map<String, String>> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                fe ->
                    Map.of(
                        "field",
                        fe.getField(),
                        "message",
                        fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()))
            .toList();
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request body has invalid fields");
    problem.setTitle("Validation Failed");
    problem.setType(URI.create(TYPE_BASE + "validation"));
    problem.setInstance(URI.create(req.getRequestURI()));
    problem.setProperty("errors", errors);
    addTraceId(problem);
    return ResponseEntity.badRequest()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ProblemDetail> handleConstraintViolation(
      ConstraintViolationException ex, HttpServletRequest req) {
    List<Map<String, String>> errors =
        ex.getConstraintViolations().stream()
            .map(
                (ConstraintViolation<?> v) ->
                    Map.of(
                        "field", v.getPropertyPath().toString(),
                        "message", v.getMessage()))
            .toList();
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Constraint violation");
    problem.setTitle("Validation Failed");
    problem.setType(URI.create(TYPE_BASE + "validation"));
    problem.setInstance(URI.create(req.getRequestURI()));
    problem.setProperty("errors", errors);
    addTraceId(problem);
    return ResponseEntity.badRequest()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ProblemDetail> handleAccessDenied(
      AccessDeniedException ex, HttpServletRequest req) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied");
    problem.setTitle("Forbidden");
    problem.setType(URI.create(TYPE_BASE + "forbidden"));
    problem.setInstance(URI.create(req.getRequestURI()));
    addTraceId(problem);
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<ProblemDetail> handleBadCredentials(
      BadCredentialsException ex, HttpServletRequest req) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    problem.setTitle("Unauthorized");
    problem.setType(URI.create(TYPE_BASE + "unauthorized"));
    problem.setInstance(URI.create(req.getRequestURI()));
    addTraceId(problem);
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .header("WWW-Authenticate", "Bearer realm=\"api\"")
        .body(problem);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleGeneric(Exception ex, HttpServletRequest req) {
    log.error("Unhandled exception", ex);
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred. Reference traceId for support.");
    problem.setTitle("Internal Server Error");
    problem.setType(URI.create(TYPE_BASE + "internal"));
    problem.setInstance(URI.create(req.getRequestURI()));
    addTraceId(problem);
    return ResponseEntity.internalServerError()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  private static void addTraceId(ProblemDetail problem) {
    String traceId = MDC.get(TraceIdFilter.TRACE_ID_KEY);
    if (traceId != null) {
      problem.setProperty("traceId", traceId);
    }
  }
}
