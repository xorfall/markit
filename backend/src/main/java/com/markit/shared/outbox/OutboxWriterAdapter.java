package com.markit.shared.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * JPA-backed {@link OutboxWriter}. Persists the event via a Spring Data repository, which enlists in
 * the caller's active transaction — so the outbox row and the state change commit atomically. It
 * does no I/O beyond that insert (no broker call), keeping the request transaction cheap.
 */
@Component
public class OutboxWriterAdapter implements OutboxWriter {

  private final SpringDataOutboxRepository repository;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public OutboxWriterAdapter(
      SpringDataOutboxRepository repository, ObjectMapper objectMapper, Clock clock) {
    this.repository = repository;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Override
  public void append(String aggregateType, UUID aggregateId, String eventType, Object payload) {
    JsonNode node = objectMapper.valueToTree(payload);
    repository.save(
        new OutboxEventJpaEntity(aggregateType, aggregateId, eventType, node, clock.instant()));
  }
}
