package com.inghubscl.ticketing.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplTest {

  @Mock UserRepository userRepository;
  @Mock RefreshTokenRepository refreshTokenRepository;
  @Mock JwtService jwtService;
  @Mock PasswordEncoder passwordEncoder;
  @Mock AuthenticationManager authenticationManager;

  @InjectMocks AuthServiceImpl service;

  private final TokenPair samplePair = new TokenPair("access-jwt", "raw-refresh", 900);

  @BeforeEach
  void stubJwtIssue() {
    when(jwtService.issue(any(UUID.class), anyString(), anySet())).thenReturn(samplePair);
    when(jwtService.hashRefreshToken(anyString())).thenReturn("hash-of-refresh");
    when(jwtService.refreshTokenExpiry()).thenReturn(Instant.now().plusSeconds(7 * 86400));
  }

  @Test
  void registerCreatesCustomerAndIssuesTokens() {
    when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
    when(passwordEncoder.encode("password1")).thenReturn("encoded");
    when(userRepository.save(any(User.class)))
        .thenAnswer(
            inv -> {
              User u = inv.getArgument(0);
              setId(u, UUID.randomUUID());
              return u;
            });

    TokenResponse response = service.register(new RegisterRequest("new@example.com", "password1"));

    assertThat(response.accessToken()).isEqualTo("access-jwt");
    assertThat(response.refreshToken()).isEqualTo("raw-refresh");
    verify(userRepository).save(any(User.class));
    verify(refreshTokenRepository).save(any(RefreshToken.class));
  }

  @Test
  void registerRejectsDuplicateEmail() {
    when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);

    assertThatThrownBy(() -> service.register(new RegisterRequest("dup@example.com", "password1")))
        .isInstanceOf(ResourceConflictException.class)
        .hasMessageContaining("dup@example.com");

    verify(userRepository, never()).save(any());
    verify(refreshTokenRepository, never()).save(any());
  }

  @Test
  void loginIssuesTokensAndUpdatesLastLogin() {
    User user = new User("login@example.com", "encoded", Set.of(Role.CUSTOMER));
    setId(user, UUID.randomUUID());
    when(userRepository.findByEmail("login@example.com")).thenReturn(Optional.of(user));

    TokenResponse response = service.login(new LoginRequest("login@example.com", "raw"));

    assertThat(response.accessToken()).isEqualTo("access-jwt");
    assertThat(user.getLastLoginAt()).isNotNull();
    verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
  }

  @Test
  void loginWrapsAuthenticationExceptionAsBadCredentials() {
    when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

    assertThatThrownBy(() -> service.login(new LoginRequest("any@example.com", "wrong")))
        .isInstanceOf(BadCredentialsException.class);
  }

  @Test
  void refreshRotatesTokens() {
    User user = new User("r@example.com", "h", Set.of(Role.CUSTOMER));
    setId(user, UUID.randomUUID());
    RefreshToken token =
        new RefreshToken(UUID.randomUUID(), "hashed", Instant.now().plusSeconds(86400));
    when(jwtService.hashRefreshToken("raw-token")).thenReturn("hashed");
    when(refreshTokenRepository.findByTokenHash("hashed")).thenReturn(Optional.of(token));
    when(userRepository.findById(token.getUserId())).thenReturn(Optional.of(user));

    TokenResponse response = service.refresh(new RefreshRequest("raw-token"));

    assertThat(response.accessToken()).isEqualTo("access-jwt");
    assertThat(token.isRevoked()).isTrue();
  }

  @Test
  void refreshRejectsUnknownToken() {
    when(jwtService.hashRefreshToken("raw")).thenReturn("hash");
    when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.refresh(new RefreshRequest("raw")))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Invalid refresh token");
  }

  @Test
  void refreshRejectsRevokedToken() {
    RefreshToken token = new RefreshToken(UUID.randomUUID(), "h", Instant.now().plusSeconds(86400));
    token.revoke();
    when(jwtService.hashRefreshToken("raw")).thenReturn("h");
    when(refreshTokenRepository.findByTokenHash("h")).thenReturn(Optional.of(token));

    assertThatThrownBy(() -> service.refresh(new RefreshRequest("raw")))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("revoked");
  }

  @Test
  void refreshRejectsExpiredToken() {
    RefreshToken token = new RefreshToken(UUID.randomUUID(), "h", Instant.now().minusSeconds(60));
    when(jwtService.hashRefreshToken("raw")).thenReturn("h");
    when(refreshTokenRepository.findByTokenHash("h")).thenReturn(Optional.of(token));

    assertThatThrownBy(() -> service.refresh(new RefreshRequest("raw")))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("expired");
  }

  @Test
  void refreshRejectsTokenForMissingUser() {
    RefreshToken token = new RefreshToken(UUID.randomUUID(), "h", Instant.now().plusSeconds(86400));
    when(jwtService.hashRefreshToken("raw")).thenReturn("h");
    when(refreshTokenRepository.findByTokenHash("h")).thenReturn(Optional.of(token));
    when(userRepository.findById(token.getUserId())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.refresh(new RefreshRequest("raw")))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("User no longer exists");
  }

  @Test
  void registerSavesRefreshHashOnce() {
    when(userRepository.existsByEmail(anyString())).thenReturn(false);
    when(passwordEncoder.encode(anyString())).thenReturn("enc");
    when(userRepository.save(any(User.class)))
        .thenAnswer(
            inv -> {
              User u = inv.getArgument(0);
              setId(u, UUID.randomUUID());
              return u;
            });

    service.register(new RegisterRequest("once@example.com", "password1"));

    verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
  }

  private static void setId(User user, UUID id) {
    try {
      var f = User.class.getDeclaredField("id");
      f.setAccessible(true);
      f.set(user, id);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }
}
