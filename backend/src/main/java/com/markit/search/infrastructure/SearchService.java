package com.markit.search.infrastructure;

import com.markit.identity.domain.UserId;
import com.markit.search.application.MatchedBookmark;
import com.markit.search.application.SearchResults;
import com.markit.search.application.SearchResults.SearchResultItem;
import com.markit.search.application.port.MetadataSearchSource;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.stereotype.Service;

/**
 * Content-aware search (FR-SRC-001/002/003, api-contract §5). Runs a relevance-ranked, highlighted
 * {@code multi_match} over the Elasticsearch projection, then degrades gracefully to a Postgres
 * metadata search if ES is unavailable (ADR-0007) so a single downed dependency never 5xxs search.
 *
 * <p>Every query carries a MANDATORY {@code userId} term filter equal to the caller — bookmarks are
 * never searchable across users (NFR-SEC-002).
 */
@Service
public class SearchService {

  private static final Logger log = LoggerFactory.getLogger(SearchService.class);

  static final String USER_ID_FIELD = "userId";
  static final String CATEGORY_ID_FIELD = "categoryId";
  static final String TITLE_FIELD = "title";
  static final String DESCRIPTION_FIELD = "description";
  static final String CONTENT_FIELD = "content";
  private static final String PRE_TAG = "<em>";
  private static final String POST_TAG = "</em>";
  private static final List<String> SNIPPET_FIELDS =
      List.of(CONTENT_FIELD, TITLE_FIELD, DESCRIPTION_FIELD);

  private static final String MODE_NORMAL = "normal";
  private static final String MODE_DEGRADED = "degraded";

  private final ElasticsearchOperations elasticsearch;
  private final MetadataSearchSource metadataSearchSource;
  private final MeterRegistry meterRegistry;

  public SearchService(
      ElasticsearchOperations elasticsearch,
      MetadataSearchSource metadataSearchSource,
      MeterRegistry meterRegistry) {
    this.elasticsearch = elasticsearch;
    this.metadataSearchSource = metadataSearchSource;
    this.meterRegistry = meterRegistry;
  }

  /**
   * Search the owner's bookmarks. Returns full content-aware results from ES, or — if ES throws —
   * degraded metadata-only results from Postgres (never propagates the ES failure).
   */
  public SearchResults search(
      UserId userId, String query, UUID categoryId, int limit, String cursor) {
    int size = normalizeLimit(limit);
    int offset = decodeCursor(cursor);
    // C8 RED: time every search (NFR-PERF-001 p95<300ms); tag its mode and flag empty result sets.
    Timer.Sample sample = Timer.start(meterRegistry);
    String mode = MODE_NORMAL;
    SearchResults results;
    try {
      try {
        results = searchElasticsearch(userId, query, categoryId, size, offset);
      } catch (RuntimeException ex) {
        // ES down / connection failure: keep search useful via cheap Postgres metadata search
        // (ADR-0007). The failure is swallowed here — it must never surface to the client as a 5xx.
        log.warn("Elasticsearch search failed; degrading to Postgres metadata search", ex);
        mode = MODE_DEGRADED;
        results = searchDegraded(userId, query, categoryId, size, offset);
      }
    } finally {
      sample.stop(meterRegistry.timer("markit.search.duration"));
    }
    meterRegistry.counter("markit.search", "mode", mode).increment();
    if (results.results().isEmpty()) {
      meterRegistry.counter("markit.search.zero_results").increment();
    }
    return results;
  }

  private SearchResults searchElasticsearch(
      UserId userId, String query, UUID categoryId, int size, int offset) {
    NativeQuery nativeQuery = buildQuery(userId, query, categoryId, size, offset);
    SearchHits<BookmarkDocument> hits = elasticsearch.search(nativeQuery, BookmarkDocument.class);

    List<SearchResultItem> results = new ArrayList<>();
    for (SearchHit<BookmarkDocument> hit : hits.getSearchHits()) {
      results.add(
          new SearchResultItem(toMatchedBookmark(hit.getContent()), snippet(hit), hit.getScore()));
    }
    return new SearchResults(false, true, results, nextCursor(results.size(), size, offset));
  }

  private SearchResults searchDegraded(
      UserId userId, String query, UUID categoryId, int size, int offset) {
    List<MatchedBookmark> matches =
        metadataSearchSource.search(userId.value(), query, categoryId, size, offset);
    List<SearchResultItem> results = new ArrayList<>();
    for (MatchedBookmark match : matches) {
      // No content snippet in degraded mode (metadata-only, no highlighting).
      results.add(new SearchResultItem(match, null, 0.0));
    }
    return new SearchResults(true, false, results, nextCursor(results.size(), size, offset));
  }

  /**
   * Build the owner-scoped {@link NativeQuery}. A MANDATORY {@code userId} term filter is applied on
   * every query (NFR-SEC-002); the relevance {@code multi_match} spans title/description/content,
   * with an optional {@code categoryId} filter. Package-visible so the isolation guarantee can be
   * asserted in isolation.
   */
  NativeQuery buildQuery(UserId userId, String query, UUID categoryId, int size, int offset) {
    String terms = query == null ? "" : query.trim();

    // Partial (prefix) matching once there are at least 3 characters — e.g. "rel" matches
    // "reliability". Shorter queries (1-2 chars) match whole terms only — e.g. "go" matches
    // only the token "go", not "golang".
    final co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType matchType =
        terms.length() >= 3
            ? co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.BoolPrefix
            : co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.BestFields;

    var boolQuery =
        co.elastic.clients.elasticsearch._types.query_dsl.Query.of(
            q ->
                q.bool(
                    b -> {
                      if (terms.isBlank()) {
                        b.must(m -> m.matchAll(ma -> ma));
                      } else {
                        b.must(
                            m ->
                                m.multiMatch(
                                    mm ->
                                        mm.query(terms)
                                            .type(matchType)
                                            .fields(
                                                TITLE_FIELD, DESCRIPTION_FIELD, CONTENT_FIELD)));
                      }
                      // MANDATORY owner isolation — every query is scoped to the caller.
                      b.filter(
                          f -> f.term(t -> t.field(USER_ID_FIELD).value(userId.asString())));
                      if (categoryId != null) {
                        b.filter(
                            f ->
                                f.term(
                                    t ->
                                        t.field(CATEGORY_ID_FIELD).value(categoryId.toString())));
                      }
                      return b;
                    }));

    return new NativeQueryBuilder()
        .withQuery(boolQuery)
        .withHighlightQuery(highlightQuery())
        .withPageable(PageRequest.of(offset / size, size))
        .withTrackTotalHits(true)
        .build();
  }

  private HighlightQuery highlightQuery() {
    var parameters = HighlightParameters.builder().withPreTags(PRE_TAG).withPostTags(POST_TAG).build();
    List<HighlightField> fields =
        SNIPPET_FIELDS.stream().map(HighlightField::new).toList();
    return new HighlightQuery(new Highlight(parameters, fields), BookmarkDocument.class);
  }

  private static String snippet(SearchHit<BookmarkDocument> hit) {
    for (String field : SNIPPET_FIELDS) {
      List<String> fragments = hit.getHighlightField(field);
      if (fragments != null && !fragments.isEmpty()) {
        return String.join(" … ", fragments);
      }
    }
    return null;
  }

  private static MatchedBookmark toMatchedBookmark(BookmarkDocument doc) {
    return new MatchedBookmark(
        doc.getId(),
        doc.getCategoryId(),
        doc.getUrl(),
        doc.getTitle(),
        doc.getDescription(),
        doc.getState());
  }

  private static int normalizeLimit(int limit) {
    if (limit < 1) {
      return 20;
    }
    return Math.min(limit, 100);
  }

  /** A next cursor exists only when the page came back full (there may be more). */
  private static String nextCursor(int returned, int size, int offset) {
    if (returned < size) {
      return null;
    }
    return encodeCursor(offset + size);
  }

  private static String encodeCursor(int offset) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(Integer.toString(offset).getBytes(StandardCharsets.UTF_8));
  }

  private static int decodeCursor(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return 0;
    }
    try {
      String decoded =
          new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      int offset = Integer.parseInt(decoded);
      return Math.max(offset, 0);
    } catch (IllegalArgumentException ignored) {
      // Opaque, client-supplied token — an unparseable cursor just starts from the beginning.
      return 0;
    }
  }
}
