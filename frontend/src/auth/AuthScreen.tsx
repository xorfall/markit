import { useState, type FormEvent } from 'react';
import { ApiError, messageForCode, toApiError } from '../api/errors';
import { useAuth } from './AuthContext';
import { GoogleButton } from './GoogleButton';

type Mode = 'login' | 'register';

const EMAIL_RE = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const MIN_PASSWORD = 8;

/** Turn the server's field errors into one readable line ("Email: must be a valid address"). */
function describeFieldErrors(err: ApiError): string | null {
  if (err.fieldErrors.length === 0) return null;
  return err.fieldErrors
    .map((f) => `${f.field.charAt(0).toUpperCase()}${f.field.slice(1)}: ${f.message}`)
    .join(' · ');
}

/** Combined sign-in / create-account screen. Copy is active, sentence case. */
export function AuthScreen(): JSX.Element {
  const { login, register } = useAuth();
  const [mode, setMode] = useState<Mode>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const isRegister = mode === 'register';

  // Client-side rules, so the user gets instant, specific feedback before any request.
  const validate = (): string | null => {
    if (!email.trim()) return 'Enter your email address.';
    if (!EMAIL_RE.test(email.trim())) return 'Enter a valid email address, like you@example.com.';
    if (!password) return 'Enter your password.';
    if (isRegister && password.length < MIN_PASSWORD) {
      return `Use at least ${MIN_PASSWORD} characters for your password.`;
    }
    return null;
  };

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);

    const problem = validate();
    if (problem) {
      setError(problem);
      return;
    }

    setBusy(true);
    try {
      if (isRegister) await register(email.trim(), password);
      else await login(email.trim(), password);
    } catch (err) {
      const apiErr = err instanceof ApiError ? err : toApiError(err);
      // Prefer the server's specific field errors; otherwise the friendly per-code copy.
      setError(describeFieldErrors(apiErr) ?? messageForCode(apiErr.code));
    } finally {
      setBusy(false);
    }
  };

  const switchMode = () => {
    setMode(isRegister ? 'login' : 'register');
    setError(null);
  };

  return (
    <div className="auth-wrap">
      <div className="auth-card">
        <div className="auth-head">
          <span className="brand auth-brand">markit</span>
          <p className="auth-tag">Find the link by what is written inside it.</p>
        </div>

        <form className="auth-form" onSubmit={onSubmit} noValidate>
          <div className="field">
            <label className="field-label" htmlFor="email">
              Email
            </label>
            <input
              id="email"
              className="input"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
            />
            <p className="field-hint">Use a valid email, like you@example.com.</p>
          </div>

          <div className="field">
            <label className="field-label" htmlFor="password">
              Password
            </label>
            <input
              id="password"
              className="input"
              type="password"
              autoComplete={isRegister ? 'new-password' : 'current-password'}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder={isRegister ? 'At least 8 characters' : 'Your password'}
            />
            {isRegister && <p className="field-hint">At least {MIN_PASSWORD} characters.</p>}
          </div>

          {error && (
            <p className="inline-error" role="alert">
              {error}
            </p>
          )}

          <button className="btn btn-primary btn-block" type="submit" disabled={busy}>
            {busy ? 'Working…' : isRegister ? 'Create account' : 'Sign in'}
          </button>
        </form>

        <div className="auth-divider">or</div>
        <GoogleButton onError={setError} />

        <p className="auth-switch">
          {isRegister ? 'Already have an account?' : "Don't have an account?"}{' '}
          <button type="button" onClick={switchMode}>
            {isRegister ? 'Sign in' : 'Create one'}
          </button>
        </p>
      </div>
    </div>
  );
}
