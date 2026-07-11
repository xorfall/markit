package com.markit.shared.outbox;

import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polling relay (ADR-0003). On each tick it reads a batch of PENDING rows in id order, publishes
 * each to the broker via {@link EventPublisher}, and marks it PUBLISHED — in that order, so a crash
 * between publish and mark simply republishes on the next tick (at-least-once). Publishing in id
 * order through a single exchange/queue preserves per-aggregate order (NFR-CONS-003). A row that
 * keeps failing is retried and, past {@code maxAttempts}, moved to DEAD so it cannot block the relay
 * (poison isolation).
 */
@Component
public class OutboxRelay {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

  private final SpringDataOutboxRepository repository;
  private final EventPublisher publisher;
  private final OutboxProperties properties;
  private final Clock clock;

  public OutboxRelay(
      SpringDataOutboxRepository repository,
      EventPublisher publisher,
      OutboxProperties properties,
      Clock clock) {
    this.repository = repository;
    this.publisher = publisher;
    this.properties = properties;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${markit.outbox.poll-interval-ms:1000}")
  @Transactional
  public void relay() {
    List<OutboxEventJpaEntity> batch =
        repository.findByStatusOrderByIdAsc(
            OutboxStatus.PENDING.name(), PageRequest.of(0, properties.getBatchSize()));
    for (OutboxEventJpaEntity event : batch) {
      try {
        publisher.publish(event.toEvent());
        event.markPublished(clock.instant());
      } catch (RuntimeException ex) {
        event.recordFailure(ex.getMessage(), properties.getMaxAttempts());
        log.warn(
            "Outbox publish failed for event id={} attempt={} status={}",
            event.getId(),
            event.getAttempts(),
            event.getStatus(),
            ex);
      }
    }
    repository.saveAll(batch);
  }
}
