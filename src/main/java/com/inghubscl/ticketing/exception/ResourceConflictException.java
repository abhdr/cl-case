package com.inghubscl.ticketing.exception;

import org.springframework.http.HttpStatus;

public class ResourceConflictException extends BusinessException {

  public ResourceConflictException(String message) {
    super(HttpStatus.CONFLICT, "Resource Conflict", message);
  }
}
