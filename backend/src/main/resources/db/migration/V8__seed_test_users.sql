-- V8: Seed test users for prototype authentication (T12B)
-- 4 sample accounts with BCrypt-hashed password 'Password@123' (BCrypt strength 12).
-- Hash value is interchangeable — verifying logic in AuthService uses BCryptPasswordEncoder.matches().

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
    ('it01@itam.example',   'IT Support 01',         '$2a$12$ap67CmIDlhWC9EQKLs1X4uUi7SRhvFd6C42Umidu2dgtkwv6IPVLO', 'IT_STAFF'),
    ('pur01@itam.example',  'Mua sắm 01',            '$2a$12$ap67CmIDlhWC9EQKLs1X4uUi7SRhvFd6C42Umidu2dgtkwv6IPVLO', 'PUR_STAFF'),
    ('user01@itam.example', 'Nguyễn Văn A',          '$2a$12$ap67CmIDlhWC9EQKLs1X4uUi7SRhvFd6C42Umidu2dgtkwv6IPVLO', 'USER')
) AS seed(email, full_name, password_hash, role_code)
JOIN roles r ON r.code = seed.role_code
ON CONFLICT (email) DO NOTHING;
