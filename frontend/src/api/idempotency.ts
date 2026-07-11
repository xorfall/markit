/**
 * Client-generated Idempotency-Key for create mutations (client-contract A.1,
 * api-contract §1). The same key must be reused across retries of one logical
 * create so the server does not double-create after a network timeout.
 */
export function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  // Fallback for environments without crypto.randomUUID.
  return `idem-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export const IDEMPOTENCY_HEADER = 'Idempotency-Key';
