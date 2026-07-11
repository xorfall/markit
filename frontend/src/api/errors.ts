/**
 * Typed error model for RFC 7807 `application/problem+json` responses
 * (api-contract §7). The rest of the app switches on `ApiError.code`, never on
 * raw strings or HTTP status.
 */

export type ApiErrorCode =
  | 'VALIDATION'
  | 'UNAUTHORIZED'
  | 'FORBIDDEN'
  | 'NOT_FOUND'
  | 'DUPLICATE_URL'
  | 'EMAIL_TAKEN'
  | 'INVALID_CREDENTIALS'
  | 'CONTENT_TOO_LARGE'
  | 'UNSAFE_URL'
  | 'RATE_LIMITED'
  | 'INTERNAL'
  | 'NETWORK'
  | 'UNKNOWN';

export interface ProblemFieldError {
  field: string;
  message: string;
}

export interface ProblemJson {
  type?: string;
  title?: string;
  status?: number;
  code?: string;
  detail?: string;
  traceId?: string;
  errors?: ProblemFieldError[];
}

export class ApiError extends Error {
  readonly code: ApiErrorCode;
  readonly status: number;
  readonly detail?: string;
  readonly traceId?: string;
  readonly fieldErrors: ProblemFieldError[];

  constructor(params: {
    code: ApiErrorCode;
    status: number;
    message: string;
    detail?: string;
    traceId?: string;
    fieldErrors?: ProblemFieldError[];
  }) {
    super(params.message);
    this.name = 'ApiError';
    this.code = params.code;
    this.status = params.status;
    this.detail = params.detail;
    this.traceId = params.traceId;
    this.fieldErrors = params.fieldErrors ?? [];
  }

  /** True for errors a retry with the same Idempotency-Key can recover from. */
  get isRetryable(): boolean {
    return this.code === 'NETWORK' || this.status >= 500;
  }
}

/** Narrow an unknown thrown value into an ApiError with a best-effort code. */
export function toApiError(err: unknown): ApiError {
  if (err instanceof ApiError) return err;
  const message = err instanceof Error ? err.message : 'Something went wrong.';
  return new ApiError({ code: 'UNKNOWN', status: 0, message });
}

/**
 * Map a problem+json code/status into our closed ApiErrorCode union. The server
 * uses `FORBIDDEN`/not-found interchangeably for non-enumeration (api-contract
 * §1); we normalise both to NOT_FOUND for the UI.
 */
export function resolveErrorCode(status: number, rawCode?: string): ApiErrorCode {
  switch (rawCode) {
    case 'VALIDATION':
    case 'UNAUTHORIZED':
    case 'DUPLICATE_URL':
    case 'EMAIL_TAKEN':
    case 'INVALID_CREDENTIALS':
    case 'CONTENT_TOO_LARGE':
    case 'UNSAFE_URL':
    case 'RATE_LIMITED':
    case 'INTERNAL':
      return rawCode;
    case 'FORBIDDEN':
      return 'NOT_FOUND';
    default:
      break;
  }
  switch (status) {
    case 400:
      return 'VALIDATION';
    case 401:
      return 'UNAUTHORIZED';
    case 403:
    case 404:
      return 'NOT_FOUND';
    case 409:
      return 'DUPLICATE_URL';
    case 429:
      return 'RATE_LIMITED';
    default:
      return status >= 500 ? 'INTERNAL' : 'UNKNOWN';
  }
}

/**
 * Human, end-user framed copy for an error — active voice, says what happened
 * and (where useful) how to fix it (client-contract B.4 / interface voice).
 */
export function messageForCode(code: ApiErrorCode, fallback?: string): string {
  switch (code) {
    case 'DUPLICATE_URL':
      return 'Already in this category.';
    case 'EMAIL_TAKEN':
      return 'That email is already registered. Try signing in instead.';
    case 'INVALID_CREDENTIALS':
      return 'Email or password is incorrect.';
    case 'VALIDATION':
      return fallback ?? 'Please check the highlighted fields and try again.';
    case 'UNAUTHORIZED':
      return 'Your session expired. Sign in again to continue.';
    case 'NOT_FOUND':
      return "That item no longer exists. It may have been removed.";
    case 'CONTENT_TOO_LARGE':
      return 'The page is too large to index.';
    case 'UNSAFE_URL':
      return "We could not safely reach that address.";
    case 'RATE_LIMITED':
      return 'Too many requests. Wait a moment and try again.';
    case 'NETWORK':
      return "We could not reach markit. Check your connection.";
    default:
      return fallback ?? 'Something went wrong. Please try again.';
  }
}
