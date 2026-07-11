-- Identity bounded context (ADR-0006, data-model §2).

CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         CITEXT NOT NULL UNIQUE,
    password_hash TEXT,                         -- null for OAuth-only accounts (S1b)
    google_sub    TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Google identity is unique only when present (data-model §3).
CREATE UNIQUE INDEX ux_users_google_sub ON users (google_sub) WHERE google_sub IS NOT NULL;

-- Rotating, revocable refresh tokens (ADR-0006). Only the hash is stored, never the raw token.
CREATE TABLE refresh_tokens (
    id           UUID PRIMARY KEY,
    user_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash   TEXT NOT NULL UNIQUE,
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
