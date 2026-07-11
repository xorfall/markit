package com.markit.shared.outbox;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository over {@link OutboxEventJpaEntity}. */
public interface SpringDataOutboxRepository extends JpaRepository<OutboxEventJpaEntity, Long> {

  /** A page of rows in the given status, oldest first (uses the partial PENDING index for polling). */
  List<OutboxEventJpaEntity> findByStatusOrderByIdAsc(String status, Pageable pageable);
}
