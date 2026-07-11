import { request } from './http';
import type { SearchResponse } from './types';

export interface SearchParams {
  q: string;
  collectionId?: string;
  categoryId?: string;
  limit?: number;
  cursor?: string;
}

/** Search endpoint (api-contract §5). */
export const searchApi = {
  search(params: SearchParams, signal?: AbortSignal): Promise<SearchResponse> {
    const query = new URLSearchParams();
    query.set('q', params.q);
    if (params.collectionId) query.set('collectionId', params.collectionId);
    if (params.categoryId) query.set('categoryId', params.categoryId);
    query.set('limit', String(params.limit ?? 20));
    if (params.cursor) query.set('cursor', params.cursor);

    const opts = signal ? { signal } : {};
    return request<SearchResponse>(`/search?${query.toString()}`, opts);
  },
};
