-- Visitors buy a ticket for a public event without an account (a "guest"), as the analysis planned (guest_email, V6).
-- The booking keeps who they are (name, address, language of their emails) and the hash of a secret link: the
-- link is their only way back to the booking (to pay, then show the ticket), so it's emailed to them, and only its
-- SHA-256 hash is stored, like the other one-time links: a copy of this table can't be used to open a booking.
ALTER TABLE registrations
    ADD COLUMN guest_name        VARCHAR(255),
    ADD COLUMN guest_language    VARCHAR(5) CHECK (guest_language IN ('fr', 'nl', 'en')),
    ADD COLUMN access_token_hash VARCHAR(64) UNIQUE;

-- A guest booking is complete: someone to send the ticket to, and the link to come back with.
ALTER TABLE registrations ADD CONSTRAINT registrations_guest_complete CHECK (
    user_id IS NOT NULL
    OR (guest_email IS NOT NULL AND guest_name IS NOT NULL AND guest_language IS NOT NULL
        AND access_token_hash IS NOT NULL));
