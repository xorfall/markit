import { useState, type FormEvent } from 'react';
import type { Bookmark } from '../api/types';
import { ExternalIcon, PencilIcon, RefreshIcon, TrashIcon } from '../components/icons';
import { failureMessage } from './model';

interface BookmarkCardProps {
  bookmark: Bookmark;
  onEdit: (patch: { title?: string; description?: string }) => void;
  onDelete: () => void;
  onRescrape: () => void;
}

const STATE_LABEL: Record<Bookmark['state'], string> = {
  PENDING: 'Indexing',
  INDEXED: 'Indexed',
  FAILED: 'Failed',
};

/** A single bookmark card — carries the scrape underline and lifecycle states (A.3). */
export function BookmarkCard({
  bookmark,
  onEdit,
  onDelete,
  onRescrape,
}: BookmarkCardProps): JSX.Element {
  const [editing, setEditing] = useState(false);
  const [title, setTitle] = useState(bookmark.title);
  const [description, setDescription] = useState(bookmark.description ?? '');

  const isUrlTitle = bookmark.title === bookmark.url;
  const stateClass = `state-pill state-pill-${bookmark.state.toLowerCase()}`;

  const submit = (e: FormEvent) => {
    e.preventDefault();
    onEdit({ title: title.trim() || bookmark.url, description: description.trim() });
    setEditing(false);
  };

  if (editing) {
    return (
      <form className="card" onSubmit={submit}>
        <div className="field">
          <label className="field-label" htmlFor={`title-${bookmark.id}`}>
            Title
          </label>
          <input
            id={`title-${bookmark.id}`}
            className="input"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            autoFocus
          />
        </div>
        <div className="field" style={{ marginTop: 'var(--sp-3)' }}>
          <label className="field-label" htmlFor={`desc-${bookmark.id}`}>
            Notes
          </label>
          <textarea
            id={`desc-${bookmark.id}`}
            className="input"
            rows={3}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
        </div>
        <div className="modal-actions">
          <button type="button" className="btn btn-sm btn-ghost" onClick={() => setEditing(false)}>
            Cancel
          </button>
          <button type="submit" className="btn btn-sm btn-primary">
            Save changes
          </button>
        </div>
      </form>
    );
  }

  return (
    <article className="card">
      <h3 className={`card-title${isUrlTitle ? ' is-url' : ''}`}>{bookmark.title}</h3>
      {!isUrlTitle && <div className="card-url">{bookmark.url}</div>}

      {bookmark.description && <p className="card-desc">{bookmark.description}</p>}

      {bookmark.tags.length > 0 && (
        <div className="card-tags">
          {bookmark.tags.map((tag) => (
            <span key={tag} className="chip">
              {tag}
            </span>
          ))}
        </div>
      )}

      {bookmark.state === 'FAILED' && (
        <div className="card-failure">
          {failureMessage(bookmark)}
          <div className="card-failure-actions">
            <button className="btn btn-sm" onClick={onRescrape}>
              <RefreshIcon width={13} height={13} /> Re-scrape
            </button>
          </div>
        </div>
      )}

      <div className="card-foot">
        <span className={stateClass}>
          <span className="state-dot" />
          {STATE_LABEL[bookmark.state]}
        </span>
        <div className="card-actions">
          <a
            className="icon-btn"
            href={bookmark.url}
            target="_blank"
            rel="noreferrer"
            aria-label="Open link in a new tab"
          >
            <ExternalIcon width={14} height={14} />
          </a>
          <button className="icon-btn" onClick={() => setEditing(true)} aria-label="Edit bookmark">
            <PencilIcon width={14} height={14} />
          </button>
          <button
            className="icon-btn icon-btn-danger"
            onClick={onDelete}
            aria-label="Delete bookmark"
          >
            <TrashIcon width={14} height={14} />
          </button>
        </div>
      </div>

      {/* Signature scrape underline while PENDING; fades on INDEXED (B.2). */}
      {bookmark.state === 'PENDING' && <div className="scrape-underline" aria-hidden="true" />}
    </article>
  );
}
