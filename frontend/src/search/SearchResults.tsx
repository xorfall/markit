import { useMemo } from 'react';
import type { SearchResultItem } from '../api/types';
import { useSearch } from './useSearch';
import { renderSnippet } from './snippet';

interface SearchResultsProps {
  query: string;
}

/** The search surface that replaces the board while a query is active (A.5). */
export function SearchResults({ query }: SearchResultsProps): JSX.Element {
  const { data, isLoading, isError, fetchNextPage, hasNextPage, isFetchingNextPage } =
    useSearch(query);

  const items: SearchResultItem[] = useMemo(
    () => data?.pages.flatMap((page) => page.results) ?? [],
    [data],
  );
  const first = data?.pages[0];
  const degraded = first ? !first.contentSearchAvailable : false;
  const total = items.length;

  return (
    <div className="results">
      <div className="results-inner">
        <div className="results-head">
          <h1 className="results-query">{query}</h1>
          {!isLoading && !isError && (
            <span className="results-count">
              {total} {total === 1 ? 'hit' : 'hits'}
              {hasNextPage ? '+' : ''}
            </span>
          )}
        </div>

        {degraded && (
          <div className="banner" role="status" style={{ marginBottom: 'var(--sp-4)' }}>
            Content search is paused — searching titles, notes and tags.
          </div>
        )}

        {isLoading && (
          <div className="center-fill">
            <div className="spinner" role="status" aria-label="Searching" />
          </div>
        )}

        {isError && (
          <div className="results-empty">
            <p>Search is unavailable right now. Try again in a moment.</p>
          </div>
        )}

        {!isLoading && !isError && total === 0 && (
          <div className="results-empty">
            <h2>No matches</h2>
            <p>Nothing found for “{query}”. Try another word or a tag.</p>
          </div>
        )}

        {items.map(({ bookmark, snippet }) => (
          <button
            key={bookmark.id}
            className="result-item"
            onClick={() => window.open(bookmark.url, '_blank', 'noreferrer')}
          >
            <div className="result-line">
              <span className="result-title">{bookmark.title}</span>
              <span className="result-url">{bookmark.url}</span>
            </div>
            {snippet && <p className="result-snippet">{renderSnippet(snippet)}</p>}
          </button>
        ))}

        {hasNextPage && (
          <button
            className="btn load-more"
            onClick={() => void fetchNextPage()}
            disabled={isFetchingNextPage}
          >
            {isFetchingNextPage ? 'Loading…' : 'Load more'}
          </button>
        )}
      </div>
    </div>
  );
}
