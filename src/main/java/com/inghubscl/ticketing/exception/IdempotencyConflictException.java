package com.inghubscl.ticketing.exception;

import org.springframework.http.HttpStatus;

public class IdempotencyConflictException extends BusinessException {

  public IdempotencyConflictException(String message) {
    super(HttpStatus.CONFLICT, "Idempotency Conflict", message);
  }
}
