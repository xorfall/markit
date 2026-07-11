package com.markit.shared.outbox;

/**
 * Port for shipping an outbox event to the message broker. Called only by the {@link OutboxRelay},
 * never inside a request transaction. May throw on transport failure — the relay treats that as a
 * publish attempt and retries/dead-letters accordingly.
 */
public interface EventPublisher {

  void publish(OutboxEvent event);
}
