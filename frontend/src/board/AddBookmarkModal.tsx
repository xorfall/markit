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
 * Add-bookmark dialog — a single view. You paste a URL at the top; the title, notes
 * and state below fill in live from the scrape (PENDING → INDEXED/FAILED). Nothing
 * jumps to a second screen: the same form grows into the created bookmark.
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

  const isValidUrl = (value: string): boolean => {
    try {
      const parsed = new URL(value);
      return parsed.protocol === 'http:' || parsed.protocol === 'https:';
    } catch {
      return false;
    }
  };

  // Create as soon as there's a valid URL — on blur, on Enter, or via the button — so
  // the title and content start fetching without waiting for a separate click.
  const createFromUrl = () => {
    const trimmed = url.trim();
    if (!trimmed || created || create.isPending || !isValidUrl(trimmed)) return;
    setError(null);
    create.mutate(trimmed, {
      onSuccess: (bm) => setCreatedId(bm.id),
      onError: (err) =>
        setError(err instanceof ApiError ? messageForCode(err.code) : 'Could not add the link.'),
    });
  };

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    createFromUrl();
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

  const titlePlaceholder = !created
    ? 'Fills in automatically once you add the link'
    : created.state === 'PENDING'
      ? 'Fetching title…'
      : 'Title';

  return (
    <Modal title="Add bookmark" onClose={onClose}>
      <form onSubmit={onSubmit}>
        <div className="field">
          <label className="field-label" htmlFor="add-url">
            Link
          </label>
          {created ? (
            <div className="copy-row">
              <span className="mono copy-url" title={created.url}>
                {created.url}
              </span>
              <button type="button" className="btn btn-sm btn-ghost" onClick={copyUrl}>
                {copied ? 'Copied' : 'Copy'}
              </button>
            </div>
          ) : (
            <input
              id="add-url"
              className="input mono"
              placeholder="https://…"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              onBlur={createFromUrl}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault();
                  createFromUrl();
                }
              }}
              autoFocus
            />
          )}
          {!created && (
            <p className="field-hint">
              Paste a URL — the title and content start loading automatically; no need to press a button.
            </p>
          )}
        </div>

        <div className="field" style={{ marginTop: 'var(--sp-3)' }}>
          <label className="field-label" htmlFor="add-title">
            Title
          </label>
          <input
            id="add-title"
            className="input"
            placeholder={titlePlaceholder}
            value={title}
            disabled={!created}
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
            placeholder={created ? 'Optional' : 'Add a note once the link is saved'}
            value={description}
            disabled={!created}
            onChange={(e) => {
              setDescription(e.target.value);
              setDirty(true);
            }}
          />
        </div>

        {created && (
          <div className="field" style={{ marginTop: 'var(--sp-3)' }}>
            <span className={`state-pill state-pill-${created.state.toLowerCase()}`}>
              <span className="state-dot" />
              {STATE_LABEL[created.state]}
            </span>
            {created.state === 'FAILED' && (
              <div className="card-failure" style={{ marginTop: 'var(--sp-2)' }}>
                {failureMessage(created)}
                <div className="card-failure-actions">
                  <button
                    type="button"
                    className="btn btn-sm"
                    onClick={() => rescrape.mutate(created.id)}
                  >
                    <RefreshIcon width={13} height={13} /> Re-scrape
                  </button>
                </div>
              </div>
            )}
          </div>
        )}

        {error && (
          <p className="inline-error" role="alert">
            {error}
          </p>
        )}

        <div className="modal-actions">
          {created ? (
            <>
              <button type="button" className="btn btn-ghost" onClick={onClose}>
                Done
              </button>
              <button
                type="button"
                className="btn btn-primary"
                onClick={save}
                disabled={edit.isPending}
              >
                Save changes
              </button>
            </>
          ) : (
            <>
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
            </>
          )}
        </div>
      </form>
    </Modal>
  );
}
