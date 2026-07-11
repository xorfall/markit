package com.markit.shared.outbox;

import java.util.UUID;

/**
 * Port for appending a domain event to the outbox. Implementations MUST enlist in the caller's
 * current transaction so the event row and the state change commit atomically (no dual write,
 * ADR-0003). Never publishes to a broker directly.
 */
public interface OutboxWriter {

  /**
   * Append an event to the outbox within the current transaction.
   *
   * @param aggregateType the aggregate kind (e.g. {@code "bookmark"})
   * @param aggregateId the aggregate id (preserves per-aggregate order on replay)
   * @param eventType the event/routing key (e.g. {@code "bookmark.upserted"})
   * @param payload a serializable payload object, stored as JSON
   */
  void append(String aggregateType, UUID aggregateId, String eventType, Object payload);
}
