import { useEffect, useState, type FormEvent } from 'react';
import { ApiError, messageForCode } from '../api/errors';
import type { Bookmark } from '../api/types';
import { Modal } from '../components/Modal';
import { RefreshIcon } from '../components/icons';
import { failureMessage } from './model';
import { useBookmarkMutations, useBookmarksQuery } from './useBookmarks';

const STATE_LABEL: Record<Bookmark['state'], string> = {
  PENDING: 'Indexing…',
  INDEXED: 'Indexed',
  FAILED: "Couldn't index",
};

interface Props {
  categoryId: string;
  onClose: () => void;
}

/**
 * Add-bookmark dialog. Step 1 pastes a URL; step 2 shows the created bookmark live —
 * the title auto-fills from the scrape, notes are editable, and a pill tracks the
 * PENDING → INDEXED/FAILED state (updated by the column's polling).
 */
export function AddBookmarkModal({ categoryId, onClose }: Props): JSX.Element {
  const { create, edit, rescrape } = useBookmarkMutations(categoryId);
  const { data: bookmarks = [] } = useBookmarksQuery(categoryId);

  const [url, setUrl] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [createdId, setCreatedId] = useState<string | null>(null);
  const created = createdId ? bookmarks.find((b) => b.id === createdId) ?? null : null;

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [dirty, setDirty] = useState(false);
  const [copied, setCopied] = useState(false);

  // Mirror the server's auto-filled title/notes until the user starts editing.
  useEffect(() => {
    if (created && !dirty) {
      setTitle(created.title && created.title !== created.url ? created.title : '');
      setDescription(created.description ?? '');
    }
  }, [created?.title, created?.description, created?.url, dirty, created]);

  const onAdd = (e: FormEvent) => {
    e.preventDefault();
    const trimmed = url.trim();
    if (!trimmed) return;
    setError(null);
    create.mutate(trimmed, {
      onSuccess: (bm) => setCreatedId(bm.id),
      onError: (err) =>
        setError(err instanceof ApiError ? messageForCode(err.code) : 'Could not add the link.'),
    });
  };

  const copyUrl = async () => {
    if (!created) return;
    try {
      await navigator.clipboard.writeText(created.url);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1500);
    } catch {
      /* clipboard unavailable — ignore */
    }
  };

  const save = () => {
    if (!created) return;
    edit.mutate({
      id: created.id,
      patch: { title: title.trim() || created.url, description: description.trim() },
    });
    onClose();
  };

  // Step 1 — paste a URL.
  if (!created) {
    return (
      <Modal title="Add bookmark" onClose={onClose}>
        <form onSubmit={onAdd}>
          <div className="field">
            <label className="field-label" htmlFor="add-url">
              Link
            </label>
            <input
              id="add-url"
              className="input mono"
              placeholder="https://…"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              autoFocus
            />
            <p className="field-hint">
              Paste a URL — markit fetches the title and indexes the page so you can search inside it.
            </p>
          </div>
          {error && (
            <p className="inline-error" role="alert">
              {error}
            </p>
          )}
          <div className="modal-actions">
            <button type="button" className="btn btn-ghost" onClick={onClose}>
              Cancel
            </button>
            <button
              type="submit"
              className="btn btn-primary"
              disabled={create.isPending || !url.trim()}
            >
              {create.isPending ? 'Adding…' : 'Add bookmark'}
            </button>
          </div>
        </form>
      </Modal>
    );
  }

  // Step 2 — the created bookmark, live.
  const stateClass = `state-pill state-pill-${created.state.toLowerCase()}`;
  return (
    <Modal title="Bookmark added" onClose={onClose}>
      <div className="field">
        <label className="field-label">Link</label>
        <div className="copy-row">
          <span className="mono copy-url" title={created.url}>
            {created.url}
          </span>
          <button type="button" className="btn btn-sm btn-ghost" onClick={copyUrl}>
            {copied ? 'Copied' : 'Copy'}
          </button>
        </div>
      </div>

      <div className="field" style={{ marginTop: 'var(--sp-3)' }}>
        <label className="field-label" htmlFor="add-title">
          Title
        </label>
        <input
          id="add-title"
          className="input"
          placeholder={created.state === 'PENDING' ? 'Fetching title…' : 'Title'}
          value={title}
          onChange={(e) => {
            setTitle(e.target.value);
            setDirty(true);
          }}
        />
      </div>

      <div className="field" style={{ marginTop: 'var(--sp-3)' }}>
        <label className="field-label" htmlFor="add-notes">
          Notes
        </label>
        <textarea
          id="add-notes"
          className="input"
          rows={2}
          placeholder="Optional"
          value={description}
          onChange={(e) => {
            setDescription(e.target.value);
            setDirty(true);
          }}
        />
      </div>

      <div className="field" style={{ marginTop: 'var(--sp-3)' }}>
        <span className={stateClass}>
          <span className="state-dot" />
          {STATE_LABEL[created.state]}
        </span>
        {created.state === 'FAILED' && (
          <div className="card-failure" style={{ marginTop: 'var(--sp-2)' }}>
            {failureMessage(created)}
            <div className="card-failure-actions">
              <button className="btn btn-sm" onClick={() => rescrape.mutate(created.id)}>
                <RefreshIcon width={13} height={13} /> Re-scrape
              </button>
            </div>
          </div>
        )}
      </div>

      <div className="modal-actions">
        <button type="button" className="btn btn-ghost" onClick={onClose}>
          Done
        </button>
        <button type="button" className="btn btn-primary" onClick={save} disabled={edit.isPending}>
          Save changes
        </button>
      </div>
    </Modal>
  );
}
