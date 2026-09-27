-- Personal invitations sent by email by an association's administrators. Unlike the shared join link, an invitation
-- is for one address and joins directly (no approval): an administrator already chose the person. Only a hash of the
-- link's token is stored; single use; valid 14 days.
CREATE TABLE invitations (
    id          BIGSERIAL    PRIMARY KEY,
    asbl_id     BIGINT       NOT NULL REFERENCES asbls(id) ON DELETE CASCADE,
    email       VARCHAR(255) NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    invited_by  BIGINT       NOT NULL REFERENCES users(id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ  NOT NULL,
    accepted_at TIMESTAMPTZ
);

-- The daily cap per association, and the pending list.
CREATE INDEX invitations_asbl_created ON invitations (asbl_id, created_at);
