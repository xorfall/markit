package com.markit.search.application.port;

import com.markit.search.application.IndexableBookmark;
import java.util.List;

/**
 * Read port over the Postgres source of truth, used to rebuild the ES projection end-to-end. An
 * adapter in the bookmarking context supplies the rows (Postgres holds all truth, so ES is
 * disposable and fully rebuildable, NFR-CONS-004).
 */
public interface BookmarkSource {

  List<IndexableBookmark> findAllForIndexing();
}
