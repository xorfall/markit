import { Navigate } from 'react-router-dom';
import { AuthScreen } from '../auth/AuthScreen';
import { useAuth } from '../auth/AuthContext';

/** Public auth route — sends already-authenticated users to the board. */
export function LoginRoute(): JSX.Element {
  const { status } = useAuth();
  if (status === 'authenticated') return <Navigate to="/" replace />;
  return <AuthScreen />;
}
