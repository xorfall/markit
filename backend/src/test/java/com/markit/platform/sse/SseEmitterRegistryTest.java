package com.markit.platform.sse;

import static org.assertj.core.api.Assertions.assertThat;

import com.markit.identity.domain.UserId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseEmitterRegistryTest {

  private final SseEmitterRegistry registry = new SseEmitterRegistry(new SimpleMeterRegistry());

  @Test
  void should_RegisterAndCountConnections() {
    UserId user = UserId.newId();
    assertThat(registry.create(user)).isNotNull();
    assertThat(registry.connectionCount(user)).isEqualTo(1);
    registry.create(user);
    assertThat(registry.connectionCount(user)).isEqualTo(2);
  }

  @Test
  void should_ScopeConnectionsPerUser() {
    UserId a = UserId.newId();
    UserId b = UserId.newId();
    registry.create(a);
    assertThat(registry.connectionCount(a)).isEqualTo(1);
    assertThat(registry.connectionCount(b)).isZero();
  }

  @Test
  void should_NoOp_When_PushingToUserWithoutConnection() {
    registry.push(UserId.newId(), "bookmark.state", "data"); // must not throw
  }

  @Test
  void should_PruneDeadEmitter_When_SendFails() {
    UserId user = UserId.newId();
    SseEmitter emitter = registry.create(user);
    emitter.complete(); // a completed emitter rejects further sends

    registry.push(user, "bookmark.state", "data"); // send fails -> emitter pruned

    assertThat(registry.connectionCount(user)).isZero();
  }
}
