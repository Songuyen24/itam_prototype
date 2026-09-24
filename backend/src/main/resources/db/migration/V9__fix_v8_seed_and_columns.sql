-- V9: Fix V8 seed - ensure role codes IT_STAFF/PUR_STAFF exist, password_hash/account_status columns exist,
--     then upsert the 4 test users. Safe to re-run.

-- 1) Ensure required role codes exist (V5 only seeded ADMIN/IT/PUR/USER)
INSERT INTO roles (code, name, is_active) VALUES
    ('IT_STAFF', 'IT Staff',         TRUE),
    ('PUR_STAFF','Purchasing Staff', TRUE)
ON CONFLICT (code) DO NOTHING;

-- Promote legacy short codes if they exist (rename IT -> IT_STAFF, PUR -> PUR_STAFF).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM roles WHERE code = 'IT')
       AND NOT EXISTS (SELECT 1 FROM roles WHERE code = 'IT_STAFF') THEN
        UPDATE roles SET code = 'IT_STAFF' WHERE code = 'IT';
    END IF;
    IF EXISTS (SELECT 1 FROM roles WHERE code = 'PUR')
       AND NOT EXISTS (SELECT 1 FROM roles WHERE code = 'PUR_STAFF') THEN
        UPDATE roles SET code = 'PUR_STAFF' WHERE code = 'PUR';
    END IF;
END $$;

-- 2) Ensure users.password_hash / account_status exist (V1 created users without them)
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

-- 3) Upsert 4 test users (BCrypt cost 12 of 'Password@123').
--    IMPORTANT: INSERT ... SELECT ... ON CONFLICT DO UPDATE uses EXCLUDED.* to refer to
--    the row that would have been inserted. EXCLUDED only exposes target-table columns,
--    so we select role_id (not role_code) and use EXCLUDED.role_id for the update.
INSERT INTO users (email, full_name, password_hash, role_id, department_id, account_status)
SELECT
    seed.email,
    seed.full_name,
    seed.password_hash,
    r.role_id,
    NULL,
    'ACTIVE'
FROM (VALUES
    ('admin@itam.example',  'Quản trị viên hệ thống', '$2a$12$ap67CmIDlhWC9EQKLs1X4uUi7SRhvFd6C42Umidu2dgtkwv6IPVLO', 'ADMIN'),
    ('it01@itam.example',   'IT Support 01',          '$2a$12$ap67CmIDlhWC9EQKLs1X4uUi7SRhvFd6C42Umidu2dgtkwv6IPVLO', 'IT_STAFF'),
    ('pur01@itam.example',  'Mua sắm 01',            '$2a$12$ap67CmIDlhWC9EQKLs1X4uUi7SRhvFd6C42Umidu2dgtkwv6IPVLO', 'PUR_STAFF'),
    ('user01@itam.example', 'Nguyễn Văn A',           '$2a$12$ap67CmIDlhWC9EQKLs1X4uUi7SRhvFd6C42Umidu2dgtkwv6IPVLO', 'USER')
) AS seed(email, full_name, password_hash, role_code)
JOIN roles r ON r.code = seed.role_code
ON CONFLICT (email) DO UPDATE
   SET password_hash = EXCLUDED.password_hash,
       account_status = 'ACTIVE',
       full_name      = EXCLUDED.full_name,
       role_id        = EXCLUDED.role_id;
