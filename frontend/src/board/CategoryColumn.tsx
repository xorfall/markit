import { useMemo, useState } from 'react';
import type { Category } from '../api/types';
import { PlusIcon, TrashIcon } from '../components/icons';
import { AddBookmarkModal } from './AddBookmarkModal';
import { BookmarkCard } from './BookmarkCard';
import { useBookmarkMutations, useBookmarkPolling, useBookmarksQuery } from './useBookmarks';

interface CategoryColumnProps {
  category: Category;
  onRename: (name: string) => void;
  onDelete: () => void;
}

/** One category column: its bookmark cards, add form, and PENDING polling. */
export function CategoryColumn({ category, onRename, onDelete }: CategoryColumnProps): JSX.Element {
  const { data: bookmarks = [], isLoading } = useBookmarksQuery(category.id);
  const { edit, remove, rescrape } = useBookmarkMutations(category.id);
  const [name, setName] = useState(category.name);
  const [adding, setAdding] = useState(false);

  const pending = useMemo(
    () =>
      bookmarks
        .filter((b) => b.state === 'PENDING' && !b.id.startsWith('temp:'))
        .map((b) => ({ id: b.id, categoryId: category.id })),
    [bookmarks, category.id],
  );
  useBookmarkPolling(pending);

  const commitRename = () => {
    const trimmed = name.trim();
    if (trimmed && trimmed !== category.name) onRename(trimmed);
    else setName(category.name);
  };

  return (
    <section className="column">
      <header className="column-head">
        <input
          className="column-title"
          value={name}
          aria-label={`Category name: ${category.name}`}
          onChange={(e) => setName(e.target.value)}
          onBlur={commitRename}
          onKeyDown={(e) => {
            if (e.key === 'Enter') e.currentTarget.blur();
            if (e.key === 'Escape') {
              setName(category.name);
              e.currentTarget.blur();
            }
          }}
        />
        <span className="column-count mono">{bookmarks.length}</span>
        <button
          className="icon-btn icon-btn-danger"
          onClick={onDelete}
          aria-label={`Delete category ${category.name}`}
        >
          <TrashIcon width={14} height={14} />
        </button>
      </header>

      <div className="column-body">
        {isLoading ? (
          <div className="spinner" role="status" aria-label="Loading bookmarks" />
        ) : (
          bookmarks.map((bookmark) => (
            <BookmarkCard
              key={bookmark.id}
              bookmark={bookmark}
              onEdit={(patch) => edit.mutate({ id: bookmark.id, patch })}
              onDelete={() => remove.mutate(bookmark.id)}
              onRescrape={() => rescrape.mutate(bookmark.id)}
            />
          ))
        )}
        <div className="column-add">
          <button className="btn btn-sm btn-ghost" onClick={() => setAdding(true)}>
            <PlusIcon width={14} height={14} /> Add bookmark
          </button>
        </div>
      </div>

      {adding && (
        <AddBookmarkModal categoryId={category.id} onClose={() => setAdding(false)} />
      )}
    </section>
  );
}
