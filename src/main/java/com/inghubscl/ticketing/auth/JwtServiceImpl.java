package com.inghubscl.ticketing.auth;

import com.inghubscl.ticketing.user.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtServiceImpl implements JwtService {

  private static final int REFRESH_TOKEN_BYTES = 32;

  private final SecretKey signingKey;
  private final JwtProperties props;
  private final SecureRandom random = new SecureRandom();

  public JwtServiceImpl(JwtProperties props) {
    this.props = props;
    this.signingKey = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public TokenPair issue(UUID userId, String email, Set<Role> roles) {
    Instant now = Instant.now();
    Instant accessExp = now.plus(props.accessTtl());

    String accessToken =
        Jwts.builder()
            .subject(userId.toString())
            .issuer(props.issuer())
            .issuedAt(Date.from(now))
            .expiration(Date.from(accessExp))
            .id(UUID.randomUUID().toString())
            .claim("email", email)
            .claim("roles", roles.stream().map(Enum::name).toList())
            .signWith(signingKey, Jwts.SIG.HS256)
            .compact();

    String refreshToken = generateRefreshToken();
    return new TokenPair(accessToken, refreshToken, props.accessTtl().toSeconds());
  }

  @Override
  public Claims parseAccessToken(String token) {
    return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
  }

  @Override
  public String generateRefreshToken() {
    byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  @Override
  public String hashRefreshToken(String raw) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  @Override
  public Instant refreshTokenExpiry() {
    return Instant.now().plus(props.refreshTtl());
  }
}
