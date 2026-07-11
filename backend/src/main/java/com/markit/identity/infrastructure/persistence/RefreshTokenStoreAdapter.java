package com.markit.identity.infrastructure.persistence;

import com.markit.identity.application.port.RefreshTokenStore;
import com.markit.identity.domain.UserId;
import com.markit.identity.infrastructure.security.SecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh-token store: issues cryptographically-random raw tokens, persists only their SHA-256 hash,
 * and validates/revokes by hash (ADR-0006). Raw tokens never touch the database.
 */
@Component
public class RefreshTokenStoreAdapter implements RefreshTokenStore {

  private static final int TOKEN_BYTES = 32;

  private final SpringDataRefreshTokenRepository jpa;
  private final SecurityProperties properties;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  RefreshTokenStoreAdapter(
      SpringDataRefreshTokenRepository jpa, SecurityProperties properties, Clock clock) {
    this.jpa = jpa;
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  @Transactional
  public String issue(UserId userId) {
    byte[] raw = new byte[TOKEN_BYTES];
    random.nextBytes(raw);
    String rawToken = HexFormat.of().formatHex(raw);
    Instant now = clock.instant();
    jpa.save(
        new RefreshTokenJpaEntity(
            UUID.randomUUID(),
            userId.value(),
            sha256(rawToken),
            now.plus(properties.jwt().refreshTokenValidity()),
            now));
    return rawToken;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UserId> validate(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return Optional.empty();
    }
    return jpa.findByTokenHash(sha256(rawToken))
        .filter(t -> t.getRevokedAt() == null)
        .filter(t -> t.getExpiresAt().isAfter(clock.instant()))
        .map(t -> UserId.of(t.getUserId()));
  }

  @Override
  @Transactional
  public void revoke(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return;
    }
    jpa.findByTokenHash(sha256(rawToken))
        .filter(t -> t.getRevokedAt() == null)
        .ifPresent(t -> t.revoke(clock.instant()));
  }

  private static String sha256(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
