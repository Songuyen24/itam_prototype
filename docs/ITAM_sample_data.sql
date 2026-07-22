-- ============================================================================
-- ITAM PROTOTYPE - SAMPLE DATA FOR POSTGRESQL
-- Version: 1.0
-- Date: 2026-07-21
--
-- Mục đích:
--   Tạo dữ liệu mẫu cho thiết kế database ITAM gồm 24 bảng.
--   File có thể chạy lại; các bản ghi có mã duy nhất sẽ được cập nhật hoặc bỏ qua.
--
-- Yêu cầu:
--   1. Chạy migration tạo schema trước khi chạy file này.
--   2. Tên bảng/cột phải khớp tài liệu ITAM_Thiet_ke_co_so_du_lieu.docx.
--   3. Nếu dùng Flyway, đổi tên file theo version tiếp theo của dự án, ví dụ:
--      V5__seed_sample_data.sql
--
-- Quyết định nghiệp vụ:
--   - Không có role FIN.
--   - PUR tạo phiếu nhập kho, IT xác nhận hoặc từ chối.
--   - Admin duyệt hoặc từ chối thanh lý.
--   - Chưa triển khai QR Code.
-- ============================================================================

BEGIN;

SET LOCAL TIME ZONE 'Asia/Ho_Chi_Minh';

-- ============================================================================
-- 1. ROLE
-- ============================================================================

INSERT INTO roles (code, name, is_active)
VALUES
    ('ADMIN', 'Quản trị viên', TRUE),
    ('IT',    'Nhân viên IT', TRUE),
    ('PUR',   'Nhân viên mua hàng', TRUE),
    ('USER',  'Người sử dụng', TRUE)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    is_active = EXCLUDED.is_active;

-- ============================================================================
-- 2. PHÒNG BAN
-- ============================================================================

INSERT INTO departments (code, name, is_active, created_at, updated_at)
VALUES
    ('IT',    'Phòng Công nghệ thông tin', TRUE, TIMESTAMPTZ '2026-07-01 08:00:00+07', NULL),
    ('PUR',   'Phòng Mua hàng',            TRUE, TIMESTAMPTZ '2026-07-01 08:00:00+07', NULL),
    ('ACC',   'Phòng Kế toán',             TRUE, TIMESTAMPTZ '2026-07-01 08:00:00+07', NULL),
    ('HR',    'Phòng Nhân sự',             TRUE, TIMESTAMPTZ '2026-07-01 08:00:00+07', NULL),
    ('SALES', 'Phòng Kinh doanh',          TRUE, TIMESTAMPTZ '2026-07-01 08:00:00+07', NULL)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    is_active = EXCLUDED.is_active,
    updated_at = TIMESTAMPTZ '2026-07-21 09:00:00+07';

-- ============================================================================
-- 3. NGƯỜI DÙNG
-- password_hash để NULL vì authentication chưa nằm trong phạm vi seed này.
-- ============================================================================

INSERT INTO users (
    email,
    full_name,
    password_hash,
    role_id,
    department_id,
    is_active,
    created_at,
    updated_at
)
SELECT
    seed.email,
    seed.full_name,
    NULL,
    role.role_id,
    department.department_id,
    TRUE,
    TIMESTAMPTZ '2026-07-01 08:15:00+07',
    NULL
FROM (
    VALUES
        ('admin@itam.local',     'Quản trị hệ thống',  'ADMIN', 'IT'),
        ('it.staff@itam.local',  'Trần Minh Tuấn',     'IT',    'IT'),
        ('pur.staff@itam.local', 'Nguyễn Thu Hương',   'PUR',   'PUR'),
        ('an.nguyen@itam.local', 'Nguyễn Hoàng An',    'USER',  'SALES'),
        ('linh.tran@itam.local', 'Trần Ngọc Linh',     'USER',  'ACC'),
        ('khoa.le@itam.local',   'Lê Minh Khoa',       'USER',  'HR')
) AS seed(email, full_name, role_code, department_code)
JOIN roles role
    ON role.code = seed.role_code
LEFT JOIN departments department
    ON department.code = seed.department_code
ON CONFLICT (email) DO UPDATE
SET full_name = EXCLUDED.full_name,
    role_id = EXCLUDED.role_id,
    department_id = EXCLUDED.department_id,
    is_active = EXCLUDED.is_active,
    updated_at = TIMESTAMPTZ '2026-07-21 09:00:00+07';

-- ============================================================================
-- 4. VỊ TRÍ
-- ============================================================================

INSERT INTO locations (code, name, address, is_active, created_at, updated_at)
VALUES
    ('HCM-WH', 'Kho IT TP.HCM',       'Tầng 1 - Văn phòng TP.HCM', TRUE, TIMESTAMPTZ '2026-07-01 08:30:00+07', NULL),
    ('HCM-F2', 'Văn phòng tầng 2',    'Tầng 2 - Văn phòng TP.HCM', TRUE, TIMESTAMPTZ '2026-07-01 08:30:00+07', NULL),
    ('HCM-F3', 'Văn phòng tầng 3',    'Tầng 3 - Văn phòng TP.HCM', TRUE, TIMESTAMPTZ '2026-07-01 08:30:00+07', NULL),
    ('HCM-DC', 'Phòng máy chủ TP.HCM','Tầng 1 - Khu vực hạn chế',  TRUE, TIMESTAMPTZ '2026-07-01 08:30:00+07', NULL),
    ('HN-WH',  'Kho IT Hà Nội',       'Văn phòng chi nhánh Hà Nội', TRUE, TIMESTAMPTZ '2026-07-01 08:30:00+07', NULL)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    address = EXCLUDED.address,
    is_active = EXCLUDED.is_active,
    updated_at = TIMESTAMPTZ '2026-07-21 09:00:00+07';

-- ============================================================================
-- 5. NHÀ CUNG CẤP VÀ LIÊN HỆ
-- ============================================================================

INSERT INTO suppliers (
    code, name, tax_code, address, phone, email, is_active, created_at, updated_at
)
VALUES
    (
        'FPT-TRADING',
        'Công ty TNHH Phân phối FPT',
        '0101778163',
        'TP. Hồ Chí Minh',
        '02873002345',
        'business@fpt.com',
        TRUE,
        TIMESTAMPTZ '2026-07-01 09:00:00+07',
        NULL
    ),
    (
        'PHONGVU',
        'Công ty Cổ phần Thương mại Dịch vụ Phong Vũ',
        '0304998358',
        'TP. Hồ Chí Minh',
        '18006867',
        'sales@phongvu.vn',
        TRUE,
        TIMESTAMPTZ '2026-07-01 09:00:00+07',
        NULL
    ),
    (
        'DELL-VN',
        'Dell Technologies Việt Nam',
        NULL,
        'TP. Hồ Chí Minh',
        '02839101111',
        'support@dell.com',
        TRUE,
        TIMESTAMPTZ '2026-07-01 09:00:00+07',
        NULL
    )
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    tax_code = EXCLUDED.tax_code,
    address = EXCLUDED.address,
    phone = EXCLUDED.phone,
    email = EXCLUDED.email,
    is_active = EXCLUDED.is_active,
    updated_at = TIMESTAMPTZ '2026-07-21 09:00:00+07';

INSERT INTO supplier_contacts (supplier_id, name, position, phone, email)
SELECT supplier.supplier_id, seed.name, seed.position, seed.phone, seed.email
FROM (
    VALUES
        ('FPT-TRADING', 'Phạm Quốc Bảo', 'Nhân viên kinh doanh', '0901000001', 'bao.pham@example.com'),
        ('PHONGVU',     'Lê Thu Hà',      'Nhân viên kinh doanh', '0901000002', 'ha.le@example.com'),
        ('DELL-VN',     'Nguyễn Anh Đức', 'Chuyên viên hỗ trợ',   '0901000003', 'duc.nguyen@example.com')
) AS seed(supplier_code, name, position, phone, email)
JOIN suppliers supplier
    ON supplier.code = seed.supplier_code
WHERE NOT EXISTS (
    SELECT 1
    FROM supplier_contacts existing
    WHERE existing.supplier_id = supplier.supplier_id
      AND existing.email = seed.email
);

-- ============================================================================
-- 6. DANH MỤC TÀI SẢN
-- ============================================================================

INSERT INTO asset_categories (code, name, is_active)
VALUES
    ('HARDWARE',   'Phần cứng', TRUE),
    ('SOFTWARE',   'Phần mềm', TRUE),
    ('CONSUMABLE', 'Vật tư tiêu hao', TRUE)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    is_active = EXCLUDED.is_active;

INSERT INTO asset_types (code, type_name, is_active)
VALUES
    ('LAPTOP',    'Máy tính xách tay', TRUE),
    ('DESKTOP',   'Máy tính để bàn', TRUE),
    ('MONITOR',   'Màn hình', TRUE),
    ('SERVER',    'Máy chủ', TRUE),
    ('PRINTER',   'Máy in', TRUE),
    ('COMPONENT', 'Linh kiện', TRUE),
    ('SOFTWARE',  'Phần mềm và license', TRUE)
ON CONFLICT (code) DO UPDATE
SET type_name = EXCLUDED.type_name,
    is_active = EXCLUDED.is_active;

INSERT INTO asset_statuses (code, name, is_active)
VALUES
    ('IN_STOCK',  'Trong kho', TRUE),
    ('IN_USE',    'Đang sử dụng', TRUE),
    ('IN_REPAIR', 'Đang sửa chữa', TRUE),
    ('DAMAGED',   'Hư hỏng', TRUE),
    ('RETIRED',   'Đã thanh lý', TRUE)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    is_active = EXCLUDED.is_active;

INSERT INTO asset_conditions (code, name, is_active)
VALUES
    ('NEW',     'Mới', TRUE),
    ('USED',    'Đã qua sử dụng', TRUE),
    ('DAMAGED', 'Hư hỏng', TRUE)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    is_active = EXCLUDED.is_active;

-- ============================================================================
-- 7. MODEL
-- ============================================================================

INSERT INTO models (
    name,
    brand,
    type_id,
    default_cpu,
    default_ram,
    default_storage,
    default_graphics_card,
    is_active,
    created_at,
    updated_at
)
SELECT
    seed.name,
    seed.brand,
    asset_type.type_id,
    seed.default_cpu,
    seed.default_ram,
    seed.default_storage,
    seed.default_graphics_card,
    TRUE,
    TIMESTAMPTZ '2026-07-01 09:30:00+07',
    NULL
FROM (
    VALUES
        ('Latitude 5450',      'Dell',    'LAPTOP',    'Intel Core Ultra 5 125U', '16 GB',  '512 GB SSD', 'Intel Graphics'),
        ('ThinkPad T14 Gen 5', 'Lenovo',  'LAPTOP',    'Intel Core Ultra 5 125U', '16 GB',  '512 GB SSD', 'Intel Graphics'),
        ('24MP400-B',          'LG',      'MONITOR',   NULL,                       NULL,     NULL,         NULL),
        ('PowerEdge R760',     'Dell',    'SERVER',    '2 x Intel Xeon Silver',   '128 GB', '4 TB SSD',   NULL),
        ('LaserJet Pro 4003dn','HP',      'PRINTER',   NULL,                       NULL,     NULL,         NULL),
        ('ProDesk 400 G6',     'HP',      'DESKTOP',   'Intel Core i5-10500',     '8 GB',   '256 GB SSD', 'Intel UHD 630'),
        ('KVR32N22S8/16',      'Kingston','COMPONENT', NULL,                       '16 GB',  NULL,         NULL)
) AS seed(
    name,
    brand,
    type_code,
    default_cpu,
    default_ram,
    default_storage,
    default_graphics_card
)
JOIN asset_types asset_type
    ON asset_type.code = seed.type_code
WHERE NOT EXISTS (
    SELECT 1
    FROM models existing
    WHERE existing.name = seed.name
      AND existing.brand = seed.brand
      AND existing.type_id = asset_type.type_id
);

-- ============================================================================
-- 8. DANH MỤC PHẦN MỀM VÀ LOẠI LICENSE
-- ============================================================================

INSERT INTO software_catalog (name, manufacturer, version, is_active)
SELECT seed.name, seed.manufacturer, seed.version, TRUE
FROM (
    VALUES
        ('Microsoft 365 Business Standard', 'Microsoft', '2026'),
        ('Windows 11 Pro',                   'Microsoft', '23H2'),
        ('Adobe Acrobat Pro',                'Adobe',     '2026')
) AS seed(name, manufacturer, version)
WHERE NOT EXISTS (
    SELECT 1
    FROM software_catalog existing
    WHERE existing.name = seed.name
      AND existing.version = seed.version
);

INSERT INTO software_license_types (code, name, is_active)
VALUES
    ('PER_USER',  'Theo người dùng', TRUE),
    ('OEM',       'Bản quyền OEM', TRUE),
    ('PERPETUAL', 'Bản quyền vĩnh viễn', TRUE)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    is_active = EXCLUDED.is_active;

-- ============================================================================
-- 9. TÀI SẢN
-- ============================================================================

INSERT INTO assets (
    asset_tag,
    name,
    category_id,
    status_id,
    assigned_to,
    department_id,
    location_id,
    supplier_id,
    po_number,
    purchase_date,
    purchase_cost,
    created_by,
    created_at,
    updated_by,
    updated_at
)
SELECT
    seed.asset_tag,
    seed.asset_name,
    category.category_id,
    status.status_id,
    assigned_user.user_id,
    department.department_id,
    location.location_id,
    supplier.supplier_id,
    seed.po_number,
    seed.purchase_date,
    seed.purchase_cost,
    creator.user_id,
    TIMESTAMPTZ '2026-07-02 09:00:00+07',
    creator.user_id,
    TIMESTAMPTZ '2026-07-15 16:00:00+07'
FROM (
    VALUES
        ('ITAM-LT-0001', 'Laptop Dell Latitude 5450',       'HARDWARE', 'IN_USE',  'an.nguyen@itam.local', 'SALES', 'HCM-F2', 'DELL-VN',     'PO-2026-001', DATE '2026-01-10', 28500000.00),
        ('ITAM-LT-0002', 'Laptop Lenovo ThinkPad T14 Gen 5','HARDWARE', 'IN_STOCK', NULL,                   NULL,    'HCM-WH', 'FPT-TRADING', 'PO-2026-002', DATE '2026-02-12', 31500000.00),
        ('ITAM-MN-0001', 'Màn hình LG 24 inch',              'HARDWARE', 'IN_USE',  'an.nguyen@itam.local', 'SALES', 'HCM-F2', 'PHONGVU',     'PO-2026-003', DATE '2026-01-10',  3200000.00),
        ('ITAM-SV-0001', 'Máy chủ Dell PowerEdge R760',      'HARDWARE', 'IN_USE',  NULL,                   'IT',    'HCM-DC', 'DELL-VN',     'PO-2025-044', DATE '2025-11-20', 245000000.00),
        ('ITAM-PR-0001', 'Máy in HP LaserJet Pro 4003dn',    'HARDWARE', 'DAMAGED', NULL,                   'ACC',   'HCM-F3', 'FPT-TRADING', 'PO-2024-018', DATE '2024-06-15',  9200000.00),
        ('ITAM-PC-0001', 'Máy tính HP ProDesk cũ',           'HARDWARE', 'RETIRED', NULL,                   NULL,    'HCM-WH', 'PHONGVU',     'PO-2020-011', DATE '2020-03-05', 16500000.00),
        ('ITAM-RAM-0001','RAM Kingston 16 GB',                'HARDWARE', 'IN_USE',  NULL,                   'SALES', 'HCM-F2', 'PHONGVU',     'PO-2026-004', DATE '2026-01-10',  1350000.00),
        ('ITAM-SW-0001', 'Microsoft 365 - Nguyễn Hoàng An',  'SOFTWARE', 'IN_USE',  'an.nguyen@itam.local', 'SALES', 'HCM-F2', 'FPT-TRADING', 'PO-2026-005', DATE '2026-01-10',  3600000.00),
        ('ITAM-SW-0002', 'Windows 11 Pro OEM - LT-0001',     'SOFTWARE', 'IN_USE',  'an.nguyen@itam.local', 'SALES', 'HCM-F2', 'DELL-VN',     'PO-2026-001', DATE '2026-01-10',  3200000.00)
) AS seed(
    asset_tag,
    asset_name,
    category_code,
    status_code,
    assigned_email,
    department_code,
    location_code,
    supplier_code,
    po_number,
    purchase_date,
    purchase_cost
)
JOIN asset_categories category
    ON category.code = seed.category_code
JOIN asset_statuses status
    ON status.code = seed.status_code
LEFT JOIN users assigned_user
    ON assigned_user.email = seed.assigned_email
LEFT JOIN departments department
    ON department.code = seed.department_code
LEFT JOIN locations location
    ON location.code = seed.location_code
LEFT JOIN suppliers supplier
    ON supplier.code = seed.supplier_code
JOIN users creator
    ON creator.email = 'it.staff@itam.local'
ON CONFLICT (asset_tag) DO UPDATE
SET name = EXCLUDED.name,
    category_id = EXCLUDED.category_id,
    status_id = EXCLUDED.status_id,
    assigned_to = EXCLUDED.assigned_to,
    department_id = EXCLUDED.department_id,
    location_id = EXCLUDED.location_id,
    supplier_id = EXCLUDED.supplier_id,
    po_number = EXCLUDED.po_number,
    purchase_date = EXCLUDED.purchase_date,
    purchase_cost = EXCLUDED.purchase_cost,
    updated_by = EXCLUDED.updated_by,
    updated_at = EXCLUDED.updated_at;

-- ============================================================================
-- 10. CHI TIẾT PHẦN CỨNG
-- ============================================================================

INSERT INTO asset_hardware_details (
    asset_id,
    serial_number,
    model_id,
    warranty_expiration,
    condition_id,
    actual_cpu,
    actual_ram,
    actual_storage,
    actual_graphics_card
)
SELECT
    asset.asset_id,
    seed.serial_number,
    model.model_id,
    seed.warranty_expiration,
    condition.condition_id,
    seed.actual_cpu,
    seed.actual_ram,
    seed.actual_storage,
    seed.actual_graphics_card
FROM (
    VALUES
        ('ITAM-LT-0001',  'DL5450-SN-0001',  'Latitude 5450',       'Dell',     DATE '2029-01-10', 'NEW',     NULL,                      '32 GB', NULL, NULL),
        ('ITAM-LT-0002',  'LNT14-SN-0002',   'ThinkPad T14 Gen 5', 'Lenovo',   DATE '2029-02-12', 'NEW',     NULL,                      NULL,    NULL, NULL),
        ('ITAM-MN-0001',  'LG24-SN-0001',    '24MP400-B',          'LG',       DATE '2028-01-10', 'NEW',     NULL,                      NULL,    NULL, NULL),
        ('ITAM-SV-0001',  'DR760-SN-0001',   'PowerEdge R760',     'Dell',     DATE '2030-11-20', 'USED',    NULL,                      NULL,    '8 TB SSD', NULL),
        ('ITAM-PR-0001',  'HP4003-SN-0001',  'LaserJet Pro 4003dn','HP',       DATE '2026-06-15', 'DAMAGED', NULL,                      NULL,    NULL, NULL),
        ('ITAM-PC-0001',  'HP400G6-SN-0001', 'ProDesk 400 G6',     'HP',       DATE '2023-03-05', 'DAMAGED', NULL,                      NULL,    NULL, NULL),
        ('ITAM-RAM-0001', 'KVR16-SN-0001',   'KVR32N22S8/16',      'Kingston', NULL,              'NEW',     NULL,                      '16 GB', NULL, NULL)
) AS seed(
    asset_tag,
    serial_number,
    model_name,
    model_brand,
    warranty_expiration,
    condition_code,
    actual_cpu,
    actual_ram,
    actual_storage,
    actual_graphics_card
)
JOIN assets asset
    ON asset.asset_tag = seed.asset_tag
JOIN models model
    ON model.name = seed.model_name
   AND model.brand = seed.model_brand
LEFT JOIN asset_conditions condition
    ON condition.code = seed.condition_code
ON CONFLICT (asset_id) DO UPDATE
SET serial_number = EXCLUDED.serial_number,
    model_id = EXCLUDED.model_id,
    warranty_expiration = EXCLUDED.warranty_expiration,
    condition_id = EXCLUDED.condition_id,
    actual_cpu = EXCLUDED.actual_cpu,
    actual_ram = EXCLUDED.actual_ram,
    actual_storage = EXCLUDED.actual_storage,
    actual_graphics_card = EXCLUDED.actual_graphics_card;

-- ============================================================================
-- 11. CHI TIẾT PHẦN MỀM
-- ============================================================================

INSERT INTO asset_software_details (
    asset_id,
    software_catalog_id,
    license_key,
    license_type_id,
    seat_count,
    expiry_date
)
SELECT
    asset.asset_id,
    software.software_catalog_id,
    seed.license_key,
    license_type.license_type_id,
    seed.seat_count,
    seed.expiry_date
FROM (
    VALUES
        (
            'ITAM-SW-0001',
            'Microsoft 365 Business Standard',
            '2026',
            'DEMO-M365-USER-0001',
            'PER_USER',
            1,
            DATE '2027-01-09'
        ),
        (
            'ITAM-SW-0002',
            'Windows 11 Pro',
            '23H2',
            'DEMO-WIN11-OEM-0001',
            'OEM',
            1,
            NULL
        )
) AS seed(
    asset_tag,
    software_name,
    software_version,
    license_key,
    license_type_code,
    seat_count,
    expiry_date
)
JOIN assets asset
    ON asset.asset_tag = seed.asset_tag
JOIN software_catalog software
    ON software.name = seed.software_name
   AND software.version = seed.software_version
JOIN software_license_types license_type
    ON license_type.code = seed.license_type_code
ON CONFLICT (asset_id) DO UPDATE
SET software_catalog_id = EXCLUDED.software_catalog_id,
    license_key = EXCLUDED.license_key,
    license_type_id = EXCLUDED.license_type_id,
    seat_count = EXCLUDED.seat_count,
    expiry_date = EXCLUDED.expiry_date;

-- ============================================================================
-- 12. QUAN HỆ TÀI SẢN
-- parent_asset_id là tài sản chứa/thiết bị chủ.
-- child_asset_id là linh kiện hoặc phần mềm liên quan.
-- ============================================================================

INSERT INTO asset_relationships (
    parent_asset_id,
    child_asset_id,
    relationship_type,
    created_by,
    created_at
)
SELECT
    parent_asset.asset_id,
    child_asset.asset_id,
    seed.relationship_type,
    creator.user_id,
    TIMESTAMPTZ '2026-07-03 10:00:00+07'
FROM (
    VALUES
        ('ITAM-LT-0001', 'ITAM-RAM-0001', 'COMPONENT_OF'),
        ('ITAM-LT-0001', 'ITAM-SW-0001',  'INSTALLED_ON'),
        ('ITAM-LT-0001', 'ITAM-SW-0002',  'INSTALLED_ON')
) AS seed(parent_asset_tag, child_asset_tag, relationship_type)
JOIN assets parent_asset
    ON parent_asset.asset_tag = seed.parent_asset_tag
JOIN assets child_asset
    ON child_asset.asset_tag = seed.child_asset_tag
JOIN users creator
    ON creator.email = 'it.staff@itam.local'
ON CONFLICT (parent_asset_id, child_asset_id, relationship_type) DO NOTHING;

-- ============================================================================
-- 13. PHIẾU NGHIỆP VỤ
-- ============================================================================

INSERT INTO transactions (
    transaction_code,
    type,
    status,
    requester_id,
    related_user_id,
    notes,
    processed_by,
    processed_at,
    rejection_reason,
    created_at,
    completed_at
)
SELECT
    seed.transaction_code,
    seed.transaction_type,
    seed.transaction_status,
    requester.user_id,
    related_user.user_id,
    seed.notes,
    processor.user_id,
    seed.processed_at,
    seed.rejection_reason,
    seed.created_at,
    seed.completed_at
FROM (
    VALUES
        (
            'PNK-2026-0001',
            'IMPORT',
            'COMPLETED',
            'pur.staff@itam.local',
            NULL,
            'Nhập lô thiết bị văn phòng tháng 01/2026.',
            'it.staff@itam.local',
            TIMESTAMPTZ '2026-01-11 10:30:00+07',
            NULL,
            TIMESTAMPTZ '2026-01-10 09:00:00+07',
            TIMESTAMPTZ '2026-01-11 10:30:00+07'
        ),
        (
            'PBG-2026-0001',
            'HANDOVER',
            'COMPLETED',
            'it.staff@itam.local',
            'an.nguyen@itam.local',
            'Bàn giao bộ thiết bị cho nhân viên kinh doanh.',
            'it.staff@itam.local',
            TIMESTAMPTZ '2026-01-12 14:00:00+07',
            NULL,
            TIMESTAMPTZ '2026-01-12 09:00:00+07',
            TIMESTAMPTZ '2026-01-12 14:00:00+07'
        ),
        (
            'PTH-2026-0001',
            'RECOVERY',
            'COMPLETED',
            'it.staff@itam.local',
            'khoa.le@itam.local',
            'Thu hồi laptop do đổi thiết bị.',
            'it.staff@itam.local',
            TIMESTAMPTZ '2026-06-20 15:30:00+07',
            NULL,
            TIMESTAMPTZ '2026-06-20 09:00:00+07',
            TIMESTAMPTZ '2026-06-20 15:30:00+07'
        ),
        (
            'PTL-2026-0001',
            'DISPOSAL',
            'PENDING',
            'it.staff@itam.local',
            NULL,
            'Đề nghị thanh lý máy in hư hỏng, chi phí sửa chữa cao.',
            NULL,
            NULL,
            NULL,
            TIMESTAMPTZ '2026-07-15 09:15:00+07',
            NULL
        ),
        (
            'PTL-2026-0002',
            'DISPOSAL',
            'COMPLETED',
            'it.staff@itam.local',
            NULL,
            'Thanh lý máy tính để bàn cũ đã hết khấu hao.',
            'admin@itam.local',
            TIMESTAMPTZ '2026-07-10 16:00:00+07',
            NULL,
            TIMESTAMPTZ '2026-07-09 09:30:00+07',
            TIMESTAMPTZ '2026-07-10 16:00:00+07'
        )
) AS seed(
    transaction_code,
    transaction_type,
    transaction_status,
    requester_email,
    related_user_email,
    notes,
    processor_email,
    processed_at,
    rejection_reason,
    created_at,
    completed_at
)
JOIN users requester
    ON requester.email = seed.requester_email
LEFT JOIN users related_user
    ON related_user.email = seed.related_user_email
LEFT JOIN users processor
    ON processor.email = seed.processor_email
ON CONFLICT (transaction_code) DO UPDATE
SET type = EXCLUDED.type,
    status = EXCLUDED.status,
    requester_id = EXCLUDED.requester_id,
    related_user_id = EXCLUDED.related_user_id,
    notes = EXCLUDED.notes,
    processed_by = EXCLUDED.processed_by,
    processed_at = EXCLUDED.processed_at,
    rejection_reason = EXCLUDED.rejection_reason,
    completed_at = EXCLUDED.completed_at;

-- ============================================================================
-- 14. TÀI SẢN THUỘC PHIẾU
-- ============================================================================

INSERT INTO transaction_assets (transaction_id, asset_id, draft_data, notes)
SELECT txn.transaction_id, asset.asset_id, NULL, seed.notes
FROM (
    VALUES
        ('PNK-2026-0001', 'ITAM-LT-0001', 'Tài sản thuộc lô nhập tháng 01/2026.'),
        ('PNK-2026-0001', 'ITAM-LT-0002', 'Tài sản thuộc lô nhập tháng 01/2026.'),
        ('PNK-2026-0001', 'ITAM-MN-0001', 'Tài sản thuộc lô nhập tháng 01/2026.'),
        ('PBG-2026-0001', 'ITAM-LT-0001', 'Bàn giao laptop.'),
        ('PBG-2026-0001', 'ITAM-MN-0001', 'Bàn giao màn hình.'),
        ('PBG-2026-0001', 'ITAM-SW-0001', 'Gán license Microsoft 365.'),
        ('PBG-2026-0001', 'ITAM-SW-0002', 'License OEM theo laptop.'),
        ('PTH-2026-0001', 'ITAM-LT-0002', 'Thu hồi và trả về kho IT.'),
        ('PTL-2026-0001', 'ITAM-PR-0001', 'Tài sản đang chờ Admin xử lý.'),
        ('PTL-2026-0002', 'ITAM-PC-0001', 'Tài sản đã được Admin duyệt thanh lý.')
) AS seed(transaction_code, asset_tag, notes)
JOIN transactions txn
    ON txn.transaction_code = seed.transaction_code
JOIN assets asset
    ON asset.asset_tag = seed.asset_tag
WHERE NOT EXISTS (
    SELECT 1
    FROM transaction_assets existing
    WHERE existing.transaction_id = txn.transaction_id
      AND existing.asset_id = asset.asset_id
);

-- ============================================================================
-- 15. HỒ SƠ VÀ CHỨNG TỪ
-- Đây chỉ là metadata mẫu; file vật lý không được tạo bởi script này.
-- ============================================================================

INSERT INTO documents (
    transaction_id,
    asset_id,
    document_type,
    original_file_name,
    stored_file_name,
    storage_path,
    mime_type,
    file_size,
    checksum,
    uploaded_by,
    created_at
)
SELECT
    txn.transaction_id,
    asset.asset_id,
    seed.document_type,
    seed.original_file_name,
    seed.stored_file_name,
    seed.storage_path,
    seed.mime_type,
    seed.file_size,
    NULL,
    uploader.user_id,
    seed.created_at
FROM (
    VALUES
        (
            'PNK-2026-0001',
            NULL,
            'INVOICE_NCC',
            'hoa-don-thiet-bi-01-2026.pdf',
            'invoice_pnk_2026_0001.pdf',
            '/data/itam/documents/2026/01/invoice_pnk_2026_0001.pdf',
            'application/pdf',
            245760::BIGINT,
            'pur.staff@itam.local',
            TIMESTAMPTZ '2026-01-10 09:30:00+07'
        ),
        (
            'PBG-2026-0001',
            'ITAM-LT-0001',
            'HANDOVER_PDF',
            'bien-ban-ban-giao-PBG-2026-0001.pdf',
            'handover_pbg_2026_0001.pdf',
            '/data/itam/documents/2026/01/handover_pbg_2026_0001.pdf',
            'application/pdf',
            184320::BIGINT,
            'it.staff@itam.local',
            TIMESTAMPTZ '2026-01-12 14:05:00+07'
        ),
        (
            'PTH-2026-0001',
            'ITAM-LT-0002',
            'RECOVERY_PDF',
            'bien-ban-thu-hoi-PTH-2026-0001.pdf',
            'recovery_pth_2026_0001.pdf',
            '/data/itam/documents/2026/06/recovery_pth_2026_0001.pdf',
            'application/pdf',
            176128::BIGINT,
            'it.staff@itam.local',
            TIMESTAMPTZ '2026-06-20 15:35:00+07'
        ),
        (
            'PTL-2026-0002',
            'ITAM-PC-0001',
            'DISPOSAL_PDF',
            'bien-ban-thanh-ly-PTL-2026-0002.pdf',
            'disposal_ptl_2026_0002.pdf',
            '/data/itam/documents/2026/07/disposal_ptl_2026_0002.pdf',
            'application/pdf',
            196608::BIGINT,
            'admin@itam.local',
            TIMESTAMPTZ '2026-07-10 16:05:00+07'
        )
) AS seed(
    transaction_code,
    asset_tag,
    document_type,
    original_file_name,
    stored_file_name,
    storage_path,
    mime_type,
    file_size,
    uploader_email,
    created_at
)
LEFT JOIN transactions txn
    ON txn.transaction_code = seed.transaction_code
LEFT JOIN assets asset
    ON asset.asset_tag = seed.asset_tag
JOIN users uploader
    ON uploader.email = seed.uploader_email
WHERE NOT EXISTS (
    SELECT 1
    FROM documents existing
    WHERE existing.stored_file_name = seed.stored_file_name
);

-- ============================================================================
-- 16. LỊCH SỬ TÀI SẢN
-- ============================================================================

INSERT INTO asset_history (
    asset_id,
    transaction_id,
    actor_user_id,
    action,
    old_data,
    new_data,
    details,
    created_at
)
SELECT
    asset.asset_id,
    txn.transaction_id,
    actor.user_id,
    seed.action,
    seed.old_data,
    seed.new_data,
    seed.details,
    seed.created_at
FROM (
    VALUES
        (
            'ITAM-LT-0001',
            'PNK-2026-0001',
            'it.staff@itam.local',
            'CREATE',
            NULL::JSONB,
            '{"status":"IN_STOCK","location":"HCM-WH"}'::JSONB,
            'Tạo tài sản sau khi IT xác nhận nhập kho.',
            TIMESTAMPTZ '2026-01-11 10:30:00+07'
        ),
        (
            'ITAM-LT-0001',
            'PBG-2026-0001',
            'it.staff@itam.local',
            'HANDOVER',
            '{"status":"IN_STOCK","assignedTo":null,"location":"HCM-WH"}'::JSONB,
            '{"status":"IN_USE","assignedTo":"an.nguyen@itam.local","location":"HCM-F2"}'::JSONB,
            'Bàn giao laptop cho Nguyễn Hoàng An.',
            TIMESTAMPTZ '2026-01-12 14:00:00+07'
        ),
        (
            'ITAM-LT-0002',
            'PTH-2026-0001',
            'it.staff@itam.local',
            'RECOVERY',
            '{"status":"IN_USE","assignedTo":"khoa.le@itam.local","location":"HCM-F3"}'::JSONB,
            '{"status":"IN_STOCK","assignedTo":null,"location":"HCM-WH"}'::JSONB,
            'Thu hồi laptop và trả về kho.',
            TIMESTAMPTZ '2026-06-20 15:30:00+07'
        ),
        (
            'ITAM-PR-0001',
            'PTL-2026-0001',
            'it.staff@itam.local',
            'DISPOSAL_REQUEST',
            '{"status":"DAMAGED"}'::JSONB,
            '{"status":"DAMAGED","disposalStatus":"PENDING"}'::JSONB,
            'Tạo đề nghị thanh lý, chờ Admin xử lý.',
            TIMESTAMPTZ '2026-07-15 09:15:00+07'
        ),
        (
            'ITAM-PC-0001',
            'PTL-2026-0002',
            'admin@itam.local',
            'DISPOSAL',
            '{"status":"DAMAGED"}'::JSONB,
            '{"status":"RETIRED"}'::JSONB,
            'Admin duyệt thanh lý tài sản.',
            TIMESTAMPTZ '2026-07-10 16:00:00+07'
        )
) AS seed(
    asset_tag,
    transaction_code,
    actor_email,
    action,
    old_data,
    new_data,
    details,
    created_at
)
JOIN assets asset
    ON asset.asset_tag = seed.asset_tag
LEFT JOIN transactions txn
    ON txn.transaction_code = seed.transaction_code
JOIN users actor
    ON actor.email = seed.actor_email
WHERE NOT EXISTS (
    SELECT 1
    FROM asset_history existing
    WHERE existing.asset_id = asset.asset_id
      AND existing.action = seed.action
      AND COALESCE(existing.transaction_id, 0) = COALESCE(txn.transaction_id, 0)
);

-- ============================================================================
-- 17. IMPORT EXCEL
-- ============================================================================

INSERT INTO import_batches (
    file_name,
    status,
    total_rows,
    valid_rows,
    invalid_rows,
    imported_rows,
    uploaded_by,
    created_at,
    completed_at
)
SELECT
    'ITAM_import_demo_20260715.xlsx',
    'COMPLETED',
    3,
    2,
    1,
    2,
    uploader.user_id,
    TIMESTAMPTZ '2026-07-15 08:30:00+07',
    TIMESTAMPTZ '2026-07-15 08:35:00+07'
FROM users uploader
WHERE uploader.email = 'pur.staff@itam.local'
  AND NOT EXISTS (
      SELECT 1
      FROM import_batches existing
      WHERE existing.file_name = 'ITAM_import_demo_20260715.xlsx'
  );

INSERT INTO import_rows (
    import_batch_id,
    row_number,
    raw_data,
    validation_status,
    error_message,
    asset_id,
    created_at
)
SELECT
    batch.import_batch_id,
    seed.row_number,
    seed.raw_data,
    seed.validation_status,
    seed.error_message,
    asset.asset_id,
    TIMESTAMPTZ '2026-07-15 08:32:00+07'
FROM (
    VALUES
        (
            2,
            '{"assetTag":"ITAM-LT-0002","name":"Laptop Lenovo ThinkPad T14 Gen 5","serialNumber":"LNT14-SN-0002"}'::JSONB,
            'VALID',
            NULL,
            'ITAM-LT-0002'
        ),
        (
            3,
            '{"assetTag":"ITAM-MN-0001","name":"Màn hình LG 24 inch","serialNumber":"LG24-SN-0001"}'::JSONB,
            'VALID',
            NULL,
            'ITAM-MN-0001'
        ),
        (
            4,
            '{"assetTag":"ITAM-LT-0001","name":"Dòng bị trùng","serialNumber":"DL5450-SN-0001"}'::JSONB,
            'DUPLICATE',
            'Asset Tag và Serial Number đã tồn tại.',
            NULL
        )
) AS seed(
    row_number,
    raw_data,
    validation_status,
    error_message,
    asset_tag
)
JOIN import_batches batch
    ON batch.file_name = 'ITAM_import_demo_20260715.xlsx'
LEFT JOIN assets asset
    ON asset.asset_tag = seed.asset_tag
ON CONFLICT (import_batch_id, row_number) DO UPDATE
SET raw_data = EXCLUDED.raw_data,
    validation_status = EXCLUDED.validation_status,
    error_message = EXCLUDED.error_message,
    asset_id = EXCLUDED.asset_id;

-- ============================================================================
-- 18. EMAIL GIẢ LẬP
-- ============================================================================

INSERT INTO email_logs (
    transaction_id,
    recipient,
    subject,
    content,
    status,
    error_message,
    sent_at,
    created_at
)
SELECT
    txn.transaction_id,
    seed.recipient,
    seed.subject,
    seed.content,
    seed.status,
    NULL,
    seed.sent_at,
    seed.created_at
FROM (
    VALUES
        (
            'PBG-2026-0001',
            'an.nguyen@itam.local',
            'Thông báo bàn giao tài sản PBG-2026-0001',
            'Bạn đã được bàn giao laptop, màn hình và license phần mềm.',
            'SENT',
            TIMESTAMPTZ '2026-01-12 14:06:00+07',
            TIMESTAMPTZ '2026-01-12 14:05:30+07'
        ),
        (
            'PTH-2026-0001',
            'khoa.le@itam.local',
            'Thông báo thu hồi tài sản PTH-2026-0001',
            'Phiếu thu hồi tài sản đã hoàn tất.',
            'SENT',
            TIMESTAMPTZ '2026-06-20 15:36:00+07',
            TIMESTAMPTZ '2026-06-20 15:35:30+07'
        ),
        (
            'PTL-2026-0002',
            'pur.staff@itam.local',
            'Thông báo hoàn tất thanh lý PTL-2026-0002',
            'Admin đã duyệt thanh lý tài sản ITAM-PC-0001.',
            'SENT',
            TIMESTAMPTZ '2026-07-10 16:06:00+07',
            TIMESTAMPTZ '2026-07-10 16:05:30+07'
        )
) AS seed(
    transaction_code,
    recipient,
    subject,
    content,
    status,
    sent_at,
    created_at
)
JOIN transactions txn
    ON txn.transaction_code = seed.transaction_code
WHERE NOT EXISTS (
    SELECT 1
    FROM email_logs existing
    WHERE existing.transaction_id = txn.transaction_id
      AND existing.recipient = seed.recipient
      AND existing.subject = seed.subject
);

COMMIT;

-- ============================================================================
-- KIỂM TRA NHANH SAU KHI CHẠY
-- Các câu SELECT dưới đây chỉ đọc dữ liệu và không thay đổi database.
-- ============================================================================

SELECT 'roles' AS table_name, COUNT(*) AS row_count FROM roles
UNION ALL SELECT 'departments', COUNT(*) FROM departments
UNION ALL SELECT 'users', COUNT(*) FROM users
UNION ALL SELECT 'locations', COUNT(*) FROM locations
UNION ALL SELECT 'suppliers', COUNT(*) FROM suppliers
UNION ALL SELECT 'models', COUNT(*) FROM models
UNION ALL SELECT 'assets', COUNT(*) FROM assets
UNION ALL SELECT 'transactions', COUNT(*) FROM transactions
UNION ALL SELECT 'documents', COUNT(*) FROM documents
UNION ALL SELECT 'asset_history', COUNT(*) FROM asset_history
UNION ALL SELECT 'import_batches', COUNT(*) FROM import_batches
UNION ALL SELECT 'import_rows', COUNT(*) FROM import_rows
UNION ALL SELECT 'email_logs', COUNT(*) FROM email_logs
ORDER BY table_name;
