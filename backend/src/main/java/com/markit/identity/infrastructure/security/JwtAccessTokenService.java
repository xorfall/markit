package com.markit.identity.infrastructure.security;

import com.markit.identity.application.port.AccessTokenService;
import com.markit.identity.domain.UserId;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/** Issues and verifies HS256-signed access JWTs whose subject is the user id (ADR-0006). */
@Component
public class JwtAccessTokenService implements AccessTokenService {

  private final SecretKey key;
  private final long validitySeconds;
  private final Clock clock;

  JwtAccessTokenService(SecurityProperties properties, Clock clock) {
    this.key = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
    this.validitySeconds = properties.jwt().accessTokenValidity().toSeconds();
    this.clock = clock;
  }

  @Override
  public String issue(UserId userId) {
    var now = clock.instant();
    return Jwts.builder()
        .subject(userId.asString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(validitySeconds)))
        .signWith(key)
        .compact();
  }

  @Override
  public Optional<UserId> verify(String token) {
    try {
      String subject =
          Jwts.parser()
              .verifyWith(key)
              .clock(() -> Date.from(clock.instant()))
              .build()
              .parseSignedClaims(token)
              .getPayload()
              .getSubject();
      return Optional.of(UserId.of(subject));
    } catch (RuntimeException e) {
      return Optional.empty();
    }
  }

  @Override
  public long validitySeconds() {
    return validitySeconds;
  }
}
