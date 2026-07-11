/**
 * Token storage policy (client-contract / api-contract §2):
 * - access token lives in memory only (never persisted — reduces XSS blast radius);
 * - refresh token lives in localStorage so a reload can silently re-authenticate.
 *
 * A small subscriber list lets the auth context react when the store is cleared
 * by the refresh interceptor after a failed refresh.
 */

const REFRESH_KEY = 'markit.refreshToken';

let accessToken: string | null = null;
const listeners = new Set<() => void>();

function notify(): void {
  for (const listener of listeners) listener();
}

export const tokenStore = {
  getAccessToken(): string | null {
    return accessToken;
  },

  getRefreshToken(): string | null {
    try {
      return localStorage.getItem(REFRESH_KEY);
    } catch {
      return null;
    }
  },

  set(tokens: { accessToken: string; refreshToken: string }): void {
    accessToken = tokens.accessToken;
    try {
      localStorage.setItem(REFRESH_KEY, tokens.refreshToken);
    } catch {
      /* storage disabled — access token in memory still works for the session */
    }
    notify();
  },

  setAccessToken(token: string): void {
    accessToken = token;
    notify();
  },

  clear(): void {
    accessToken = null;
    try {
      localStorage.removeItem(REFRESH_KEY);
    } catch {
      /* ignore */
    }
    notify();
  },

  hasSession(): boolean {
    return accessToken !== null || this.getRefreshToken() !== null;
  },

  subscribe(listener: () => void): () => void {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};
