package com.markit.bookmarking.domain;

import java.util.UUID;

/** Identity of a {@link Bookmark}. Framework-free value object (NFR-MAINT-001). */
public record BookmarkId(UUID value) {

  public BookmarkId {
    if (value == null) {
      throw new IllegalArgumentException("BookmarkId must not be null");
    }
  }

  public static BookmarkId newId() {
    return new BookmarkId(UUID.randomUUID());
  }

  public static BookmarkId of(UUID value) {
    return new BookmarkId(value);
  }

  public static BookmarkId of(String value) {
    return new BookmarkId(UUID.fromString(value));
  }

  public String asString() {
    return value.toString();
  }
}
