import { useAuth } from './AuthContext';

/**
 * Google sign-in. Google Identity Services is not wired end-to-end in this
 * slice; the button is only rendered when VITE_GOOGLE_CLIENT_ID is configured,
 * and it posts a stub id token to /auth/google so the flow is exercised without
 * blocking the build on Google being set up (see task S6 deviation notes).
 */
export function GoogleButton({ onError }: { onError: (message: string) => void }): JSX.Element | null {
  const { loginWithGoogle } = useAuth();
  const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;

  if (!clientId) return null;

  const handleClick = async () => {
    try {
      // Real integration would obtain this token from Google Identity Services.
      await loginWithGoogle('stub-google-id-token');
    } catch {
      onError('Google sign-in is not available right now.');
    }
  };

  return (
    <button type="button" className="btn btn-block" onClick={handleClick}>
      Continue with Google
    </button>
  );
}
