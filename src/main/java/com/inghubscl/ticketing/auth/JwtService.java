package com.inghubscl.ticketing.auth;

import com.inghubscl.ticketing.user.Role;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public interface JwtService {

  TokenPair issue(UUID userId, String email, Set<Role> roles);

  Claims parseAccessToken(String token);

  String generateRefreshToken();

  String hashRefreshToken(String raw);

  Instant refreshTokenExpiry();
}
