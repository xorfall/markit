package com.markit.bookmarking.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataCollectionRepository extends JpaRepository<CollectionJpaEntity, UUID> {

  Optional<CollectionJpaEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

  List<CollectionJpaEntity> findByOwnerIdOrderByPositionAsc(UUID ownerId);
}
