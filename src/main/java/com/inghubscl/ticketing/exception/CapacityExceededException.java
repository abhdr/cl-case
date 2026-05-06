package com.inghubscl.ticketing.exception;

import org.springframework.http.HttpStatus;

public class CapacityExceededException extends BusinessException {

  public CapacityExceededException(int requested, int available) {
    super(
        HttpStatus.CONFLICT,
        "Capacity Exceeded",
        "Requested " + requested + " seat(s) but only " + available + " remain");
  }
}
