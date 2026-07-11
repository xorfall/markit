import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from './AuthContext';

/** Gate the app shell: loading spinner while resolving, redirect if anonymous. */
export function ProtectedRoute({ children }: { children: ReactNode }): JSX.Element {
  const { status } = useAuth();

  if (status === 'loading') {
    return (
      <div className="center-fill" style={{ minHeight: '100vh' }}>
        <div className="spinner" role="status" aria-label="Loading markit" />
      </div>
    );
  }

  if (status === 'anonymous') {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
}
