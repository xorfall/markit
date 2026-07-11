package com.markit.identity.domain;

import java.util.UUID;

/** Identity of a {@link User}. Framework-free value object (NFR-MAINT-001). */
public record UserId(UUID value) {

  public UserId {
    if (value == null) {
      throw new IllegalArgumentException("UserId must not be null");
    }
  }

  public static UserId newId() {
    return new UserId(UUID.randomUUID());
  }

  public static UserId of(UUID value) {
    return new UserId(value);
  }

  public static UserId of(String value) {
    return new UserId(UUID.fromString(value));
  }

  public String asString() {
    return value.toString();
  }
}
