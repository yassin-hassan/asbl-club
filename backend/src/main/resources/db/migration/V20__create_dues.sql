-- Membership dues (cotisations): one yearly fee per association, paid online like a ticket.

-- The association's yearly fee; empty = it doesn't collect dues.
ALTER TABLE asbls ADD COLUMN annual_fee NUMERIC(8,2) CHECK (annual_fee > 0);

-- A member's dues for one year: a payable (type MEMBERSHIP, planned in V6), paid through the same payments table.
-- Tied to the association and the person, not to the membership row: the payment record outlives a membership
-- (someone who leaves or closes their account keeps their history, anonymised like any payment).
CREATE TABLE dues (
    id      BIGINT   PRIMARY KEY REFERENCES payables(id) ON DELETE CASCADE,
    asbl_id BIGINT   NOT NULL REFERENCES asbls(id),
    user_id BIGINT   NOT NULL REFERENCES users(id),
    year    INTEGER  NOT NULL CHECK (year BETWEEN 2000 AND 2100),
    -- One set of dues per person, association and year: paying twice is impossible, even with two tabs open.
    UNIQUE (asbl_id, user_id, year)
);
