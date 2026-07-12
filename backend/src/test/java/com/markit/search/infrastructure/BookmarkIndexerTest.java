package com.markit.search.infrastructure;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.markit.search.application.port.ContentSource;
import com.markit.shared.events.BookmarkDeletedPayload;
import com.markit.shared.events.BookmarkUpsertedPayload;
import com.markit.shared.events.EventTypes;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

class BookmarkIndexerTest {

  private final ElasticsearchOperations elasticsearch = mock(ElasticsearchOperations.class);
  private final ContentSource contentSource = mock(ContentSource.class);
  private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
  private final BookmarkIndexer indexer =
      new BookmarkIndexer(elasticsearch, contentSource, mapper, new SimpleMeterRegistry());

  private static BookmarkUpsertedPayload upsert(UUID id) {
    return new BookmarkUpsertedPayload(
        id,
        UUID.randomUUID(),
        UUID.randomUUID(),
        "https://example.com",
        "Title",
        "desc",
        "PENDING",
        Instant.parse("2026-07-11T00:00:00Z"));
  }

  @Test
  void should_UpsertDocumentById_WithMandatoryUserId() {
    var id = UUID.randomUUID();
    var payload = upsert(id);
    when(contentSource.findContent(id)).thenReturn(Optional.empty());

    indexer.index(payload);

    verify(elasticsearch)
        .save(
            argThat(
                (BookmarkDocument doc) ->
                    doc.getId().equals(id.toString())
                        && doc.getUserId().equals(payload.ownerId().toString())));
  }

  @Test
  void should_EnrichDocumentWithContent_When_ContentPresent() {
    var id = UUID.randomUUID();
    when(contentSource.findContent(id)).thenReturn(Optional.of("the full scraped page text"));

    indexer.index(upsert(id));

    verify(elasticsearch)
        .save(
            argThat((BookmarkDocument doc) -> "the full scraped page text".equals(doc.getContent())));
  }

  @Test
  void should_LeaveContentNull_When_ContentAbsent() {
    var id = UUID.randomUUID();
    when(contentSource.findContent(id)).thenReturn(Optional.empty());

    indexer.index(upsert(id));

    verify(elasticsearch).save(argThat((BookmarkDocument doc) -> doc.getContent() == null));
  }

  @Test
  void should_BeIdempotent_When_SameUpsertAppliedTwice() {
    var id = UUID.randomUUID();
    var payload = upsert(id);
    when(contentSource.findContent(id)).thenReturn(Optional.empty());

    indexer.index(payload);
    indexer.index(payload);

    // Both saves target the same id — ES overwrites, converging to one doc (NFR-CONS-002).
    verify(elasticsearch, times(2))
        .save(argThat((BookmarkDocument doc) -> doc.getId().equals(id.toString())));
  }

  @Test
  void should_DeleteById_And_BeIdempotentWhenAbsent() {
    var id = UUID.randomUUID();

    indexer.remove(new BookmarkDeletedPayload(id));
    indexer.remove(new BookmarkDeletedPayload(id)); // already-absent second delete is a no-op

    verify(elasticsearch, times(2)).delete(id.toString(), BookmarkDocument.class);
    // Delete is unchanged: no content read is involved.
    verifyNoInteractions(contentSource);
  }

  @Test
  void should_DispatchByRoutingKey() throws Exception {
    var id = UUID.randomUUID();
    when(contentSource.findContent(id)).thenReturn(Optional.empty());

    indexer.handle(EventTypes.BOOKMARK_UPSERTED, mapper.writeValueAsBytes(upsert(id)));
    verify(elasticsearch).save(any(BookmarkDocument.class));

    indexer.handle(
        EventTypes.BOOKMARK_DELETED, mapper.writeValueAsBytes(new BookmarkDeletedPayload(id)));
    verify(elasticsearch).delete(id.toString(), BookmarkDocument.class);
  }

  @Test
  void should_IgnoreUnknownEventType() {
    indexer.handle("bookmark.unknown", "{}".getBytes());
    verifyNoInteractions(elasticsearch);
  }
}
