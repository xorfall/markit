package com.markit.identity.application.port;

import com.markit.identity.domain.UserId;
import java.util.Optional;

/**
 * Issues, validates, and revokes rotating refresh tokens (ADR-0006). Only a hash of each token is
 * stored server-side; callers pass raw tokens.
 */
public interface RefreshTokenStore {

  /** Issue a new refresh token for the user; returns the raw token (stored only as a hash). */
  String issue(UserId userId);

  /** Returns the owning user if the raw token is known, unrevoked, and unexpired. */
  Optional<UserId> validate(String rawToken);

  /** Revoke the token identified by the raw value. No-op if unknown (idempotent). */
  void revoke(String rawToken);
}
