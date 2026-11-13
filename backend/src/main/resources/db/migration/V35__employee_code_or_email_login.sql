DO $$
DECLARE invalid_id bigint;
BEGIN
    SELECT user_id INTO invalid_id FROM users
    WHERE password_hash IS NOT NULL
      AND nullif(trim(employee_code), '') IS NULL
      AND nullif(trim(email), '') IS NULL
    LIMIT 1;
    IF invalid_id IS NOT NULL THEN
        RAISE EXCEPTION 'Cannot remove username: credential account user_id % needs a real employee_code or email', invalid_id;
    END IF;

    SELECT code_user.user_id INTO invalid_id
    FROM users code_user JOIN users email_user
      ON code_user.user_id <> email_user.user_id
     AND lower(code_user.employee_code) = lower(email_user.email)
    WHERE code_user.employee_code IS NOT NULL AND email_user.email IS NOT NULL
    LIMIT 1;
    IF invalid_id IS NOT NULL THEN
        RAISE EXCEPTION 'Cannot enable employee code/email login: employee_code of user_id % matches another user email', invalid_id;
    END IF;
END $$;

ALTER TABLE users DROP CONSTRAINT chk_users_username_not_blank;
DROP INDEX uq_users_username_ci;
ALTER TABLE users DROP COLUMN username;
