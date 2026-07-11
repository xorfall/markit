import { useState } from 'react';
import type { Collection } from '../api/types';
import { PencilIcon, TrashIcon } from '../components/icons';
import { InlineAddForm } from './InlineAddForm';

interface CollectionsRailProps {
  collections: Collection[];
  activeId: string | null;
  onSelect: (id: string) => void;
  onCreate: (name: string) => void;
  onRename: (id: string, name: string) => void;
  onDelete: (id: string) => void;
  onReorder: (orderedIds: string[]) => void;
}

/** Left rail: select, create, rename, delete and reorder collections (A.2). */
export function CollectionsRail({
  collections,
  activeId,
  onSelect,
  onCreate,
  onRename,
  onDelete,
  onReorder,
}: CollectionsRailProps): JSX.Element {
  const [renamingId, setRenamingId] = useState<string | null>(null);
  const [draft, setDraft] = useState('');

  const move = (index: number, delta: number) => {
    const target = index + delta;
    if (target < 0 || target >= collections.length) return;
    const ids = collections.map((c) => c.id);
    [ids[index], ids[target]] = [ids[target], ids[index]];
    onReorder(ids);
  };

  const commitRename = (id: string) => {
    const trimmed = draft.trim();
    if (trimmed) onRename(id, trimmed);
    setRenamingId(null);
  };

  return (
    <nav className="rail" aria-label="Collections">
      <div className="rail-title">Collections</div>
      <ul className="rail-list">
        {collections.map((collection, index) => (
          <li key={collection.id}>
            {renamingId === collection.id ? (
              <input
                className="input btn-sm"
                value={draft}
                autoFocus
                onChange={(e) => setDraft(e.target.value)}
                onBlur={() => commitRename(collection.id)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') commitRename(collection.id);
                  if (e.key === 'Escape') setRenamingId(null);
                }}
              />
            ) : (
              <div
                className={`rail-item${collection.id === activeId ? ' rail-item-active' : ''}`}
                role="button"
                tabIndex={0}
                onClick={() => onSelect(collection.id)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    onSelect(collection.id);
                  }
                }}
              >
                <span className="rail-marker" aria-hidden="true" />
                <span className="rail-item-name">{collection.name}</span>
                <span className="rail-reorder">
                  <button
                    aria-label={`Move ${collection.name} up`}
                    disabled={index === 0}
                    onClick={(e) => {
                      e.stopPropagation();
                      move(index, -1);
                    }}
                  >
                    ▲
                  </button>
                  <button
                    aria-label={`Move ${collection.name} down`}
                    disabled={index === collections.length - 1}
                    onClick={(e) => {
                      e.stopPropagation();
                      move(index, 1);
                    }}
                  >
                    ▼
                  </button>
                </span>
                <button
                  className="icon-btn"
                  aria-label={`Rename ${collection.name}`}
                  onClick={(e) => {
                    e.stopPropagation();
                    setRenamingId(collection.id);
                    setDraft(collection.name);
                  }}
                >
                  <PencilIcon width={13} height={13} />
                </button>
                <button
                  className="icon-btn icon-btn-danger"
                  aria-label={`Delete ${collection.name}`}
                  onClick={(e) => {
                    e.stopPropagation();
                    onDelete(collection.id);
                  }}
                >
                  <TrashIcon width={13} height={13} />
                </button>
              </div>
            )}
          </li>
        ))}
      </ul>

      <div className="rail-add">
        <InlineAddForm
          triggerLabel="New collection"
          placeholder="Collection name"
          submitLabel="Create"
          onSubmit={onCreate}
        />
      </div>
    </nav>
  );
}
