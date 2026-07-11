package com.markit.bookmarking.domain;

import java.util.UUID;

/** Identity of a {@link Category}. Framework-free value object (NFR-MAINT-001). */
public record CategoryId(UUID value) {

  public CategoryId {
    if (value == null) {
      throw new IllegalArgumentException("CategoryId must not be null");
    }
  }

  public static CategoryId newId() {
    return new CategoryId(UUID.randomUUID());
  }

  public static CategoryId of(UUID value) {
    return new CategoryId(value);
  }

  public static CategoryId of(String value) {
    return new CategoryId(UUID.fromString(value));
  }

  public String asString() {
    return value.toString();
  }
}
