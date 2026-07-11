import { useInfiniteQuery } from '@tanstack/react-query';
import { searchApi } from '../api/search';
import type { SearchResponse } from '../api/types';
import { queryKeys } from '../lib/queryKeys';

/**
 * Cursor-paginated search (api-contract §5). Disabled until the query is
 * non-empty. Degradation flags travel on every page and are surfaced by the UI.
 */
export function useSearch(query: string, scope?: { collectionId?: string; categoryId?: string }) {
  const trimmed = query.trim();
  const scopeKey = scope?.categoryId ?? scope?.collectionId;

  return useInfiniteQuery<SearchResponse>({
    queryKey: queryKeys.search(trimmed, scopeKey),
    enabled: trimmed.length > 0,
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam, signal }) =>
      searchApi.search(
        {
          q: trimmed,
          ...(scope?.collectionId ? { collectionId: scope.collectionId } : {}),
          ...(scope?.categoryId ? { categoryId: scope.categoryId } : {}),
          ...(typeof pageParam === 'string' ? { cursor: pageParam } : {}),
        },
        signal,
      ),
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    staleTime: 10_000,
  });
}
