import { QueryClient } from '@tanstack/react-query';
import { ApiError } from '../api/errors';

/**
 * Shared TanStack Query client. Auth/not-found failures are not retried (a
 * refresh already happened in the http layer); transient/network errors get a
 * single retry.
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      refetchOnWindowFocus: false,
      retry: (failureCount, error) => {
        if (error instanceof ApiError) {
          if (error.code === 'UNAUTHORIZED' || error.code === 'NOT_FOUND') return false;
        }
        return failureCount < 1;
      },
    },
    mutations: {
      retry: false,
    },
  },
});
