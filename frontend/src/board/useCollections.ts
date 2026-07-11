import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { collectionsApi } from '../api/collections';
import type { Category, Collection } from '../api/types';
import { queryKeys } from '../lib/queryKeys';
import { tempId } from './model';

type Collections = Collection[];

/** Load the user's collections with their nested categories (api-contract §3). */
export function useCollectionsQuery() {
  return useQuery({
    queryKey: queryKeys.collections,
    queryFn: () => collectionsApi.list(),
  });
}

/** Read/update helpers over the collections cache used by every mutation below. */
function useCollectionsCache() {
  const qc = useQueryClient();
  return {
    snapshot: () => qc.getQueryData<Collections>(queryKeys.collections),
    set: (updater: (prev: Collections) => Collections) =>
      qc.setQueryData<Collections>(queryKeys.collections, (prev) =>
        prev ? updater(prev) : prev,
      ),
    restore: (prev: Collections | undefined) =>
      qc.setQueryData(queryKeys.collections, prev),
    invalidate: () => qc.invalidateQueries({ queryKey: queryKeys.collections }),
    cancel: () => qc.cancelQueries({ queryKey: queryKeys.collections }),
  };
}

interface OptimisticCtx {
  previous: Collections | undefined;
}

/**
 * All collection & category mutations, each applied optimistically and rolled
 * back on failure (client-contract A.1). Reconciliation happens by invalidating
 * the collections query on settle so server ids replace temp ids.
 */
export function useCollectionMutations() {
  const cache = useCollectionsCache();

  const beginOptimistic = async (mutate: (prev: Collections) => Collections) => {
    await cache.cancel();
    const previous = cache.snapshot();
    cache.set(mutate);
    return { previous } satisfies OptimisticCtx;
  };
  const rollback = (_err: unknown, _vars: unknown, ctx: OptimisticCtx | undefined) => {
    cache.restore(ctx?.previous);
  };
  const settle = () => {
    void cache.invalidate();
  };

  const createCollection = useMutation({
    mutationFn: (name: string) => collectionsApi.create(name),
    onMutate: (name) => {
      const position = (cache.snapshot()?.length ?? 0);
      const optimistic: Collection = { id: tempId(), name, position, categories: [] };
      return beginOptimistic((prev) => [...prev, optimistic]);
    },
    onError: rollback,
    onSettled: settle,
  });

  const renameCollection = useMutation({
    mutationFn: ({ id, name }: { id: string; name: string }) =>
      collectionsApi.rename(id, name),
    onMutate: ({ id, name }) =>
      beginOptimistic((prev) => prev.map((c) => (c.id === id ? { ...c, name } : c))),
    onError: rollback,
    onSettled: settle,
  });

  const deleteCollection = useMutation({
    mutationFn: (id: string) => collectionsApi.remove(id),
    onMutate: (id) => beginOptimistic((prev) => prev.filter((c) => c.id !== id)),
    onError: rollback,
    onSettled: settle,
  });

  const reorderCollections = useMutation({
    mutationFn: (orderedIds: string[]) => collectionsApi.reorder(orderedIds),
    onMutate: (orderedIds) =>
      beginOptimistic((prev) => {
        const byId = new Map(prev.map((c) => [c.id, c]));
        return orderedIds
          .map((id, index) => {
            const c = byId.get(id);
            return c ? { ...c, position: index } : undefined;
          })
          .filter((c): c is Collection => c !== undefined);
      }),
    onError: rollback,
    onSettled: settle,
  });

  const createCategory = useMutation({
    mutationFn: ({ collectionId, name }: { collectionId: string; name: string }) =>
      collectionsApi.createCategory(collectionId, name),
    onMutate: ({ collectionId, name }) =>
      beginOptimistic((prev) =>
        prev.map((c) =>
          c.id === collectionId
            ? {
                ...c,
                categories: [
                  ...(c.categories ?? []),
                  {
                    id: tempId(),
                    collectionId,
                    name,
                    position: c.categories?.length ?? 0,
                  } satisfies Category,
                ],
              }
            : c,
        ),
      ),
    onError: rollback,
    onSettled: settle,
  });

  const renameCategory = useMutation({
    mutationFn: ({ id, name }: { id: string; name: string }) =>
      collectionsApi.renameCategory(id, name),
    onMutate: ({ id, name }) =>
      beginOptimistic((prev) =>
        prev.map((c) => ({
          ...c,
          categories: (c.categories ?? []).map((cat) =>
            cat.id === id ? { ...cat, name } : cat,
          ),
        })),
      ),
    onError: rollback,
    onSettled: settle,
  });

  const deleteCategory = useMutation({
    mutationFn: (id: string) => collectionsApi.removeCategory(id),
    onMutate: (id) =>
      beginOptimistic((prev) =>
        prev.map((c) => ({
          ...c,
          categories: (c.categories ?? []).filter((cat) => cat.id !== id),
        })),
      ),
    onError: rollback,
    onSettled: settle,
  });

  return {
    createCollection,
    renameCollection,
    deleteCollection,
    reorderCollections,
    createCategory,
    renameCategory,
    deleteCategory,
  };
}
