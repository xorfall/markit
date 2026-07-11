/** Centralised TanStack Query keys so invalidation stays consistent. */
export const queryKeys = {
  me: ['me'] as const,
  collections: ['collections'] as const,
  bookmarks: (categoryId: string) => ['bookmarks', categoryId] as const,
  bookmark: (id: string) => ['bookmark', id] as const,
  search: (q: string, scope: string | undefined) => ['search', q, scope ?? 'all'] as const,
};
