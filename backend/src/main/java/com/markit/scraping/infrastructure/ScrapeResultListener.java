package com.markit.scraping.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.markit.scraping.application.ScrapeOrchestrator;
import com.markit.shared.events.EventTypes;
import com.markit.shared.events.ScrapeContentCompletedPayload;
import com.markit.shared.events.ScrapeFailedPayload;
import com.markit.shared.events.ScrapeMetadataReadyPayload;
import com.markit.shared.messaging.MessagingConfig;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes scrape results off the scrape-results queue and hands them to {@link ScrapeOrchestrator}.
 * These are trusted internal system events (produced by the scraper worker), so they are NOT
 * owner-scoped: the orchestrator loads the bookmark by id. Mirrors {@code BookmarkIndexer} — the
 * dispatch method is package-visible for direct unit testing.
 */
@Component
public class ScrapeResultListener {

  private static final Logger log = LoggerFactory.getLogger(ScrapeResultListener.class);

  private final ScrapeOrchestrator orchestrator;
  private final ObjectMapper objectMapper;
  private final MeterRegistry meterRegistry;

  public ScrapeResultListener(
      ScrapeOrchestrator orchestrator, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
    this.orchestrator = orchestrator;
    this.objectMapper = objectMapper;
    this.meterRegistry = meterRegistry;
  }

  @RabbitListener(queues = MessagingConfig.SCRAPE_RESULTS_QUEUE)
  public void onMessage(Message message) {
    String eventType = message.getMessageProperties().getReceivedRoutingKey();
    handle(eventType, message.getBody());
  }

  /** Dispatch on the routing key. Package-visible for direct unit testing. */
  void handle(String eventType, byte[] payload) {
    switch (eventType) {
      case EventTypes.SCRAPE_METADATA_READY -> {
        countResult("metadata");
        orchestrator.onMetadataReady(parse(payload, ScrapeMetadataReadyPayload.class));
      }
      case EventTypes.SCRAPE_CONTENT_COMPLETED -> {
        countResult("content");
        orchestrator.onContentCompleted(parse(payload, ScrapeContentCompletedPayload.class));
      }
      case EventTypes.SCRAPE_FAILED -> {
        countResult("failed");
        orchestrator.onFailed(parse(payload, ScrapeFailedPayload.class));
      }
      default -> log.warn("Ignoring unknown scrape event type {}", eventType);
    }
  }

  /** C6 domain metric: one increment per consumed scrape-result message, tagged by phase. */
  private void countResult(String phase) {
    meterRegistry.counter("markit.scrape.results", "phase", phase).increment();
  }

  private <T> T parse(byte[] payload, Class<T> type) {
    try {
      return objectMapper.readValue(payload, type);
    } catch (IOException e) {
      throw new UncheckedIOException("Malformed event payload for " + type.getSimpleName(), e);
    }
  }
}
