package com.markit.search.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

/**
 * Creates the {@code bookmarks} index with its explicit mapping at startup if it does not yet exist.
 *
 * <p>Without this, the first {@code save()} auto-creates the index with a dynamic mapping that types
 * every field as analyzed {@code text}. That silently breaks the mandatory {@code userId} term filter
 * (a UUID indexed as {@code text} is tokenized on its hyphens, so a term query never matches), making
 * every search return nothing. The {@code @Field(type = Keyword)} mapping must therefore be applied
 * before any document is written (NFR-SEC-002, FR-SRC-001).
 */
@Component
public class ElasticsearchIndexInitializer implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(ElasticsearchIndexInitializer.class);

  private final ElasticsearchOperations operations;

  public ElasticsearchIndexInitializer(ElasticsearchOperations operations) {
    this.operations = operations;
  }

  @Override
  public void run(ApplicationArguments args) {
    IndexOperations index = operations.indexOps(BookmarkDocument.class);
    if (!index.exists()) {
      index.createWithMapping();
      log.info("Created Elasticsearch index 'bookmarks' with explicit mapping");
    }
  }
}
