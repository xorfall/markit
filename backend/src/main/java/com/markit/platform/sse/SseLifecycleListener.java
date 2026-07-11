package com.markit.platform.sse;

import com.markit.identity.domain.UserId;
import com.markit.shared.sse.BookmarkLifecycleEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges committed {@link BookmarkLifecycleEvent}s to the SSE registry. Fires AFTER_COMMIT so the
 * client is only notified of state the database actually persisted; {@code fallbackExecution=true} so
 * events published outside a transaction still deliver.
 */
@Component
public class SseLifecycleListener {

  private final SseEmitterRegistry registry;

  public SseLifecycleListener(SseEmitterRegistry registry) {
    this.registry = registry;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onLifecycleEvent(BookmarkLifecycleEvent event) {
    registry.push(UserId.of(event.userId()), event.event(), event.data());
  }
}
