package com.inghubscl.ticketing.exception;

import org.springframework.http.HttpStatus;

public abstract class BusinessException extends RuntimeException {

  private final HttpStatus status;
  private final String title;

  protected BusinessException(HttpStatus status, String title, String message) {
    super(message);
    this.status = status;
    this.title = title;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getTitle() {
    return title;
  }

  public String getTypeSlug() {
    return title.toLowerCase().replace(' ', '-');
  }
}
