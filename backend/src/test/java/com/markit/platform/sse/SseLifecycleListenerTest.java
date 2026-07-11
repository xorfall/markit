package com.markit.platform.sse;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.markit.identity.domain.UserId;
import com.markit.shared.sse.BookmarkLifecycleEvent;
import com.markit.shared.sse.SsePayloads;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SseLifecycleListenerTest {

  private final SseEmitterRegistry registry = mock(SseEmitterRegistry.class);
  private final SseLifecycleListener listener = new SseLifecycleListener(registry);

  @Test
  void should_PushEventToOwnersStream() {
    UUID userId = UUID.randomUUID();
    var data = new SsePayloads.State("bookmark-1", "INDEXED", null);

    listener.onLifecycleEvent(new BookmarkLifecycleEvent(userId, "bookmark.state", data));

    verify(registry).push(UserId.of(userId), "bookmark.state", data);
  }
}
