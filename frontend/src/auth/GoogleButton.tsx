import { GoogleLogin } from '@react-oauth/google';
import { useAuth } from './AuthContext';

/**
 * Real Google sign-in via Google Identity Services. Rendered only when
 * `VITE_GOOGLE_CLIENT_ID` is set (and the app is wrapped in `GoogleOAuthProvider`,
 * see main.tsx). On success GIS returns a real credential (an id-token JWT) which we
 * post to `/auth/google`; the backend verifies it against Google and issues our JWT.
 */
export function GoogleButton({ onError }: { onError: (message: string) => void }): JSX.Element | null {
  const { loginWithGoogle } = useAuth();
  const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;

  if (!clientId) return null;

  return (
    <div className="google-btn">
      <GoogleLogin
        text="continue_with"
        onSuccess={async (credentialResponse) => {
          const idToken = credentialResponse.credential;
          if (!idToken) {
            onError('Google did not return a credential. Try again.');
            return;
          }
          try {
            await loginWithGoogle(idToken);
          } catch {
            onError('Google sign-in failed. Try again.');
          }
        }}
        onError={() => onError('Google sign-in was cancelled or failed.')}
      />
    </div>
  );
}
