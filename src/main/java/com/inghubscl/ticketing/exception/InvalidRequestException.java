package com.inghubscl.ticketing.exception;

import org.springframework.http.HttpStatus;

public class InvalidRequestException extends BusinessException {

  public InvalidRequestException(String message) {
    super(HttpStatus.BAD_REQUEST, "Invalid Request", message);
  }
}
