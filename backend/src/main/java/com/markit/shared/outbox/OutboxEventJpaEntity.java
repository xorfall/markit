package com.markit.shared.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA persistence model for an outbox row. State transitions live here as intention-revealing
 * methods ({@link #markPublished}, {@link #recordFailure}) so the {@link OutboxRelay} stays thin and
 * the transitions are unit-testable without a database.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEventJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "aggregate_type", nullable = false)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false)
  private UUID aggregateId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  // Mapped as jsonb: Hibernate serializes the JsonNode to JSON (no double-encoding), so the payload
  // is stored exactly once and read back as a structured node.
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private JsonNode payload;

  @Column(nullable = false)
  private String status;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "last_error")
  private String lastError;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  private static final int MAX_ERROR_LENGTH = 1000;

  protected OutboxEventJpaEntity() {}

  public OutboxEventJpaEntity(
      String aggregateType, UUID aggregateId, String eventType, JsonNode payload, Instant createdAt) {
    this.aggregateType = aggregateType;
    this.aggregateId = aggregateId;
    this.eventType = eventType;
    this.payload = payload;
    this.status = OutboxStatus.PENDING.name();
    this.attempts = 0;
    this.createdAt = createdAt;
  }

  /** Mark the row published after a successful broker send. */
  public void markPublished(Instant now) {
    this.status = OutboxStatus.PUBLISHED.name();
    this.publishedAt = now;
    this.lastError = null;
  }

  /**
   * Record a failed publish attempt. Increments {@code attempts} and, once the bound is reached,
   * moves the row to DEAD so a poison event cannot block the relay (charter L6).
   */
  public void recordFailure(String error, int maxAttempts) {
    this.attempts += 1;
    this.lastError = truncate(error);
    if (this.attempts >= maxAttempts) {
      this.status = OutboxStatus.DEAD.name();
    }
  }

  private static String truncate(String error) {
    if (error == null) {
      return null;
    }
    return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
  }

  public OutboxEvent toEvent() {
    return new OutboxEvent(
        id,
        aggregateType,
        aggregateId,
        eventType,
        payload == null ? "{}" : payload.toString(),
        OutboxStatus.valueOf(status),
        attempts,
        lastError,
        createdAt,
        publishedAt);
  }

  public Long getId() {
    return id;
  }

  public String getAggregateType() {
    return aggregateType;
  }

  public UUID getAggregateId() {
    return aggregateId;
  }

  public String getEventType() {
    return eventType;
  }

  public JsonNode getPayload() {
    return payload;
  }

  public String getStatus() {
    return status;
  }

  public int getAttempts() {
    return attempts;
  }

  public String getLastError() {
    return lastError;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }
}
