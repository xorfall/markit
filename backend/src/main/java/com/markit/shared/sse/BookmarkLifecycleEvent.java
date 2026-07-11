package com.markit.shared.sse;

import java.util.UUID;

/**
 * An in-process application event signalling a user-visible change in a bookmark's scrape lifecycle,
 * published after the state change commits and pushed to that user's SSE stream (FR-SCR-003). Neutral
 * (no cross-context domain types) so producer and consumer both depend only on {@code shared}.
 *
 * @param userId owner to route the event to
 * @param event SSE event name (api-contract §6): {@code bookmark.metadata} / {@code bookmark.state}
 * @param data JSON-serializable payload (see {@link SsePayloads})
 */
public record BookmarkLifecycleEvent(UUID userId, String event, Object data) {}
