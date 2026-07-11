package com.markit.shared.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * A read-only view of an outbox row, handed to the {@link EventPublisher}. {@code payload} is the
 * already-serialized JSON body; {@code eventType} is used as the RabbitMQ routing key so the
 * consumer can tell an upsert from a delete.
 */
public record OutboxEvent(
    Long id,
    String aggregateType,
    UUID aggregateId,
    String eventType,
    String payload,
    OutboxStatus status,
    int attempts,
    String lastError,
    Instant createdAt,
    Instant publishedAt) {}
