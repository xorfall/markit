package com.markit.bookmarking.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataBookmarkRepository extends JpaRepository<BookmarkJpaEntity, UUID> {

  Optional<BookmarkJpaEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

  List<BookmarkJpaEntity> findByCategoryIdAndOwnerIdOrderByPositionAsc(
      UUID categoryId, UUID ownerId);

  boolean existsByCategoryIdAndUrl(UUID categoryId, String url);

  /**
   * Degraded-mode metadata search (FR-SRC-003, ADR-0007): owner-scoped, case-insensitive match over
   * the small {@code title}/{@code description} fields only — never content. An optional {@code
   * categoryId} narrows the scope. Ordered newest-first for a stable page.
   */
  @Query(
      """
      SELECT b FROM BookmarkJpaEntity b
      WHERE b.ownerId = :ownerId
        AND (:categoryId IS NULL OR b.categoryId = :categoryId)
        AND (LOWER(b.title) LIKE LOWER(CONCAT('%', :term, '%'))
             OR LOWER(b.description) LIKE LOWER(CONCAT('%', :term, '%')))
      ORDER BY b.createdAt DESC
      """)
  List<BookmarkJpaEntity> searchMetadata(
      @Param("ownerId") UUID ownerId,
      @Param("categoryId") UUID categoryId,
      @Param("term") String term,
      Pageable pageable);
}
