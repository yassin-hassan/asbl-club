-- The association's join link: a random token its administrators share. NULL = no link (switched off).
-- Joining through it only creates a PENDING membership; an administrator approves it.
ALTER TABLE asbls ADD COLUMN join_token VARCHAR(64) UNIQUE;
