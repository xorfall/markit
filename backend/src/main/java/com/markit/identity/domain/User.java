package com.markit.identity.domain;

import java.time.Instant;

/**
 * A registered user — the Identity aggregate root. Owns credentials (a password hash and/or a Google
 * subject). Framework-free (NFR-MAINT-001); persistence lives in an infrastructure adapter.
 */
public class User {

  private final UserId id;
  private final Email email;
  private final String passwordHash; // nullable — OAuth-only accounts have none
  private String googleSub; // nullable; may be linked later
  private final Instant createdAt;

  private User(UserId id, Email email, String passwordHash, String googleSub, Instant createdAt) {
    if (id == null || email == null || createdAt == null) {
      throw new IllegalArgumentException("id, email and createdAt are required");
    }
    this.id = id;
    this.email = email;
    this.passwordHash = passwordHash;
    this.googleSub = googleSub;
    this.createdAt = createdAt;
  }

  /** Register a new user with a password (already hashed by the application layer). */
  public static User registerWithPassword(
      UserId id, Email email, String passwordHash, Instant createdAt) {
    if (passwordHash == null || passwordHash.isBlank()) {
      throw new IllegalArgumentException("passwordHash is required");
    }
    return new User(id, email, passwordHash, null, createdAt);
  }

  /** Register a new user via Google sign-in (no password). */
  public static User registerWithGoogle(
      UserId id, Email email, String googleSub, Instant createdAt) {
    if (googleSub == null || googleSub.isBlank()) {
      throw new IllegalArgumentException("googleSub is required");
    }
    return new User(id, email, null, googleSub, createdAt);
  }

  /** Link a Google identity to an existing (password) account. Idempotent-safe on same subject. */
  public void linkGoogle(String googleSub) {
    if (googleSub == null || googleSub.isBlank()) {
      throw new IllegalArgumentException("googleSub is required");
    }
    if (this.googleSub != null && !this.googleSub.equals(googleSub)) {
      throw new IllegalStateException("Account already linked to a different Google identity");
    }
    this.googleSub = googleSub;
  }

  /** Reconstitute a user from persistence. */
  public static User rehydrate(
      UserId id, Email email, String passwordHash, String googleSub, Instant createdAt) {
    return new User(id, email, passwordHash, googleSub, createdAt);
  }

  public boolean hasPassword() {
    return passwordHash != null && !passwordHash.isBlank();
  }

  public UserId id() {
    return id;
  }

  public Email email() {
    return email;
  }

  public String passwordHash() {
    return passwordHash;
  }

  public String googleSub() {
    return googleSub;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
