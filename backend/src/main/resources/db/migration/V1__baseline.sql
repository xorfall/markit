-- V1 baseline (S0). Enables extensions the domain schema will rely on later.
-- Case-insensitive text for unique email / tag name (data-model §3).
CREATE EXTENSION IF NOT EXISTS citext;
