package com.markit.platform.sse;

import com.markit.identity.domain.UserId;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Holds the live SSE connections per user and pushes lifecycle events to them (FR-SCR-003). In-process
 * by design — this matches the single-instance deployment (ADR-0005, NFR-AVAIL-001); a multi-instance
 * setup would need a shared broker fan-out instead. Dead emitters are pruned on completion/timeout/error.
 */
@Component
public class SseEmitterRegistry {

  private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);
  private static final long TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

  private final Map<UserId, Set<SseEmitter>> byUser = new ConcurrentHashMap<>();

  /** Register a new stream for the user and send an initial handshake so proxies flush headers. */
  public SseEmitter create(UserId userId) {
    SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
    byUser.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(emitter);
    emitter.onCompletion(() -> remove(userId, emitter));
    emitter.onTimeout(() -> remove(userId, emitter));
    emitter.onError(e -> remove(userId, emitter));
    try {
      emitter.send(SseEmitter.event().name("connected").data("ok"));
    } catch (IOException e) {
      remove(userId, emitter);
    }
    return emitter;
  }

  /** Push a named event with a JSON payload to every live stream of the user. */
  public void push(UserId userId, String event, Object data) {
    Set<SseEmitter> emitters = byUser.get(userId);
    if (emitters == null) {
      return;
    }
    for (SseEmitter emitter : emitters) {
      try {
        emitter.send(SseEmitter.event().name(event).data(data));
      } catch (Exception ex) {
        log.debug("Dropping dead SSE emitter for user {}", userId, ex);
        remove(userId, emitter);
      }
    }
  }

  /** Live connection count for a user (for tests/metrics). */
  public int connectionCount(UserId userId) {
    Set<SseEmitter> emitters = byUser.get(userId);
    return emitters == null ? 0 : emitters.size();
  }

  private void remove(UserId userId, SseEmitter emitter) {
    Set<SseEmitter> emitters = byUser.get(userId);
    if (emitters != null) {
      emitters.remove(emitter);
      if (emitters.isEmpty()) {
        byUser.remove(userId);
      }
    }
  }
}
