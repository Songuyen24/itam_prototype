-- V5: Seed data for catalog tables
-- Seeds: roles, asset_categories, asset_statuses, asset_conditions, software_license_types

-- ============================================
-- SEED: ROLES (only 4 roles, no FIN)
-- ============================================
INSERT INTO roles (code, name, is_active) VALUES
    ('ADMIN', 'Administrator', TRUE),
    ('IT', 'IT Staff', TRUE),
    ('PUR', 'Purchasing Staff', TRUE),
    ('USER', 'End User', TRUE);

-- ============================================
-- SEED: ASSET_CATEGORIES
-- ============================================
INSERT INTO asset_categories (code, name, is_active) VALUES
    ('HARDWARE', 'Hardware', TRUE),
    ('SOFTWARE', 'Software', TRUE),
    ('CONSUMABLE', 'Consumable', TRUE);

-- ============================================
-- SEED: ASSET_STATUSES (no PENDING_IMPORT)
-- ============================================
INSERT INTO asset_statuses (code, name, is_active) VALUES
    ('IN_STOCK', 'In Stock', TRUE),
    ('IN_USE', 'In Use', TRUE),
    ('IN_REPAIR', 'In Repair', TRUE),
    ('DAMAGED', 'Damaged', TRUE),
    ('RETIRED', 'Retired', TRUE);

-- ============================================
-- SEED: ASSET_CONDITIONS
-- ============================================
INSERT INTO asset_conditions (code, name, is_active) VALUES
    ('NEW', 'New', TRUE),
    ('USED', 'Used', TRUE),
    ('DAMAGED', 'Damaged', TRUE);

-- ============================================
-- SEED: SOFTWARE_LICENSE_TYPES
-- ============================================
INSERT INTO software_license_types (code, name, is_active) VALUES
    ('PER_USER', 'Per User', TRUE),
    ('OEM', 'OEM License', TRUE),
    ('PERPETUAL', 'Perpetual License', TRUE);
