package com.markit.bookmarking.domain;

import com.markit.identity.domain.UserId;
import java.time.Instant;

/**
 * A user's top-level grouping — root of the {@code Collection → Category → Bookmark} tree.
 * Framework-free (NFR-MAINT-001); persistence lives in an infrastructure adapter. Carries its
 * {@code ownerId} explicitly so every access can be isolated per user (R-SEC-01).
 */
public class Collection {

  private final CollectionId id;
  private final UserId ownerId;
  private String name;
  private int position;
  private final Instant createdAt;

  private Collection(
      CollectionId id, UserId ownerId, String name, int position, Instant createdAt) {
    if (id == null || ownerId == null || createdAt == null) {
      throw new IllegalArgumentException("id, ownerId and createdAt are required");
    }
    this.id = id;
    this.ownerId = ownerId;
    this.name = requireName(name);
    this.position = position;
    this.createdAt = createdAt;
  }

  /** Create a new collection owned by {@code ownerId}, appended at {@code position}. */
  public static Collection create(
      CollectionId id, UserId ownerId, String name, int position, Instant createdAt) {
    return new Collection(id, ownerId, name, position, createdAt);
  }

  /** Reconstitute a collection from persistence. */
  public static Collection rehydrate(
      CollectionId id, UserId ownerId, String name, int position, Instant createdAt) {
    return new Collection(id, ownerId, name, position, createdAt);
  }

  public void rename(String newName) {
    this.name = requireName(newName);
  }

  public void reposition(int newPosition) {
    this.position = newPosition;
  }

  private static String requireName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Collection name must not be blank");
    }
    return name.trim();
  }

  public CollectionId id() {
    return id;
  }

  public UserId ownerId() {
    return ownerId;
  }

  public String name() {
    return name;
  }

  public int position() {
    return position;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
