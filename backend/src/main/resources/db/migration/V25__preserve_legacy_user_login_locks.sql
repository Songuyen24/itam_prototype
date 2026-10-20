-- Preserve previously locked credentials before USER login stops reading recipient status.
UPDATE users SET login_locked = true
WHERE account_status = 'LOCKED' AND role_id IN (SELECT role_id FROM roles WHERE code = 'USER');
