import { useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { Board } from '../board/Board';
import { CommandBar } from '../search/CommandBar';
import { SearchResults } from '../search/SearchResults';
import { ThemeToggle } from '../components/ThemeToggle';
import { useDebounced, useOnlineStatus } from './useDebounced';

/**
 * Authenticated app shell: the hero command bar in the topbar, and below it
 * either the board or — when a query is active — the search result list (A.5).
 */
export function AppShell(): JSX.Element {
  const { user, logout } = useAuth();
  const [query, setQuery] = useState('');
  const debouncedQuery = useDebounced(query, 250);
  const online = useOnlineStatus();

  const searching = debouncedQuery.trim().length > 0;

  return (
    <div className="app-shell">
      <header className="topbar">
        <span className="brand">markit</span>
        <div className="topbar-search">
          <CommandBar value={query} onChange={setQuery} />
        </div>
        <div className="topbar-user">
          <ThemeToggle />
          {user && <span className="mono" style={{ fontSize: 12 }}>{user.email}</span>}
          <button className="btn btn-sm btn-ghost" onClick={() => void logout()}>
            Sign out
          </button>
        </div>
      </header>

      {!online && (
        <div className="banner banner-offline" role="status">
          You are offline. Reconnect to make changes — your board stays readable.
        </div>
      )}

      {searching ? <SearchResults query={debouncedQuery} /> : <Board />}
    </div>
  );
}
