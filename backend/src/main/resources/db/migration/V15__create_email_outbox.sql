-- Emails waiting to be sent, written in the same transaction as the action that causes them (a password reset
-- request, …): if that transaction rolls back, no email; if it commits, the email will go out, even if the mail
-- server is down right now. A background job sends them with retries. Once sent, the body is cleared (it may
-- contain a one-time link); the row stays as a trace.
CREATE TABLE email_outbox (
    id              BIGSERIAL    PRIMARY KEY,
    recipient       VARCHAR(255) NOT NULL,
    subject         VARCHAR(255) NOT NULL,
    body            TEXT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_error      VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at         TIMESTAMPTZ
);

-- The sender's question: "what is due now?"
CREATE INDEX email_outbox_due ON email_outbox (next_attempt_at) WHERE status = 'PENDING';
