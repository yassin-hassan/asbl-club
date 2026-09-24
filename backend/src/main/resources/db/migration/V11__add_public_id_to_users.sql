-- Stable, non-guessable user ID that is safe to expose (JWT "sub", API responses).
-- The BIGINT id stays the primary key for joins and foreign keys and never leaves the server.

-- 1. Add the column as nullable: existing rows have no value yet.
ALTER TABLE users ADD COLUMN public_id UUID;

-- 2. Backfill existing rows, one random UUID each.
UPDATE users SET public_id = gen_random_uuid() WHERE public_id IS NULL;

-- 3. Now every row has a value: enforce it. The default is a safety net for inserts made
--    outside the application; the application generates its own UUIDs.
ALTER TABLE users
    ALTER COLUMN public_id SET NOT NULL,
    ALTER COLUMN public_id SET DEFAULT gen_random_uuid(),
    ADD CONSTRAINT users_public_id_key UNIQUE (public_id);
