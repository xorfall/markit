package com.markit.bookmarking.domain;

import java.util.UUID;

/** Identity of a {@link Collection}. Framework-free value object (NFR-MAINT-001). */
public record CollectionId(UUID value) {

  public CollectionId {
    if (value == null) {
      throw new IllegalArgumentException("CollectionId must not be null");
    }
  }

  public static CollectionId newId() {
    return new CollectionId(UUID.randomUUID());
  }

  public static CollectionId of(UUID value) {
    return new CollectionId(value);
  }

  public static CollectionId of(String value) {
    return new CollectionId(UUID.fromString(value));
  }

  public String asString() {
    return value.toString();
  }
}
