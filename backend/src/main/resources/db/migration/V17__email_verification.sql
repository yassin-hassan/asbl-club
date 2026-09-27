-- Accounts created before email verification existed count as verified (their owners have been using them).
UPDATE users SET email_verified_at = now() WHERE email_verified_at IS NULL AND deleted_at IS NULL;

-- "Confirm your email" links. Same design as the password reset links (V16): only a hash is stored, single use,
-- short-lived; the link itself exists only in the email.
CREATE TABLE email_verification_tokens (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX email_verification_tokens_user ON email_verification_tokens (user_id);
