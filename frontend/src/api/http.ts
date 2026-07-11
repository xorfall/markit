/**
 * Typed fetch wrapper for the markit API.
 *
 * Responsibilities:
 * - prefix the versioned base path (VITE_API_BASE, default `/api/v1`);
 * - attach the bearer access token;
 * - parse RFC 7807 problem+json into a typed {@link ApiError};
 * - a single-flight 401 interceptor that refreshes the access token and retries
 *   the original request exactly once (client-contract A.1 / api-contract §2).
 */

import {
  ApiError,
  resolveErrorCode,
  type ProblemJson,
} from './errors';
import { tokenStore } from './tokenStore';

const BASE_URL = (import.meta.env.VITE_API_BASE ?? '/api/v1').replace(/\/$/, '');

export interface RequestOptions {
  method?: string;
  /** JSON-serialisable body. Omit for GET/DELETE without payload. */
  body?: unknown;
  /** Extra headers, e.g. an Idempotency-Key on create mutations. */
  headers?: Record<string, string>;
  /** Skip the bearer token + refresh flow (used by /auth/* endpoints). */
  auth?: boolean;
  signal?: AbortSignal;
}

/** In-flight refresh promise, shared so concurrent 401s trigger one refresh. */
let refreshInFlight: Promise<boolean> | null = null;

async function parseProblem(response: Response): Promise<ApiError> {
  let problem: ProblemJson = {};
  try {
    const text = await response.text();
    if (text) problem = JSON.parse(text) as ProblemJson;
  } catch {
    /* non-JSON error body — fall back to status-derived code */
  }
  const code = resolveErrorCode(response.status, problem.code);
  return new ApiError({
    code,
    status: response.status,
    message: problem.title ?? problem.detail ?? response.statusText,
    detail: problem.detail,
    traceId: problem.traceId ?? response.headers.get('X-Trace-Id') ?? undefined,
    fieldErrors: problem.errors,
  });
}

async function runRefresh(): Promise<boolean> {
  const refreshToken = tokenStore.getRefreshToken();
  if (!refreshToken) return false;
  try {
    const response = await fetch(`${BASE_URL}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    });
    if (!response.ok) {
      tokenStore.clear();
      return false;
    }
    const tokens = (await response.json()) as {
      accessToken: string;
      refreshToken: string;
    };
    tokenStore.set(tokens);
    return true;
  } catch {
    return false;
  }
}

/** Refresh once, sharing the promise across concurrent callers. */
function refreshAccessToken(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = runRefresh().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

async function rawRequest(path: string, opts: RequestOptions): Promise<Response> {
  const headers: Record<string, string> = { ...opts.headers };
  const useAuth = opts.auth !== false;

  if (opts.body !== undefined) headers['Content-Type'] = 'application/json';
  if (useAuth) {
    const token = tokenStore.getAccessToken();
    if (token) headers['Authorization'] = `Bearer ${token}`;
  }

  const requestInit: RequestInit = {
    method: opts.method ?? 'GET',
    headers,
  };
  if (opts.body !== undefined) requestInit.body = JSON.stringify(opts.body);
  if (opts.signal) requestInit.signal = opts.signal;

  return fetch(`${BASE_URL}${path}`, requestInit);
}

/**
 * Perform a request, decode the JSON body as `T`, and translate failures into
 * typed errors. `204 No Content` resolves to `undefined as T`.
 */
export async function request<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  let response: Response;
  try {
    response = await rawRequest(path, opts);
  } catch {
    throw new ApiError({
      code: 'NETWORK',
      status: 0,
      message: 'Network request failed.',
    });
  }

  // 401 interceptor: refresh once, then retry the original request.
  if (response.status === 401 && opts.auth !== false && !isAuthPath(path)) {
    const refreshed = await refreshAccessToken();
    if (refreshed) {
      try {
        response = await rawRequest(path, opts);
      } catch {
        throw new ApiError({
          code: 'NETWORK',
          status: 0,
          message: 'Network request failed.',
        });
      }
    }
  }

  if (!response.ok) throw await parseProblem(response);

  if (response.status === 204) return undefined as T;
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

function isAuthPath(path: string): boolean {
  return path.startsWith('/auth/');
}

export const apiBaseUrl = BASE_URL;
