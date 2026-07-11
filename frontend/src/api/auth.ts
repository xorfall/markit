import { request } from './http';
import type { AuthTokens, RegisterResponse, User } from './types';

/** Auth endpoints (api-contract §2). All are unauthenticated (`auth: false`). */
export const authApi = {
  register(email: string, password: string): Promise<RegisterResponse> {
    return request<RegisterResponse>('/auth/register', {
      method: 'POST',
      body: { email, password },
      auth: false,
    });
  },

  login(email: string, password: string): Promise<AuthTokens> {
    return request<AuthTokens>('/auth/login', {
      method: 'POST',
      body: { email, password },
      auth: false,
    });
  },

  google(idToken: string): Promise<AuthTokens> {
    return request<AuthTokens>('/auth/google', {
      method: 'POST',
      body: { idToken },
      auth: false,
    });
  },

  logout(refreshToken: string): Promise<void> {
    return request<void>('/auth/logout', {
      method: 'POST',
      body: { refreshToken },
      auth: false,
    });
  },

  me(): Promise<User> {
    return request<User>('/me');
  },
};
