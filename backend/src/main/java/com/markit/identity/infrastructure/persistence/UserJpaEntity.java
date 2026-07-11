package com.markit.identity.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA persistence model for a user (kept separate from the domain {@code User}, ADR-0001). */
@Entity
@Table(name = "users")
public class UserJpaEntity {

  @Id private UUID id;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "password_hash")
  private String passwordHash;

  @Column(name = "google_sub")
  private String googleSub;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected UserJpaEntity() {}

  public UserJpaEntity(
      UUID id, String email, String passwordHash, String googleSub, Instant createdAt) {
    this.id = id;
    this.email = email;
    this.passwordHash = passwordHash;
    this.googleSub = googleSub;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getGoogleSub() {
    return googleSub;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
