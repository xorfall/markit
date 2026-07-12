package com.markit.search.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.markit.search.application.port.ContentSource;
import com.markit.shared.events.BookmarkDeletedPayload;
import com.markit.shared.events.BookmarkUpsertedPayload;
import com.markit.shared.events.EventTypes;
import com.markit.shared.messaging.MessagingConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Component;

/**
 * ES indexer: the idempotent consumer that makes at-least-once delivery effectively-once. Every op
 * keys on {@code bookmarkId} — upsert is save-by-id (applying twice yields the same doc), delete is
 * delete-by-id (an already-absent doc is a no-op) — so re-delivery converges to the same ES state
 * (NFR-CONS-002). The queue is consumed single-threaded to preserve per-aggregate order.
 */
@Component
public class BookmarkIndexer {

  private static final Logger log = LoggerFactory.getLogger(BookmarkIndexer.class);

  private static final String OP_UPSERT = "upsert";
  private static final String OP_DELETE = "delete";
  private static final String RESULT_OK = "ok";
  private static final String RESULT_ERROR = "error";

  private final ElasticsearchOperations elasticsearch;
  private final ContentSource contentSource;
  private final ObjectMapper objectMapper;
  private final MeterRegistry meterRegistry;

  public BookmarkIndexer(
      ElasticsearchOperations elasticsearch,
      ContentSource contentSource,
      ObjectMapper objectMapper,
      MeterRegistry meterRegistry) {
    this.elasticsearch = elasticsearch;
    this.contentSource = contentSource;
    this.objectMapper = objectMapper;
    this.meterRegistry = meterRegistry;
  }

  @RabbitListener(queues = MessagingConfig.INDEX_QUEUE)
  public void onMessage(Message message) {
    String eventType = message.getMessageProperties().getReceivedRoutingKey();
    handle(eventType, message.getBody());
  }

  /** Dispatch on the event type (routing key). Package-visible for direct unit testing. */
  void handle(String eventType, byte[] payload) {
    switch (eventType) {
      case EventTypes.BOOKMARK_UPSERTED -> index(parse(payload, BookmarkUpsertedPayload.class));
      case EventTypes.BOOKMARK_DELETED -> remove(parse(payload, BookmarkDeletedPayload.class));
      default -> log.warn("Ignoring unknown event type {}", eventType);
    }
  }

  /**
   * Idempotent upsert: save-by-id overwrites any existing doc with the same id. The document is
   * enriched with the bookmark's current content read from Postgres (architecture §4.1) — kept out
   * of the event payload so a ≤1 MB blob never rides through RabbitMQ. Content is {@code null} until
   * the scrape's content phase has stored it.
   */
  void index(BookmarkUpsertedPayload payload) {
    // C7 crown-jewel RED: time the ES op and count its outcome. On error we still re-throw so the
    // listener nacks/DLQs — the metric records the failure, it does not swallow it.
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      String content = contentSource.findContent(payload.bookmarkId()).orElse(null);
      elasticsearch.save(BookmarkDocument.from(payload).withContent(content));
      recordOp(sample, OP_UPSERT, RESULT_OK);
    } catch (RuntimeException ex) {
      recordOp(sample, OP_UPSERT, RESULT_ERROR);
      throw ex;
    }
  }

  /** Idempotent delete: delete-by-id; ES treats an absent id as a no-op (does not throw). */
  void remove(BookmarkDeletedPayload payload) {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      elasticsearch.delete(payload.bookmarkId().toString(), BookmarkDocument.class);
      recordOp(sample, OP_DELETE, RESULT_OK);
    } catch (RuntimeException ex) {
      recordOp(sample, OP_DELETE, RESULT_ERROR);
      throw ex;
    }
  }

  private void recordOp(Timer.Sample sample, String op, String result) {
    sample.stop(meterRegistry.timer("markit.index.op.duration", "op", op));
    meterRegistry.counter("markit.index.ops", "op", op, "result", result).increment();
  }

  private <T> T parse(byte[] payload, Class<T> type) {
    try {
      return objectMapper.readValue(payload, type);
    } catch (IOException e) {
      throw new UncheckedIOException("Malformed event payload for " + type.getSimpleName(), e);
    }
  }
}
