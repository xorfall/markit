import type { Bookmark } from '../api/types';

/** Prefix marking a client-only optimistic entity before the server assigns an id. */
const TEMP_PREFIX = 'temp:';

export function tempId(): string {
  const rand =
    typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? crypto.randomUUID()
      : Math.random().toString(16).slice(2);
  return `${TEMP_PREFIX}${rand}`;
}

export function isTempId(id: string): boolean {
  return id.startsWith(TEMP_PREFIX);
}

/** Build the optimistic PENDING bookmark shown the instant a URL is added (A.2). */
export function optimisticBookmark(categoryId: string, url: string): Bookmark {
  const now = new Date().toISOString();
  return {
    id: tempId(),
    categoryId,
    url,
    title: url, // client-contract A.2: title = url until metadata arrives
    description: null,
    state: 'PENDING',
    failureReason: null,
    tags: [],
    createdAt: now,
    updatedAt: now,
  };
}

/** True while a bookmark is still being scraped/indexed (drives polling + underline). */
export function isPending(bookmark: Bookmark): boolean {
  return bookmark.state === 'PENDING';
}

/** Plain-language failure copy for a FAILED card (client-contract A.3). */
export function failureMessage(bookmark: Bookmark): string {
  switch (bookmark.failureReason) {
    case 'CONTENT_TOO_LARGE':
      return 'This page is too large to index.';
    case 'UNSAFE_URL':
      return 'We could not safely reach this address.';
    case 'FETCH_ERROR':
      return 'We could not load this page.';
    default:
      return 'We could not index this page.';
  }
}
