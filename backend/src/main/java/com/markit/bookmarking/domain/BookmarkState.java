package com.markit.bookmarking.domain;

/** Lifecycle state of a {@link Bookmark} (data-model §7). Scraping transitions live in a later slice. */
public enum BookmarkState {
  PENDING,
  INDEXED,
  FAILED
}
