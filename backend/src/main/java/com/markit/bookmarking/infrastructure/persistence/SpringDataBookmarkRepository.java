package com.markit.bookmarking.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataBookmarkRepository extends JpaRepository<BookmarkJpaEntity, UUID> {

  Optional<BookmarkJpaEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

  List<BookmarkJpaEntity> findByCategoryIdAndOwnerIdOrderByPositionAsc(
      UUID categoryId, UUID ownerId);

  boolean existsByCategoryIdAndUrl(UUID categoryId, String url);
}
