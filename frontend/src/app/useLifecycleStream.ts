import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { tokenStore } from '../api/tokenStore';
import { queryKeys } from '../lib/queryKeys';

const BASE_URL = (import.meta.env.VITE_API_BASE ?? '/api/v1').replace(/\/$/, '');

/**
 * Live scrape-status via Server-Sent Events (FR-SCR-003). Opens an EventSource to /events (the token
 * rides the query string since EventSource cannot set headers) and, on a bookmark.metadata /
 * bookmark.state push, refreshes the affected bookmark and the board. This is the push path of the
 * client-contract A.4 state machine; the per-bookmark polling remains as the automatic fallback when
 * the stream is unavailable.
 */
export function useLifecycleStream(enabled: boolean): void {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!enabled) {
      return;
    }
    const token = tokenStore.getAccessToken();
    if (!token) {
      return;
    }

    const source = new EventSource(`${BASE_URL}/events?access_token=${encodeURIComponent(token)}`);

    const onLifecycle = (event: MessageEvent): void => {
      try {
        const data = JSON.parse(event.data) as { id?: string };
        if (data.id) {
          void queryClient.invalidateQueries({ queryKey: queryKeys.bookmark(data.id) });
        }
      } catch {
        /* ignore malformed event */
      }
      void queryClient.invalidateQueries({ queryKey: ['bookmarks'] });
    };

    source.addEventListener('bookmark.metadata', onLifecycle);
    source.addEventListener('bookmark.state', onLifecycle);

    return () => source.close();
  }, [enabled, queryClient]);
}
