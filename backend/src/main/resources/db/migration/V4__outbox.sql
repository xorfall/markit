-- Transactional Outbox (S3, ADR-0003, data-model §2/§4).
-- The domain event is written to this table in the SAME transaction as the state change (no dual
-- write). A polling relay publishes PENDING rows to RabbitMQ in id order and marks them PUBLISHED;
-- repeated publish failures land the row in DEAD so one poison event cannot block the relay.
-- No FK to aggregates: the row must survive the aggregate's deletion (e.g. bookmark.deleted).

CREATE TABLE outbox_event (
    id             BIGSERIAL PRIMARY KEY,
    aggregate_type TEXT NOT NULL,
    aggregate_id   UUID NOT NULL,
    event_type     TEXT NOT NULL,
    payload        JSONB NOT NULL,
    status         TEXT NOT NULL DEFAULT 'PENDING',
    attempts       INT NOT NULL DEFAULT 0,
    last_error     TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'DEAD'))
);

-- Relay polling: index only the hot PENDING rows so published/dead rows are skipped cheaply.
CREATE INDEX ix_outbox_pending ON outbox_event (id) WHERE status = 'PENDING';

-- Per-aggregate ordered replay (NFR-CONS-003): events of one aggregate read in id order.
CREATE INDEX ix_outbox_aggregate ON outbox_event (aggregate_id, id);
