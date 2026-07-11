import { useEffect, useState } from 'react';
import { InlineAddForm } from './InlineAddForm';
import { CategoryColumn } from './CategoryColumn';
import { CollectionsRail } from './CollectionsRail';
import { useCollectionMutations, useCollectionsQuery } from './useCollections';

/** The board: collections rail + category-column board (booky.io-like layout). */
export function Board(): JSX.Element {
  const { data: collections = [], isLoading, isError } = useCollectionsQuery();
  const mutations = useCollectionMutations();
  const [activeId, setActiveId] = useState<string | null>(null);

  // Default the active collection to the first one once data arrives.
  useEffect(() => {
    if (activeId && collections.some((c) => c.id === activeId)) return;
    setActiveId(collections[0]?.id ?? null);
  }, [collections, activeId]);

  const active = collections.find((c) => c.id === activeId) ?? null;

  if (isLoading) {
    return (
      <div className="center-fill">
        <div className="spinner" role="status" aria-label="Loading your board" />
      </div>
    );
  }

  if (isError) {
    return (
      <div className="center-fill">
        <div className="board-message">
          <p>We could not load your board. Check your connection and try again.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="workspace">
      <CollectionsRail
        collections={collections}
        activeId={activeId}
        onSelect={setActiveId}
        onCreate={(name) => mutations.createCollection.mutate(name)}
        onRename={(id, name) => mutations.renameCollection.mutate({ id, name })}
        onDelete={(id) => mutations.deleteCollection.mutate(id)}
        onReorder={(orderedIds) => mutations.reorderCollections.mutate(orderedIds)}
      />

      {collections.length === 0 ? (
        <div className="board">
          <div className="board-empty">
            <h2>Start with a collection</h2>
            <p>
              Group your links into collections and categories, then save pages and search inside
              them.
            </p>
            <div style={{ marginTop: 'var(--sp-4)', display: 'inline-block' }}>
              <InlineAddForm
                triggerLabel="New collection"
                placeholder="Collection name"
                submitLabel="Create"
                onSubmit={(name) => mutations.createCollection.mutate(name)}
              />
            </div>
          </div>
        </div>
      ) : (
        <div className="board">
          {active?.categories?.map((category) => (
            <CategoryColumn
              key={category.id}
              category={category}
              onRename={(name) => mutations.renameCategory.mutate({ id: category.id, name })}
              onDelete={() => mutations.deleteCategory.mutate(category.id)}
            />
          ))}
          {active && (
            <div className="column">
              <InlineAddForm
                triggerLabel="Add category"
                placeholder="Category name"
                submitLabel="Add"
                onSubmit={(name) =>
                  mutations.createCategory.mutate({ collectionId: active.id, name })
                }
              />
            </div>
          )}
        </div>
      )}
    </div>
  );
}
