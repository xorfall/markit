package com.markit.scraping.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataBookmarkContentRepository
    extends JpaRepository<BookmarkContentJpaEntity, UUID> {}
