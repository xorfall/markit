import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { authApi } from '../api/auth';
import { tokenStore } from '../api/tokenStore';
import type { User } from '../api/types';
import { queryClient } from '../lib/queryClient';

type AuthStatus = 'loading' | 'authenticated' | 'anonymous';

interface AuthContextValue {
  status: AuthStatus;
  user: User | null;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string) => Promise<void>;
  loginWithGoogle: (idToken: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * Owns the session. On mount it tries to resolve the current user from a stored
 * refresh token (the http layer refreshes the access token on the first 401),
 * so a reload restores the session without re-login.
 */
export function AuthProvider({ children }: { children: ReactNode }): JSX.Element {
  const [status, setStatus] = useState<AuthStatus>('loading');
  const [user, setUser] = useState<User | null>(null);

  const loadUser = useCallback(async () => {
    try {
      const me = await authApi.me();
      setUser(me);
      setStatus('authenticated');
    } catch {
      tokenStore.clear();
      setUser(null);
      setStatus('anonymous');
    }
  }, []);

  useEffect(() => {
    if (tokenStore.hasSession()) {
      void loadUser();
    } else {
      setStatus('anonymous');
    }
  }, [loadUser]);

  // React to the http layer clearing tokens after a failed refresh.
  useEffect(() => {
    return tokenStore.subscribe(() => {
      if (!tokenStore.hasSession()) {
        setUser(null);
        setStatus('anonymous');
        queryClient.clear();
      }
    });
  }, []);

  const login = useCallback(
    async (email: string, password: string) => {
      const tokens = await authApi.login(email, password);
      tokenStore.set(tokens);
      await loadUser();
    },
    [loadUser],
  );

  const register = useCallback(async (email: string, password: string) => {
    const res = await authApi.register(email, password);
    tokenStore.set({ accessToken: res.accessToken, refreshToken: res.refreshToken });
    setUser(res.user);
    setStatus('authenticated');
  }, []);

  const loginWithGoogle = useCallback(
    async (idToken: string) => {
      const tokens = await authApi.google(idToken);
      tokenStore.set(tokens);
      await loadUser();
    },
    [loadUser],
  );

  const logout = useCallback(async () => {
    const refreshToken = tokenStore.getRefreshToken();
    if (refreshToken) {
      try {
        await authApi.logout(refreshToken);
      } catch {
        /* revoke best-effort; clear locally regardless */
      }
    }
    tokenStore.clear();
    setUser(null);
    setStatus('anonymous');
    queryClient.clear();
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ status, user, login, register, loginWithGoogle, logout }),
    [status, user, login, register, loginWithGoogle, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
  return ctx;
}
