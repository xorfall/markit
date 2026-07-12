package com.markit.shared.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
 *
 * <p>Domain metrics (C4, crown jewel): {@code markit.outbox.published{result}} throughput/errors,
 * {@code markit.outbox.pending.age.seconds} lag gauge (NFR-CONS-001, R-CON-04) and
 * {@code markit.outbox.dlq.size} DLQ-depth gauge (R-CON-05).
 *
 * <p>Note: HTTP RED ({@code http.server.requests}), JVM/runtime (C11) and Hikari connection-pool
 * (C10) metrics are exported automatically by Spring Boot / Micrometer — they are NOT re-implemented
 * here or anywhere else in this codebase.
 */
@Component
public class OutboxRelay {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

  private final SpringDataOutboxRepository repository;
  private final EventPublisher publisher;
  private final OutboxProperties properties;
  private final Clock clock;
  private final Counter publishedCounter;
  private final Counter deadCounter;

  public OutboxRelay(
      SpringDataOutboxRepository repository,
      EventPublisher publisher,
      OutboxProperties properties,
      Clock clock,
      MeterRegistry meterRegistry) {
    this.repository = repository;
    this.publisher = publisher;
    this.properties = properties;
    this.clock = clock;
    this.publishedCounter =
        Counter.builder("markit.outbox.published").tag("result", "published").register(meterRegistry);
    this.deadCounter =
        Counter.builder("markit.outbox.published").tag("result", "dead").register(meterRegistry);
    // Gauges poll the repo via a supplier (fine at this scale; the queries are cheap aggregates).
    Gauge.builder("markit.outbox.pending.age.seconds", this, OutboxRelay::pendingAgeSeconds)
        .register(meterRegistry);
    Gauge.builder("markit.outbox.dlq.size", this, OutboxRelay::dlqSize).register(meterRegistry);
  }

  /** Age in seconds of the oldest PENDING row (outbox lag), or 0.0 when the outbox is drained. */
  double pendingAgeSeconds() {
    Instant oldest = repository.findOldestCreatedAtByStatus(OutboxStatus.PENDING.name());
    if (oldest == null) {
      return 0.0;
    }
    long millis = Duration.between(oldest, clock.instant()).toMillis();
    return Math.max(0.0, millis / 1000.0);
  }

  /** Current DLQ depth: number of rows that exhausted retries and moved to DEAD. */
  double dlqSize() {
    return repository.countByStatus(OutboxStatus.DEAD.name());
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
        publishedCounter.increment();
      } catch (RuntimeException ex) {
        event.recordFailure(ex.getMessage(), properties.getMaxAttempts());
        if (OutboxStatus.DEAD.name().equals(event.getStatus())) {
          // Terminal outcome: retries exhausted, the row is now a poison event in the DLQ.
          deadCounter.increment();
        }
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
