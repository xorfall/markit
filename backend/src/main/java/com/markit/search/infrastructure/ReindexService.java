package com.markit.search.infrastructure;

import com.markit.search.application.IndexableBookmark;
import com.markit.search.application.port.BookmarkSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Service;

/**
 * Full rebuild of the ES projection from Postgres (NFR-CONS-004) — the ultimate proof the projection
 * is disposable. Wipes the {@code bookmarks} index, recreates it with the mapping, then upserts every
 * bookmark. Idempotent by construction (save-by-id), so it is safe to re-run.
 */
@Service
public class ReindexService {

  private static final Logger log = LoggerFactory.getLogger(ReindexService.class);

  private final ElasticsearchOperations elasticsearch;
  private final BookmarkSource bookmarkSource;

  public ReindexService(ElasticsearchOperations elasticsearch, BookmarkSource bookmarkSource) {
    this.elasticsearch = elasticsearch;
    this.bookmarkSource = bookmarkSource;
  }

  /** Wipe and rebuild the index from the source of truth. Returns the number of docs indexed. */
  public long reindexAll() {
    IndexOperations index = elasticsearch.indexOps(BookmarkDocument.class);
    if (index.exists()) {
      index.delete();
    }
    index.create();
    index.putMapping();

    long count = 0;
    for (IndexableBookmark bookmark : bookmarkSource.findAllForIndexing()) {
      elasticsearch.save(BookmarkDocument.from(bookmark));
      count++;
    }
    log.info("Reindexed {} bookmarks into ES", count);
    return count;
  }
}
