package com.inghubscl.ticketing.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends BusinessException {

  public ResourceNotFoundException(String resourceType, Object id) {
    super(
        HttpStatus.NOT_FOUND,
        "Resource Not Found",
        resourceType + " with id '" + id + "' was not found");
  }

  public ResourceNotFoundException(String message) {
    super(HttpStatus.NOT_FOUND, "Resource Not Found", message);
  }
}
