package com.markit.bookmarking.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA persistence model for a category (kept separate from the domain, ADR-0001). */
@Entity
@Table(name = "categories")
public class CategoryJpaEntity {

  @Id private UUID id;

  @Column(name = "collection_id", nullable = false)
  private UUID collectionId;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private int position;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected CategoryJpaEntity() {}

  public CategoryJpaEntity(
      UUID id, UUID collectionId, UUID ownerId, String name, int position, Instant createdAt) {
    this.id = id;
    this.collectionId = collectionId;
    this.ownerId = ownerId;
    this.name = name;
    this.position = position;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getCollectionId() {
    return collectionId;
  }

  public UUID getOwnerId() {
    return ownerId;
  }

  public String getName() {
    return name;
  }

  public int getPosition() {
    return position;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
