-- Keep recipient IDs, assignments and history. Existing USER profiles require an explicit grant.
ALTER TABLE users ADD COLUMN access_granted boolean NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN access_version bigint NOT NULL DEFAULT 0;
