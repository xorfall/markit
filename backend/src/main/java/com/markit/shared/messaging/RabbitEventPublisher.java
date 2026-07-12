package com.markit.shared.messaging;

import com.markit.shared.outbox.EventPublisher;
import com.markit.shared.outbox.OutboxEvent;
import java.nio.charset.StandardCharsets;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes an outbox event to RabbitMQ. The event's {@code eventType} is the topic routing key
 * (e.g. {@code bookmark.upserted}) and the already-serialized JSON payload is the message body, so
 * the consumer can dispatch on the routing key and parse the body once.
 */
@Component
public class RabbitEventPublisher implements EventPublisher {

  private final RabbitTemplate rabbitTemplate;

  public RabbitEventPublisher(RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  @Override
  public void publish(OutboxEvent event) {
    Message message =
        MessageBuilder.withBody(event.payload().getBytes(StandardCharsets.UTF_8))
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .setHeader("aggregateId", event.aggregateId())
            .build();
    // Standard observed send path: with `spring.rabbitmq.template.observation-enabled=true` the
    // RabbitTemplate injects the W3C `traceparent` header onto this message's properties before it
    // hits the broker (end-to-end tracing, NFR-OBS-004). We deliberately keep any custom headers on
    // the Message and let the template add the trace header — it does not overwrite ours.
    rabbitTemplate.send(MessagingConfig.EVENTS_EXCHANGE, event.eventType(), message);
  }
}
