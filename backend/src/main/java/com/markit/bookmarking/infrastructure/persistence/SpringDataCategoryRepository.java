package com.markit.bookmarking.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataCategoryRepository extends JpaRepository<CategoryJpaEntity, UUID> {

  Optional<CategoryJpaEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

  List<CategoryJpaEntity> findByCollectionIdAndOwnerIdOrderByPositionAsc(
      UUID collectionId, UUID ownerId);
}
