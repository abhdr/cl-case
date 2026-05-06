package com.inghubscl.ticketing.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends BusinessException {

  public ForbiddenException(String message) {
    super(HttpStatus.FORBIDDEN, "Forbidden", message);
  }
}
