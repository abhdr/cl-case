package com.inghubscl.ticketing.auth;

import com.inghubscl.ticketing.auth.dto.LoginRequest;
import com.inghubscl.ticketing.auth.dto.RefreshRequest;
import com.inghubscl.ticketing.auth.dto.RegisterRequest;
import com.inghubscl.ticketing.auth.dto.TokenResponse;
import com.inghubscl.ticketing.exception.InvalidRequestException;
import com.inghubscl.ticketing.exception.ResourceConflictException;
import com.inghubscl.ticketing.user.Role;
import com.inghubscl.ticketing.user.User;
import com.inghubscl.ticketing.user.UserRepository;
import java.time.Instant;
import java.util.Set;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtService jwtService;
  private final PasswordEncoder passwordEncoder;
  private final AuthenticationManager authenticationManager;

  public AuthServiceImpl(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      JwtService jwtService,
      PasswordEncoder passwordEncoder,
      AuthenticationManager authenticationManager) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.jwtService = jwtService;
    this.passwordEncoder = passwordEncoder;
    this.authenticationManager = authenticationManager;
  }

  @Override
  @Transactional
  public TokenResponse register(RegisterRequest request) {
    if (userRepository.existsByEmail(request.email())) {
      throw new ResourceConflictException("Email already registered: " + request.email());
    }
    User user =
        new User(
            request.email(), passwordEncoder.encode(request.password()), Set.of(Role.CUSTOMER));
    userRepository.save(user);
    return issueTokens(user);
  }

  @Override
  @Transactional
  public TokenResponse login(LoginRequest request) {
    try {
      authenticationManager.authenticate(
          new UsernamePasswordAuthenticationToken(request.email(), request.password()));
    } catch (AuthenticationException ex) {
      throw new BadCredentialsException("Invalid email or password");
    }
    User user =
        userRepository
            .findByEmail(request.email())
            .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
    user.setLastLoginAt(Instant.now());
    return issueTokens(user);
  }

  @Override
  @Transactional
  public TokenResponse refresh(RefreshRequest request) {
    String hash = jwtService.hashRefreshToken(request.refreshToken());
    RefreshToken stored =
        refreshTokenRepository
            .findByTokenHash(hash)
            .orElseThrow(() -> new InvalidRequestException("Invalid refresh token"));
    if (stored.isRevoked()) {
      throw new InvalidRequestException("Refresh token has been revoked");
    }
    if (stored.isExpired()) {
      throw new InvalidRequestException("Refresh token has expired");
    }
    User user =
        userRepository
            .findById(stored.getUserId())
            .orElseThrow(() -> new InvalidRequestException("User no longer exists"));

    // Rotation: revoke old, issue new pair
    stored.revoke();
    return issueTokens(user);
  }

  private TokenResponse issueTokens(User user) {
    TokenPair pair = jwtService.issue(user.getId(), user.getEmail(), user.getRoles());
    String hashedRefresh = jwtService.hashRefreshToken(pair.refreshToken());
    refreshTokenRepository.save(
        new RefreshToken(user.getId(), hashedRefresh, jwtService.refreshTokenExpiry()));
    return new TokenResponse(pair.accessToken(), pair.refreshToken(), pair.expiresInSeconds());
  }
}
