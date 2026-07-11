package com.markit.bookmarking.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA persistence model for a bookmark (kept separate from the domain, ADR-0001). */
@Entity
@Table(name = "bookmarks")
public class BookmarkJpaEntity {

  @Id private UUID id;

  @Column(name = "category_id", nullable = false)
  private UUID categoryId;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(nullable = false)
  private String url;

  @Column(nullable = false)
  private String title;

  @Column private String description;

  @Column(nullable = false)
  private String state;

  @Column(name = "failure_reason")
  private String failureReason;

  @Column(nullable = false)
  private int position;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected BookmarkJpaEntity() {}

  public BookmarkJpaEntity(
      UUID id,
      UUID categoryId,
      UUID ownerId,
      String url,
      String title,
      String description,
      String state,
      String failureReason,
      int position,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.categoryId = categoryId;
    this.ownerId = ownerId;
    this.url = url;
    this.title = title;
    this.description = description;
    this.state = state;
    this.failureReason = failureReason;
    this.position = position;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getCategoryId() {
    return categoryId;
  }

  public UUID getOwnerId() {
    return ownerId;
  }

  public String getUrl() {
    return url;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public String getState() {
    return state;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public int getPosition() {
    return position;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
