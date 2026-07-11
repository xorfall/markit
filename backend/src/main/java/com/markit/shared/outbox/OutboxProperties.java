package com.markit.shared.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Tunables for the outbox relay (see {@code markit.outbox.*} in application.yml). */
@ConfigurationProperties(prefix = "markit.outbox")
public class OutboxProperties {

  /** Relay poll interval in milliseconds. */
  private long pollIntervalMs = 1000;

  /** Max rows drained per poll. */
  private int batchSize = 100;

  /** Publish attempts before a row is marked DEAD (poison isolation). */
  private int maxAttempts = 5;

  public long getPollIntervalMs() {
    return pollIntervalMs;
  }

  public void setPollIntervalMs(long pollIntervalMs) {
    this.pollIntervalMs = pollIntervalMs;
  }

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }

  public int getMaxAttempts() {
    return maxAttempts;
  }

  public void setMaxAttempts(int maxAttempts) {
    this.maxAttempts = maxAttempts;
  }
}
