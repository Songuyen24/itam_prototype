-- Access locks must not alter the recipient state used by handover/recovery.
ALTER TABLE users ADD COLUMN login_locked boolean NOT NULL DEFAULT false;
