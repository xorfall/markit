package com.markit.bookmarking.domain;

import com.markit.identity.domain.UserId;
import java.time.Instant;

/**
 * A column within a {@link Collection}, containing bookmarks. Framework-free (NFR-MAINT-001).
 * Carries its {@code ownerId} explicitly for per-user isolation (R-SEC-01).
 */
public class Category {

  private final CategoryId id;
  private CollectionId collectionId;
  private final UserId ownerId;
  private String name;
  private int position;
  private final Instant createdAt;

  private Category(
      CategoryId id,
      CollectionId collectionId,
      UserId ownerId,
      String name,
      int position,
      Instant createdAt) {
    if (id == null || collectionId == null || ownerId == null || createdAt == null) {
      throw new IllegalArgumentException("id, collectionId, ownerId and createdAt are required");
    }
    this.id = id;
    this.collectionId = collectionId;
    this.ownerId = ownerId;
    this.name = requireName(name);
    this.position = position;
    this.createdAt = createdAt;
  }

  /** Create a new category under {@code collectionId}, appended at {@code position}. */
  public static Category create(
      CategoryId id,
      CollectionId collectionId,
      UserId ownerId,
      String name,
      int position,
      Instant createdAt) {
    return new Category(id, collectionId, ownerId, name, position, createdAt);
  }

  /** Reconstitute a category from persistence. */
  public static Category rehydrate(
      CategoryId id,
      CollectionId collectionId,
      UserId ownerId,
      String name,
      int position,
      Instant createdAt) {
    return new Category(id, collectionId, ownerId, name, position, createdAt);
  }

  public void rename(String newName) {
    this.name = requireName(newName);
  }

  /** Move this category to another collection, appended at {@code newPosition}. */
  public void moveTo(CollectionId targetCollectionId, int newPosition) {
    if (targetCollectionId == null) {
      throw new IllegalArgumentException("targetCollectionId is required");
    }
    this.collectionId = targetCollectionId;
    this.position = newPosition;
  }

  public void reposition(int newPosition) {
    this.position = newPosition;
  }

  private static String requireName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Category name must not be blank");
    }
    return name.trim();
  }

  public CategoryId id() {
    return id;
  }

  public CollectionId collectionId() {
    return collectionId;
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
