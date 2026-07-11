import { request } from './http';
import { IDEMPOTENCY_HEADER, newIdempotencyKey } from './idempotency';
import type { Bookmark } from './types';

export interface BookmarkPatch {
  title?: string;
  description?: string;
  categoryId?: string;
}

/** Bookmarks endpoints (api-contract §4). */
export const bookmarksApi = {
  listByCategory(categoryId: string): Promise<Bookmark[]> {
    return request<Bookmark[]>(`/categories/${categoryId}/bookmarks`);
  },

  create(
    categoryId: string,
    url: string,
    idempotencyKey = newIdempotencyKey(),
  ): Promise<Bookmark> {
    return request<Bookmark>(`/categories/${categoryId}/bookmarks`, {
      method: 'POST',
      body: { url },
      headers: { [IDEMPOTENCY_HEADER]: idempotencyKey },
    });
  },

  get(id: string): Promise<Bookmark> {
    return request<Bookmark>(`/bookmarks/${id}`);
  },

  patch(id: string, patch: BookmarkPatch): Promise<Bookmark> {
    return request<Bookmark>(`/bookmarks/${id}`, {
      method: 'PATCH',
      body: patch,
    });
  },

  remove(id: string): Promise<void> {
    return request<void>(`/bookmarks/${id}`, { method: 'DELETE' });
  },

  reorder(categoryId: string, orderedIds: string[]): Promise<void> {
    return request<void>(`/categories/${categoryId}/bookmarks/reorder`, {
      method: 'PUT',
      body: { orderedIds },
    });
  },

  rescrape(id: string): Promise<void> {
    return request<void>(`/bookmarks/${id}/rescrape`, { method: 'POST' });
  },

  addTag(id: string, name: string): Promise<Bookmark> {
    return request<Bookmark>(`/bookmarks/${id}/tags`, {
      method: 'POST',
      body: { name },
    });
  },

  removeTag(id: string, tagName: string): Promise<Bookmark> {
    return request<Bookmark>(`/bookmarks/${id}/tags/${encodeURIComponent(tagName)}`, {
      method: 'DELETE',
    });
  },
};
