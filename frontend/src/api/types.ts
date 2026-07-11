/**
 * Shared DTOs mirroring docs/api-contract.md. These are the wire shapes the
 * server returns; the UI adds a few client-only fields (see board/model.ts).
 */

export type BookmarkState = 'PENDING' | 'INDEXED' | 'FAILED';

export type FailureReason = 'CONTENT_TOO_LARGE' | 'UNSAFE_URL' | 'FETCH_ERROR';

export interface User {
  id: string;
  email: string;
  createdAt: string;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
}

export interface RegisterResponse extends AuthTokens {
  user: User;
}

export interface Collection {
  id: string;
  name: string;
  position: number;
  categories?: Category[];
}

export interface Category {
  id: string;
  collectionId: string;
  name: string;
  position: number;
}

export interface Bookmark {
  id: string;
  categoryId: string;
  url: string;
  title: string;
  description: string | null;
  state: BookmarkState;
  failureReason?: FailureReason | null;
  tags: string[];
  createdAt: string;
  updatedAt: string;
}

export interface SearchResultItem {
  bookmark: Bookmark;
  /** Plain-text context with the matched span wrapped in <em>…</em>. Never HTML. */
  snippet: string | null;
  score: number;
}

export interface SearchResponse {
  degraded: boolean;
  contentSearchAvailable: boolean;
  results: SearchResultItem[];
  nextCursor: string | null;
}
