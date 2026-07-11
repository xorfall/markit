-- Scraping bounded context (S4, architecture §4.1, data-model bookmark_content 1:1).
-- Two-phase async scrape: a scrape.requested event is dispatched via the outbox; results
-- (metadata, content, failure) arrive back as events. The scraped page text is stored here in
-- Postgres as the source of truth (the ES projection stays content-free until S5). failure_reason
-- surfaces the last scrape error on the bookmark itself (api-contract §6, FR-SCR-005).

ALTER TABLE bookmarks ADD COLUMN failure_reason TEXT;

CREATE TABLE bookmark_content (
    bookmark_id   UUID PRIMARY KEY REFERENCES bookmarks (id) ON DELETE CASCADE,
    content       TEXT NOT NULL,
    content_bytes INT NOT NULL,
    scraped_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
