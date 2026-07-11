import { useEffect } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { bookmarksApi, type BookmarkPatch } from '../api/bookmarks';
import type { Bookmark } from '../api/types';
import { queryKeys } from '../lib/queryKeys';
import { optimisticBookmark } from './model';

type List = Bookmark[];

/** Bookmarks for one category (api-contract §4). */
export function useBookmarksQuery(categoryId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.bookmarks(categoryId),
    queryFn: () => bookmarksApi.listByCategory(categoryId),
    enabled: enabled && !categoryId.startsWith('temp:'),
  });
}

/**
 * Bookmark mutations with per-card optimistic behavior + rollback (A.1/A.2).
 * `categoryId` scopes the cache the hook manages.
 */
export function useBookmarkMutations(categoryId: string) {
  const qc = useQueryClient();
  const key = queryKeys.bookmarks(categoryId);

  const setList = (updater: (prev: List) => List) =>
    qc.setQueryData<List>(key, (prev) => (prev ? updater(prev) : prev));
  const snapshot = () => qc.getQueryData<List>(key);
  const cancel = () => qc.cancelQueries({ queryKey: key });
  const invalidate = () => void qc.invalidateQueries({ queryKey: key });

  const create = useMutation({
    mutationFn: (url: string) => bookmarksApi.create(categoryId, url),
    onMutate: async (url) => {
      await cancel();
      const previous = snapshot();
      const optimistic = optimisticBookmark(categoryId, url);
      setList((prev) => [...prev, optimistic]);
      return { previous };
    },
    // On success, swap the optimistic card for the server bookmark (temp→real id).
    onSuccess: (created, _url, ctx) => {
      const prev = ctx?.previous ?? [];
      setList(() => [...prev, created]);
    },
    onError: (_e, _url, ctx) => {
      qc.setQueryData(key, ctx?.previous);
    },
    onSettled: invalidate,
  });

  const edit = useMutation({
    mutationFn: ({ id, patch }: { id: string; patch: BookmarkPatch }) =>
      bookmarksApi.patch(id, patch),
    onMutate: async ({ id, patch }) => {
      await cancel();
      const previous = snapshot();
      setList((prev) => prev.map((b) => (b.id === id ? { ...b, ...patch } : b)));
      return { previous };
    },
    onError: (_e, _v, ctx) => {
      qc.setQueryData(key, ctx?.previous);
    },
    onSettled: invalidate,
  });

  const remove = useMutation({
    mutationFn: (id: string) => bookmarksApi.remove(id),
    onMutate: async (id) => {
      await cancel();
      const previous = snapshot();
      setList((prev) => prev.filter((b) => b.id !== id));
      return { previous };
    },
    onError: (_e, _id, ctx) => {
      qc.setQueryData(key, ctx?.previous);
    },
    onSettled: invalidate,
  });

  const rescrape = useMutation({
    mutationFn: (id: string) => bookmarksApi.rescrape(id),
    onMutate: async (id) => {
      await cancel();
      const previous = snapshot();
      // Optimistically flip back to PENDING so the scrape underline reappears (A.3).
      setList((prev) =>
        prev.map((b) =>
          b.id === id ? { ...b, state: 'PENDING', failureReason: null } : b,
        ),
      );
      return { previous };
    },
    onError: (_e, _id, ctx) => {
      qc.setQueryData(key, ctx?.previous);
    },
    onSettled: invalidate,
  });

  return { create, edit, remove, rescrape };
}

/**
 * SSE polling fallback (client-contract A.4): while a bookmark is PENDING the
 * client polls GET /bookmarks/{id} on a short interval until it resolves to
 * INDEXED or FAILED. The backend SSE stream is a later follow-up; polling keeps
 * PENDING → INDEXED resolving (degraded, not broken).
 */
export function useBookmarkPolling(pending: Array<{ id: string; categoryId: string }>): void {
  const qc = useQueryClient();
  // Serialise the dependency so the effect only restarts when the set changes.
  const signature = pending
    .map((p) => `${p.categoryId}:${p.id}`)
    .sort()
    .join('|');

  useEffect(() => {
    const items = signature ? signature.split('|').map((s) => {
      const [categoryId, id] = s.split(':');
      return { categoryId, id };
    }) : [];
    if (items.length === 0) return;

    let cancelled = false;
    const POLL_MS = 3000;

    const tick = async () => {
      await Promise.all(
        items.map(async ({ id, categoryId }) => {
          if (id.startsWith('temp:')) return;
          try {
            const fresh = await bookmarksApi.get(id);
            if (cancelled) return;
            qc.setQueryData<List>(queryKeys.bookmarks(categoryId), (prev) =>
              prev ? prev.map((b) => (b.id === id ? fresh : b)) : prev,
            );
          } catch {
            /* transient — retry on the next tick */
          }
        }),
      );
    };

    const handle = window.setInterval(() => void tick(), POLL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(handle);
    };
  }, [signature, qc]);
}
