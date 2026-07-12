package com.markit.shared.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OutboxRelayTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final SpringDataOutboxRepository repository = mock(SpringDataOutboxRepository.class);
  private final EventPublisher publisher = mock(EventPublisher.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC);

  private OutboxProperties properties;
  private OutboxRelay relay;

  @BeforeEach
  void setUp() {
    properties = new OutboxProperties();
    properties.setBatchSize(100);
    properties.setMaxAttempts(3);
    relay = new OutboxRelay(repository, publisher, properties, clock, new SimpleMeterRegistry());
  }

  private OutboxEventJpaEntity pending(String eventType) {
    return new OutboxEventJpaEntity(
        "bookmark", UUID.randomUUID(), eventType, mapper.createObjectNode(), clock.instant());
  }

  @Test
  void should_PublishInIdOrderThenMarkPublished() {
    var first = pending("bookmark.upserted");
    var second = pending("bookmark.deleted");
    when(repository.findByStatusOrderByIdAsc(eq("PENDING"), any()))
        .thenReturn(List.of(first, second));

    relay.relay();

    ArgumentCaptor<OutboxEvent> published = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(publisher, times(2)).publish(published.capture());
    assertThat(published.getAllValues())
        .extracting(OutboxEvent::aggregateId)
        .containsExactly(first.getAggregateId(), second.getAggregateId()); // order preserved
    assertThat(first.getStatus()).isEqualTo("PUBLISHED");
    assertThat(second.getStatus()).isEqualTo("PUBLISHED");
    verify(repository).saveAll(anyList());
  }

  @Test
  void should_MoveToDead_When_EventKeepsFailingPastMaxAttempts() {
    var poison = pending("bookmark.upserted");
    when(repository.findByStatusOrderByIdAsc(eq("PENDING"), any())).thenReturn(List.of(poison));
    doThrow(new RuntimeException("broker down")).when(publisher).publish(any());

    relay.relay();
    assertThat(poison.getAttempts()).isEqualTo(1);
    assertThat(poison.getStatus()).isEqualTo("PENDING"); // still retryable

    relay.relay();
    assertThat(poison.getAttempts()).isEqualTo(2);

    relay.relay();
    assertThat(poison.getAttempts()).isEqualTo(3);
    assertThat(poison.getStatus()).isEqualTo("DEAD"); // poison isolated, cannot block the relay
  }
}
