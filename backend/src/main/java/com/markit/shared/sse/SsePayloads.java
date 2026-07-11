package com.markit.shared.sse;

/** SSE data payloads pushed to the client (api-contract §6). */
public final class SsePayloads {

  private SsePayloads() {}

  /** Phase-1 metadata arrived (bookmark still PENDING). */
  public record Metadata(String id, String title, String description) {}

  /** Lifecycle state changed to INDEXED or FAILED (failureReason set only when FAILED). */
  public record State(String id, String state, String failureReason) {}
}
