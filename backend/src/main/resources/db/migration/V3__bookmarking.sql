-- Bookmarking bounded context (S2, data-model §2/§3/§4).
-- The Collection -> Category -> Bookmark tree. Every table carries a denormalized owner_id for
-- O(1) per-user isolation (R-SEC-01, NFR-SEC-002) — never trust a client-supplied owner.

CREATE TABLE collections (
    id         UUID PRIMARY KEY,
    owner_id   UUID NOT NULL,
    name       TEXT NOT NULL,
    position   INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_collections_owner ON collections (owner_id);
CREATE INDEX ix_collections_owner_position ON collections (owner_id, position);

CREATE TABLE categories (
    id            UUID PRIMARY KEY,
    collection_id UUID NOT NULL REFERENCES collections (id) ON DELETE CASCADE,
    owner_id      UUID NOT NULL,
    name          TEXT NOT NULL,
    position      INT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_categories_owner ON categories (owner_id);
CREATE INDEX ix_categories_collection_position ON categories (collection_id, position);

CREATE TABLE bookmarks (
    id          UUID PRIMARY KEY,
    category_id UUID NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    owner_id    UUID NOT NULL,
    url         TEXT NOT NULL,
    title       TEXT NOT NULL,
    description TEXT,
    state       TEXT NOT NULL,
    position    INT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_bookmarks_state CHECK (state IN ('PENDING', 'INDEXED', 'FAILED')),
    -- No duplicate URL within one category (FR-BMK-007) — race-safe at the DB, not app-level.
    CONSTRAINT ux_bookmarks_category_url UNIQUE (category_id, url)
);

CREATE INDEX ix_bookmarks_owner ON bookmarks (owner_id);
CREATE INDEX ix_bookmarks_category_position ON bookmarks (category_id, position);
