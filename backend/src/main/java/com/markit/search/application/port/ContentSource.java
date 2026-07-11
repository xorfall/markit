package com.markit.search.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Read port over the scraped page text held in Postgres ({@code bookmark_content}, 1:1 with a
 * bookmark). The ES projection enriches its document with content at index time from here rather
 * than shipping a ≤1 MB blob through RabbitMQ (architecture §4.1, S5). Returns {@link Optional#empty}
 * until the scrape's content phase has stored a row.
 */
public interface ContentSource {

  Optional<String> findContent(UUID bookmarkId);
}
