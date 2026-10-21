-- Keep the existing identity and every asset/allocation/history foreign key.
-- Legacy company codes must be supplied by the operator; never fabricate them.
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
ALTER TABLE users ADD COLUMN employee_code varchar(100);
ALTER TABLE users ADD COLUMN username varchar(255);
-- Before M7 staff login also read profile status. Preserve those pre-existing login locks.
UPDATE users u SET login_locked=true FROM roles r
WHERE u.role_id=r.role_id AND r.code<>'USER' AND u.account_status='LOCKED';
CREATE UNIQUE INDEX uq_users_employee_code_ci ON users(lower(employee_code));
CREATE UNIQUE INDEX uq_users_email_ci ON users(lower(email));
CREATE UNIQUE INDEX uq_users_username_ci ON users(lower(username));
ALTER TABLE users ADD CONSTRAINT chk_users_employee_code_not_blank CHECK (employee_code IS NULL OR length(trim(employee_code)) > 0);
ALTER TABLE users ADD CONSTRAINT chk_users_username_not_blank CHECK (username IS NULL OR length(trim(username)) > 0);
