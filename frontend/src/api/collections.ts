import { request } from './http';
import { IDEMPOTENCY_HEADER, newIdempotencyKey } from './idempotency';
import type { Category, Collection } from './types';

/** Collections & categories endpoints (api-contract §3). */
export const collectionsApi = {
  list(): Promise<Collection[]> {
    return request<Collection[]>('/collections?expand=categories');
  },

  create(name: string, idempotencyKey = newIdempotencyKey()): Promise<Collection> {
    return request<Collection>('/collections', {
      method: 'POST',
      body: { name },
      headers: { [IDEMPOTENCY_HEADER]: idempotencyKey },
    });
  },

  rename(id: string, name: string): Promise<Collection> {
    return request<Collection>(`/collections/${id}`, {
      method: 'PATCH',
      body: { name },
    });
  },

  remove(id: string): Promise<void> {
    return request<void>(`/collections/${id}`, { method: 'DELETE' });
  },

  reorder(orderedIds: string[]): Promise<void> {
    return request<void>('/collections/reorder', {
      method: 'PUT',
      body: { orderedIds },
    });
  },

  listCategories(collectionId: string): Promise<Category[]> {
    return request<Category[]>(`/collections/${collectionId}/categories`);
  },

  createCategory(
    collectionId: string,
    name: string,
    idempotencyKey = newIdempotencyKey(),
  ): Promise<Category> {
    return request<Category>(`/collections/${collectionId}/categories`, {
      method: 'POST',
      body: { name },
      headers: { [IDEMPOTENCY_HEADER]: idempotencyKey },
    });
  },

  renameCategory(categoryId: string, name: string): Promise<Category> {
    return request<Category>(`/categories/${categoryId}`, {
      method: 'PATCH',
      body: { name },
    });
  },

  moveCategory(categoryId: string, collectionId: string): Promise<Category> {
    return request<Category>(`/categories/${categoryId}`, {
      method: 'PATCH',
      body: { collectionId },
    });
  },

  removeCategory(categoryId: string): Promise<void> {
    return request<void>(`/categories/${categoryId}`, { method: 'DELETE' });
  },

  reorderCategories(collectionId: string, orderedIds: string[]): Promise<void> {
    return request<void>(`/collections/${collectionId}/categories/reorder`, {
      method: 'PUT',
      body: { orderedIds },
    });
  },
};
