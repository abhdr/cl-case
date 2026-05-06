package com.inghubscl.ticketing.exception;

import org.springframework.http.HttpStatus;

public class IdempotencyMismatchException extends BusinessException {

  public IdempotencyMismatchException(String message) {
    super(HttpStatus.UNPROCESSABLE_ENTITY, "Idempotency Mismatch", message);
  }
}
