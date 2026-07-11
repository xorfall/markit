import { useMemo, useState } from 'react';
import type { Category } from '../api/types';
import { TrashIcon } from '../components/icons';
import { BookmarkCard } from './BookmarkCard';
import { InlineAddForm } from './InlineAddForm';
import { useBookmarkMutations, useBookmarkPolling, useBookmarksQuery } from './useBookmarks';

interface CategoryColumnProps {
  category: Category;
  onRename: (name: string) => void;
  onDelete: () => void;
}

/** One category column: its bookmark cards, add form, and PENDING polling. */
export function CategoryColumn({ category, onRename, onDelete }: CategoryColumnProps): JSX.Element {
  const { data: bookmarks = [], isLoading } = useBookmarksQuery(category.id);
  const { create, edit, remove, rescrape } = useBookmarkMutations(category.id);
  const [name, setName] = useState(category.name);

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
          <InlineAddForm
            triggerLabel="Add bookmark"
            placeholder="https://…"
            submitLabel="Add"
            mono
            onSubmit={(url) => create.mutate(url)}
          />
        </div>
      </div>
    </section>
  );
}
