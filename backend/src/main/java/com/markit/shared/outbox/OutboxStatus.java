package com.markit.shared.outbox;

/** Lifecycle of an outbox row: PENDING → PUBLISHED, or PENDING → DEAD after bounded retries. */
public enum OutboxStatus {
  PENDING,
  PUBLISHED,
  DEAD
}
