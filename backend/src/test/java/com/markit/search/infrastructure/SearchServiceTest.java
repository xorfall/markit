package com.markit.search.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.markit.identity.domain.UserId;
import com.markit.search.application.MatchedBookmark;
import com.markit.search.application.SearchResults;
import com.markit.search.application.port.MetadataSearchSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;

class SearchServiceTest {

  private final ElasticsearchOperations elasticsearch = mock(ElasticsearchOperations.class);
  private final MetadataSearchSource metadataSearchSource = mock(MetadataSearchSource.class);
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final SearchService service =
      new SearchService(elasticsearch, metadataSearchSource, meterRegistry);

  private final UserId user = UserId.of(UUID.randomUUID());

  @Test
  void should_ReturnDegradedMetadataResults_When_ElasticsearchFails() {
    // ES cluster is down: the search call blows up.
    when(elasticsearch.search(any(NativeQuery.class), eq(BookmarkDocument.class)))
        .thenThrow(new RuntimeException("connection refused"));
    var match =
        new MatchedBookmark("bm-1", "cat-1", "https://a.test", "Alpha", "an alpha note", "INDEXED");
    when(metadataSearchSource.search(eq(user.value()), eq("alpha"), any(), anyInt(), anyInt()))
        .thenReturn(List.of(match));

    // The ES failure must be swallowed, not thrown.
    SearchResults results = service.search(user, "alpha", null, 20, null);

    assertThat(results.degraded()).isTrue();
    assertThat(results.contentSearchAvailable()).isFalse();
    assertThat(results.results()).hasSize(1);
    assertThat(results.results().get(0).bookmark()).isEqualTo(match);
    assertThat(results.results().get(0).snippet()).isNull();
    // Metadata fallback was consulted for the owner.
    verify(metadataSearchSource).search(eq(user.value()), eq("alpha"), any(), anyInt(), anyInt());
    // C8 metric: a degraded search increments markit.search{mode=degraded} and is timed.
    assertThat(meterRegistry.counter("markit.search", "mode", "degraded").count()).isEqualTo(1.0);
    assertThat(meterRegistry.counter("markit.search", "mode", "normal").count()).isZero();
    assertThat(meterRegistry.timer("markit.search.duration").count()).isEqualTo(1L);
  }

  @Test
  void should_IncrementZeroResultsCounter_When_ElasticsearchReturnsNoHits() {
    // Arrange: ES answers, but with an empty hit list.
    SearchHits<BookmarkDocument> empty = mock(SearchHits.class);
    when(empty.getSearchHits()).thenReturn(List.of());
    when(elasticsearch.search(any(NativeQuery.class), eq(BookmarkDocument.class))).thenReturn(empty);

    // Act
    service.search(user, "nothing", null, 20, null);

    // Assert
    assertThat(meterRegistry.counter("markit.search", "mode", "normal").count()).isEqualTo(1.0);
    assertThat(meterRegistry.counter("markit.search.zero_results").count()).isEqualTo(1.0);
  }

  @Test
  void should_MapHitsToResultsWithSnippetsAndScores_When_ElasticsearchAnswers() {
    SearchHit<BookmarkDocument> hit1 =
        hit(
            new BookmarkDocument(
                "bm-1", user.asString(), "cat-1", "https://a.test", "Alpha", "desc a", "aaa", "INDEXED", null),
            9.5f,
            Map.of("content", List.of("… the <em>kubernetes</em> operator …")));
    SearchHit<BookmarkDocument> hit2 =
        hit(
            new BookmarkDocument(
                "bm-2", user.asString(), "cat-1", "https://b.test", "Beta", "desc b", "bbb", "INDEXED", null),
            4.25f,
            Map.of("title", List.of("<em>Beta</em>")));

    @SuppressWarnings("unchecked")
    SearchHits<BookmarkDocument> hits = mock(SearchHits.class);
    when(hits.getSearchHits()).thenReturn(List.of(hit1, hit2));
    when(elasticsearch.search(any(NativeQuery.class), eq(BookmarkDocument.class))).thenReturn(hits);

    SearchResults results = service.search(user, "kubernetes", null, 20, null);

    assertThat(results.degraded()).isFalse();
    assertThat(results.contentSearchAvailable()).isTrue();
    assertThat(results.results()).hasSize(2);
    assertThat(results.results().get(0).bookmark().id()).isEqualTo("bm-1");
    assertThat(results.results().get(0).snippet()).contains("<em>kubernetes</em>");
    assertThat(results.results().get(0).score()).isEqualTo(9.5);
    // Falls back to the title highlight when content did not match.
    assertThat(results.results().get(1).snippet()).isEqualTo("<em>Beta</em>");
  }

  @Test
  void should_EnforceMandatoryUserIdFilter_OnEveryQuery() {
    NativeQuery query = service.buildQuery(user, "anything", null, 20, 0);

    Query esQuery = query.getQuery();
    assertThat(esQuery.isBool()).isTrue();
    boolean hasOwnerFilter =
        esQuery.bool().filter().stream()
            .filter(Query::isTerm)
            .anyMatch(
                f ->
                    SearchService.USER_ID_FIELD.equals(f.term().field())
                        && user.asString().equals(f.term().value().stringValue()));
    assertThat(hasOwnerFilter)
        .as("every search query must be scoped to the caller's userId (NFR-SEC-002)")
        .isTrue();
  }

  @Test
  void should_AddCategoryIdFilter_When_CategoryProvided() {
    var categoryId = UUID.randomUUID();
    NativeQuery query = service.buildQuery(user, "anything", categoryId, 20, 0);

    boolean hasCategoryFilter =
        query.getQuery().bool().filter().stream()
            .filter(Query::isTerm)
            .anyMatch(
                f ->
                    SearchService.CATEGORY_ID_FIELD.equals(f.term().field())
                        && categoryId.toString().equals(f.term().value().stringValue()));
    assertThat(hasCategoryFilter).isTrue();
  }

  // --- helpers ---

  private static SearchHit<BookmarkDocument> hit(
      BookmarkDocument doc, float score, Map<String, List<String>> highlights) {
    return new SearchHit<>(
        "bookmarks",
        doc.getId(),
        null,
        score,
        new Object[0],
        highlights,
        null,
        null,
        null,
        List.of(),
        doc);
  }
}
