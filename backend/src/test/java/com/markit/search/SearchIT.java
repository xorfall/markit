package com.markit.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.markit.identity.domain.UserId;
import com.markit.search.application.SearchResults;
import com.markit.search.infrastructure.BookmarkDocument;
import com.markit.search.infrastructure.SearchService;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end content-aware search (FR-SRC-001/002, NFR-SEC-002). Proves the differentiating feature:
 * a bookmark is found by a term that appears only in its scraped CONTENT (not its title), with a
 * highlighted snippet — and that a second user searching the same term gets nothing (per-user
 * isolation). Runs in {@code verify} (failsafe) since it needs Docker.
 */
@SpringBootTest
@Testcontainers
class SearchIT {

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

  @Container
  @ServiceConnection
  static RabbitMQContainer rabbitmq =
      new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"));

  @Container
  @ServiceConnection
  static ElasticsearchContainer elasticsearch =
      new ElasticsearchContainer(
              DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.15.3"))
          .withEnv("xpack.security.enabled", "false");

  @Autowired private ElasticsearchOperations operations;
  @Autowired private SearchService searchService;

  private final UserId owner = UserId.of(UUID.randomUUID());
  private final UserId otherUser = UserId.of(UUID.randomUUID());

  @BeforeEach
  void resetIndex() {
    IndexOperations index = operations.indexOps(BookmarkDocument.class);
    if (index.exists()) {
      index.delete();
    }
    index.create();
    index.putMapping();
  }

  @Test
  void should_FindBookmarkByContent_AndIsolateAcrossUsers() {
    // The term "photosynthesis" appears ONLY in the content, never in the title.
    operations.save(
        new BookmarkDocument(
            UUID.randomUUID().toString(),
            owner.asString(),
            UUID.randomUUID().toString(),
            "https://plants.test/guide",
            "A gardening guide",
            "how to grow things",
            "Leaves convert light through photosynthesis into sugars.",
            "INDEXED",
            Instant.parse("2026-07-11T00:00:00Z")));
    operations.save(
        new BookmarkDocument(
            UUID.randomUUID().toString(),
            owner.asString(),
            UUID.randomUUID().toString(),
            "https://cooking.test/recipe",
            "A cooking recipe",
            "dinner ideas",
            "Boil the pasta for ten minutes.",
            "INDEXED",
            Instant.parse("2026-07-11T00:00:00Z")));
    operations.indexOps(BookmarkDocument.class).refresh();

    // Owner finds the content match with a highlighted snippet.
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              SearchResults results =
                  searchService.search(owner, "photosynthesis", null, 20, null);
              assertThat(results.degraded()).isFalse();
              assertThat(results.contentSearchAvailable()).isTrue();
              assertThat(results.results()).hasSize(1);
              assertThat(results.results().get(0).snippet())
                  .contains("<em>photosynthesis</em>");
            });

    // A different user searching the same term gets nothing (isolation, NFR-SEC-002).
    SearchResults isolated = searchService.search(otherUser, "photosynthesis", null, 20, null);
    assertThat(isolated.results()).isEmpty();
  }
}
