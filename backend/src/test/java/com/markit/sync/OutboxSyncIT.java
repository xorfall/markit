package com.markit.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.markit.bookmarking.application.BookmarkService;
import com.markit.bookmarking.application.CategoryService;
import com.markit.bookmarking.application.CollectionService;
import com.markit.bookmarking.domain.Bookmark;
import com.markit.bookmarking.domain.Category;
import com.markit.bookmarking.domain.Collection;
import com.markit.identity.application.port.UserRepository;
import com.markit.identity.domain.Email;
import com.markit.identity.domain.User;
import com.markit.identity.domain.UserId;
import com.markit.scraping.application.ScrapeOrchestrator;
import com.markit.search.infrastructure.BookmarkDocument;
import com.markit.shared.events.BookmarkUpsertedPayload;
import com.markit.shared.events.EventTypes;
import com.markit.shared.events.ScrapeContentCompletedPayload;
import com.markit.shared.messaging.MessagingConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.query.Query;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end proof of the transactional-outbox pipeline (ADR-0003, NFR-CONS-001/002/003/004): a state
 * change committed through the domain services is carried by the outbox → relay → RabbitMQ → indexer
 * chain into the Elasticsearch projection, and removed on delete (including cascade). Re-delivery of
 * the same event converges to a single document (effectively-once). Runs in {@code verify} (failsafe)
 * since it needs Docker; the crown-jewel logic is also unit-tested in OutboxRelayTest/BookmarkIndexerTest.
 */
@SpringBootTest
@Testcontainers
class OutboxSyncIT {

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

  @Autowired ElasticsearchOperations operations;
  @Autowired UserRepository users;
  @Autowired CollectionService collections;
  @Autowired CategoryService categories;
  @Autowired BookmarkService bookmarks;
  @Autowired ScrapeOrchestrator scrape;
  @Autowired RabbitTemplate rabbit;
  @Autowired ObjectMapper objectMapper;

  private UserId owner;

  @BeforeEach
  void reset() {
    IndexOperations index = operations.indexOps(BookmarkDocument.class);
    if (index.exists()) {
      index.delete();
    }
    index.create();
    index.putMapping();

    owner = UserId.newId();
    // Unique per run: @BeforeEach reruns for every test method against the same container,
    // and the email column is UNIQUE — a hardcoded address collides on the second method.
    users.save(
        User.registerWithPassword(
            owner, new Email("sync-" + UUID.randomUUID() + "@example.com"), "hash", Instant.now()));
  }

  private Category seedCategory() {
    Collection collection = collections.create(owner, "Reading");
    return categories.create(owner, collection.id(), "Articles");
  }

  private BookmarkDocument doc(String id) {
    return operations.get(id, BookmarkDocument.class);
  }

  @Test
  void should_ProjectNewBookmarkIntoElasticsearch() {
    Category category = seedCategory();
    Bookmark bookmark = bookmarks.add(owner, category.id(), "https://a.test/one");

    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> {
              BookmarkDocument found = doc(bookmark.id().asString());
              assertThat(found).isNotNull();
              assertThat(found.getUserId()).isEqualTo(owner.asString()); // isolation field
              assertThat(found.getState()).isEqualTo("PENDING");
            });
  }

  @Test
  void should_EnrichWithContentAndIndexed_When_ContentPhaseCompletes() {
    Category category = seedCategory();
    Bookmark bookmark = bookmarks.add(owner, category.id(), "https://a.test/two");
    await().atMost(Duration.ofSeconds(15)).until(() -> doc(bookmark.id().asString()) != null);

    scrape.onContentCompleted(
        new ScrapeContentCompletedPayload(
            bookmark.id().value(), "the mitochondria is the powerhouse of the cell"));

    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> {
              BookmarkDocument found = doc(bookmark.id().asString());
              assertThat(found).isNotNull();
              assertThat(found.getState()).isEqualTo("INDEXED");
              assertThat(found.getContent()).contains("powerhouse of the cell");
            });
  }

  @Test
  void should_RemoveFromElasticsearch_When_BookmarkDeleted() {
    Category category = seedCategory();
    Bookmark bookmark = bookmarks.add(owner, category.id(), "https://a.test/three");
    await().atMost(Duration.ofSeconds(15)).until(() -> doc(bookmark.id().asString()) != null);

    bookmarks.delete(owner, bookmark.id());

    await()
        .atMost(Duration.ofSeconds(15))
        .until(() -> doc(bookmark.id().asString()) == null);
  }

  @Test
  void should_RemoveDescendantsFromElasticsearch_When_CollectionDeletedByCascade() {
    Collection collection = collections.create(owner, "Temp");
    Category category = categories.create(owner, collection.id(), "Temp cat");
    Bookmark bookmark = bookmarks.add(owner, category.id(), "https://a.test/four");
    await().atMost(Duration.ofSeconds(15)).until(() -> doc(bookmark.id().asString()) != null);

    collections.delete(owner, collection.id()); // cascade must emit bookmark.deleted per descendant

    await()
        .atMost(Duration.ofSeconds(15))
        .until(() -> doc(bookmark.id().asString()) == null);
  }

  @Test
  void should_ConvergeToSingleDocument_When_SameUpsertDeliveredTwice() throws Exception {
    UUID bookmarkId = UUID.randomUUID();
    BookmarkUpsertedPayload payload =
        new BookmarkUpsertedPayload(
            bookmarkId,
            owner.value(),
            UUID.randomUUID(),
            "https://a.test/dup",
            "Duplicate",
            "desc",
            "INDEXED",
            Instant.parse("2026-07-11T00:00:00Z"));

    publish(EventTypes.BOOKMARK_UPSERTED, payload);
    publish(EventTypes.BOOKMARK_UPSERTED, payload); // at-least-once re-delivery

    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> {
              operations.indexOps(BookmarkDocument.class).refresh();
              // id-keyed upsert: re-delivery overwrites, never duplicates (NFR-CONS-002).
              assertThat(operations.count(Query.findAll(), BookmarkDocument.class)).isEqualTo(1L);
              assertThat(doc(bookmarkId.toString())).isNotNull();
            });
  }

  private void publish(String routingKey, Object payload) throws Exception {
    Message message =
        MessageBuilder.withBody(objectMapper.writeValueAsBytes(payload))
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .build();
    rabbit.send(MessagingConfig.EVENTS_EXCHANGE, routingKey, message);
  }
}
