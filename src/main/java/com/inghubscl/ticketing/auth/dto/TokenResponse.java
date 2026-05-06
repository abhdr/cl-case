package com.inghubscl.ticketing.auth.dto;

public record TokenResponse(String accessToken, String refreshToken, long expiresIn) {

  public static TokenResponse from(String accessToken, String refreshToken, long expiresInSeconds) {
    return new TokenResponse(accessToken, refreshToken, expiresInSeconds);
  }
}
