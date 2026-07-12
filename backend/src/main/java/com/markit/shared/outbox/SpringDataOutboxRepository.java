package com.markit.shared.outbox;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository over {@link OutboxEventJpaEntity}. */
public interface SpringDataOutboxRepository extends JpaRepository<OutboxEventJpaEntity, Long> {

  /** A page of rows in the given status, oldest first (uses the partial PENDING index for polling). */
  List<OutboxEventJpaEntity> findByStatusOrderByIdAsc(String status, Pageable pageable);

  /**
   * Timestamp of the oldest row in the given status, or {@code null} when there are none. Backs the
   * {@code markit.outbox.pending.age.seconds} lag gauge (NFR-CONS-001, R-CON-04) — a single cheap
   * aggregate over the partial PENDING index.
   */
  @Query("select min(e.createdAt) from OutboxEventJpaEntity e where e.status = :status")
  Instant findOldestCreatedAtByStatus(@Param("status") String status);

  /** Number of rows in the given status. Backs the {@code markit.outbox.dlq.size} gauge (R-CON-05). */
  long countByStatus(String status);
}
