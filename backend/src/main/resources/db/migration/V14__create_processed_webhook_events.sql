-- Stripe delivers each webhook at least once, and a retry carries the same event ID. One row per event handled,
-- written in the same transaction as its effect: the primary key turns a repeat (even a simultaneous one) into a
-- no-op. Rows older than Stripe's retry window are deleted by a daily job.
CREATE TABLE processed_webhook_events (
    event_id    VARCHAR(255) PRIMARY KEY,
    type        VARCHAR(100) NOT NULL,
    received_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
