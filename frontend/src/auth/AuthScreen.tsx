import { useState, type FormEvent } from 'react';
import { ApiError, messageForCode, toApiError } from '../api/errors';
import { useAuth } from './AuthContext';
import { GoogleButton } from './GoogleButton';

type Mode = 'login' | 'register';

/** Combined sign-in / create-account screen. Copy is active, sentence case. */
export function AuthScreen(): JSX.Element {
  const { login, register } = useAuth();
  const [mode, setMode] = useState<Mode>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const isRegister = mode === 'register';

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setFieldError(null);

    if (!email.trim() || !password) {
      setFieldError('Enter your email and password.');
      return;
    }
    if (isRegister && password.length < 8) {
      setFieldError('Use at least 8 characters for your password.');
      return;
    }

    setBusy(true);
    try {
      if (isRegister) await register(email.trim(), password);
      else await login(email.trim(), password);
    } catch (err) {
      const apiErr = err instanceof ApiError ? err : toApiError(err);
      setError(messageForCode(apiErr.code, apiErr.detail));
    } finally {
      setBusy(false);
    }
  };

  const switchMode = () => {
    setMode(isRegister ? 'login' : 'register');
    setError(null);
    setFieldError(null);
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
          </div>

          {fieldError && <p className="inline-error">{fieldError}</p>}
          {error && <p className="inline-error">{error}</p>}

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
