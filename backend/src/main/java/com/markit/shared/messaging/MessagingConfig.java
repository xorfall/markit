package com.markit.shared.messaging;

import com.markit.shared.events.EventTypes;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for the outbox pipeline (ADR-0003). A single topic exchange {@code markit.events}
 * carries bookmark events; the ES-indexer queue binds {@code bookmark.*} and is consumed
 * single-threaded to preserve per-aggregate order. Messages the consumer rejects after its retry
 * budget are dead-lettered to a DLQ so one poison message cannot block the queue.
 */
@Configuration
public class MessagingConfig {

  public static final String EVENTS_EXCHANGE = "markit.events";
  public static final String INDEX_QUEUE = "markit.es-indexer";
  public static final String DEAD_LETTER_EXCHANGE = "markit.events.dlx";
  public static final String DEAD_LETTER_QUEUE = "markit.es-indexer.dlq";
  public static final String BOOKMARK_ROUTING_PATTERN = "bookmark.*";

  /** Queue the Python scraper worker consumes; it receives every {@code scrape.requested} event. */
  public static final String SCRAPE_REQUESTS_QUEUE = "markit.scrape-requests";

  /** Queue the Java orchestrator consumes for scrape results (metadata / content / failure). */
  public static final String SCRAPE_RESULTS_QUEUE = "markit.scrape-results";

  @Bean
  public TopicExchange eventsExchange() {
    return new TopicExchange(EVENTS_EXCHANGE, true, false);
  }

  @Bean
  public DirectExchange deadLetterExchange() {
    return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
  }

  @Bean
  public Queue indexQueue() {
    return QueueBuilder.durable(INDEX_QUEUE)
        .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
        .withArgument("x-dead-letter-routing-key", DEAD_LETTER_QUEUE)
        .build();
  }

  @Bean
  public Queue deadLetterQueue() {
    return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
  }

  @Bean
  public Binding indexBinding() {
    return BindingBuilder.bind(indexQueue()).to(eventsExchange()).with(BOOKMARK_ROUTING_PATTERN);
  }

  @Bean
  public Binding deadLetterBinding() {
    return BindingBuilder.bind(deadLetterQueue())
        .to(deadLetterExchange())
        .with(DEAD_LETTER_QUEUE);
  }

  /**
   * Requests to scrape. Declared here so the topic binding exists even though the consumer (the
   * Python worker) is a later slice; the outbox relay can publish {@code scrape.requested} reliably.
   */
  @Bean
  public Queue scrapeRequestsQueue() {
    return QueueBuilder.durable(SCRAPE_REQUESTS_QUEUE).build();
  }

  @Bean
  public Binding scrapeRequestsBinding() {
    return BindingBuilder.bind(scrapeRequestsQueue())
        .to(eventsExchange())
        .with(EventTypes.SCRAPE_REQUESTED);
  }

  /**
   * Scrape results consumed by the Java orchestrator. Bound to the three result routing keys
   * explicitly (not {@code scrape.*}) so {@code scrape.requested} is NOT delivered here. Poison
   * messages are dead-lettered to the shared DLQ.
   */
  @Bean
  public Queue scrapeResultsQueue() {
    return QueueBuilder.durable(SCRAPE_RESULTS_QUEUE)
        .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
        .withArgument("x-dead-letter-routing-key", DEAD_LETTER_QUEUE)
        .build();
  }

  @Bean
  public Binding scrapeMetadataBinding() {
    return BindingBuilder.bind(scrapeResultsQueue())
        .to(eventsExchange())
        .with(EventTypes.SCRAPE_METADATA_READY);
  }

  @Bean
  public Binding scrapeContentBinding() {
    return BindingBuilder.bind(scrapeResultsQueue())
        .to(eventsExchange())
        .with(EventTypes.SCRAPE_CONTENT_COMPLETED);
  }

  @Bean
  public Binding scrapeFailedBinding() {
    return BindingBuilder.bind(scrapeResultsQueue())
        .to(eventsExchange())
        .with(EventTypes.SCRAPE_FAILED);
  }
}
