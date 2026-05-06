package com.inghubscl.ticketing.exception;

import org.springframework.http.HttpStatus;

public class EventNotPublishedException extends BusinessException {

  public EventNotPublishedException(Object eventId) {
    super(
        HttpStatus.CONFLICT,
        "Event Not Published",
        "Event '" + eventId + "' is not published; reservations are not allowed");
  }
}
