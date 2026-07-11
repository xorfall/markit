package com.markit.bookmarking.domain;

import com.markit.identity.domain.UserId;
import java.time.Instant;

/**
 * A saved URL within a {@link Category} — root of its own aggregate (data-model §1). Framework-free
 * (NFR-MAINT-001). Carries its {@code ownerId} explicitly for per-user isolation (R-SEC-01). A newly
 * added bookmark starts {@code PENDING} with its title defaulting to the URL; scraping (which fills
 * title/description and drives the state) is a later slice.
 */
public class Bookmark {

  private final BookmarkId id;
  private CategoryId categoryId;
  private final UserId ownerId;
  private final Url url;
  private String title;
  private String description;
  private BookmarkState state;
  private int position;
  private final Instant createdAt;
  private Instant updatedAt;

  private Bookmark(
      BookmarkId id,
      CategoryId categoryId,
      UserId ownerId,
      Url url,
      String title,
      String description,
      BookmarkState state,
      int position,
      Instant createdAt,
      Instant updatedAt) {
    if (id == null
        || categoryId == null
        || ownerId == null
        || url == null
        || state == null
        || createdAt == null
        || updatedAt == null) {
      throw new IllegalArgumentException(
          "id, categoryId, ownerId, url, state, createdAt and updatedAt are required");
    }
    this.id = id;
    this.categoryId = categoryId;
    this.ownerId = ownerId;
    this.url = url;
    this.title = requireTitle(title);
    this.description = description;
    this.state = state;
    this.position = position;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /**
   * Add a new bookmark by URL: starts {@code PENDING} with {@code title = url} (scraping refines
   * these later, FR-BMK-004).
   */
  public static Bookmark add(
      BookmarkId id,
      CategoryId categoryId,
      UserId ownerId,
      Url url,
      int position,
      Instant createdAt) {
    return new Bookmark(
        id,
        categoryId,
        ownerId,
        url,
        url.value(),
        null,
        BookmarkState.PENDING,
        position,
        createdAt,
        createdAt);
  }

  /** Reconstitute a bookmark from persistence. */
  public static Bookmark rehydrate(
      BookmarkId id,
      CategoryId categoryId,
      UserId ownerId,
      Url url,
      String title,
      String description,
      BookmarkState state,
      int position,
      Instant createdAt,
      Instant updatedAt) {
    return new Bookmark(
        id,
        categoryId,
        ownerId,
        url,
        title,
        description,
        state,
        position,
        createdAt,
        updatedAt);
  }

  /**
   * Edit user-facing metadata (FR-BMK-005). A {@code null} title leaves the title unchanged (partial
   * PATCH); the description is always replaced with {@code newDescription} ({@code null} clears it).
   */
  public void editDetails(String newTitle, String newDescription, Instant now) {
    if (newTitle != null) {
      this.title = requireTitle(newTitle);
    }
    this.description = newDescription;
    touch(now);
  }

  /** Move this bookmark to another category, appended at {@code newPosition} (FR-BMK-006). */
  public void moveTo(CategoryId targetCategoryId, int newPosition, Instant now) {
    if (targetCategoryId == null) {
      throw new IllegalArgumentException("targetCategoryId is required");
    }
    this.categoryId = targetCategoryId;
    this.position = newPosition;
    touch(now);
  }

  public void reposition(int newPosition, Instant now) {
    this.position = newPosition;
    touch(now);
  }

  private void touch(Instant now) {
    if (now == null) {
      throw new IllegalArgumentException("now is required");
    }
    this.updatedAt = now;
  }

  private static String requireTitle(String title) {
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("Bookmark title must not be blank");
    }
    return title.trim();
  }

  public BookmarkId id() {
    return id;
  }

  public CategoryId categoryId() {
    return categoryId;
  }

  public UserId ownerId() {
    return ownerId;
  }

  public Url url() {
    return url;
  }

  public String title() {
    return title;
  }

  public String description() {
    return description;
  }

  public BookmarkState state() {
    return state;
  }

  public int position() {
    return position;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
