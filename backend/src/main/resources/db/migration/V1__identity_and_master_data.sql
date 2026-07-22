-- V1: Identity and master data tables
-- Creates: roles, departments, locations, suppliers, supplier_contacts,
--          asset_categories, asset_types, asset_statuses, asset_conditions,
--          models, software_catalog, software_license_types

-- Create sequence for BIGINT primary keys
CREATE SEQUENCE IF NOT EXISTS global_id_seq START WITH 1 INCREMENT BY 1;

-- ============================================
-- 1. ROLES
-- ============================================
CREATE TABLE roles (
    role_id BIGINT PRIMARY KEY DEFAULT nextval('global_id_seq'),
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_roles_code ON roles(code);

-- ============================================
-- 2. DEPARTMENTS
-- ============================================
CREATE TABLE departments (
    department_id BIGINT PRIMARY KEY DEFAULT nextval('global_id_seq'),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_departments_code ON departments(code);

-- ============================================
-- 3. USERS (basic structure, password_hash added later if needed)
-- ============================================
CREATE TABLE users (
    user_id BIGINT PRIMARY KEY DEFAULT nextval('global_id_seq'),
    email VARCHAR(255) NOT NULL UNIQUE,
    full_name VARCHAR(255) NOT NULL,
    role_id BIGINT NOT NULL,
    department_id BIGINT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles(role_id),
    CONSTRAINT fk_users_department FOREIGN KEY (department_id) REFERENCES departments(department_id)
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role_id);
CREATE INDEX idx_users_department ON users(department_id);
CREATE INDEX idx_users_is_active ON users(is_active);

-- ============================================
-- 4. LOCATIONS
-- ============================================
CREATE TABLE locations (
    location_id BIGINT PRIMARY KEY DEFAULT nextval('global_id_seq'),
    code VARCHAR(50) UNIQUE,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(500),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_locations_code ON locations(code);

-- ============================================
-- 5. SUPPLIERS
-- ============================================
CREATE TABLE suppliers (
    supplier_id BIGINT PRIMARY KEY DEFAULT nextval('global_id_seq'),
    code VARCHAR(50) UNIQUE,
    name VARCHAR(255) NOT NULL,
    tax_code VARCHAR(100),
    address VARCHAR(500),
    phone VARCHAR(50),
    email VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_suppliers_code ON suppliers(code);
CREATE INDEX idx_suppliers_name ON suppliers(name);

-- ============================================
-- 6. SUPPLIER_CONTACTS
-- ============================================
CREATE TABLE supplier_contacts (
    contact_id BIGINT PRIMARY KEY DEFAULT nextval('global_id_seq'),
    supplier_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    position VARCHAR(255),
    phone VARCHAR(50),
    email VARCHAR(255),
    CONSTRAINT fk_supplier_contacts_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id) ON DELETE CASCADE
);

CREATE INDEX idx_supplier_contacts_supplier ON supplier_contacts(supplier_id);

-- ============================================
-- 7. ASSET_CATEGORIES
-- ============================================
CREATE TABLE asset_categories (
    category_id BIGINT PRIMARY KEY DEFAULT nextval('global_id_seq'),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE
);

CREATE INDEX idx_asset_categories_code ON asset_categories(code);

-- ============================================
-- 8. ASSET_TYPES
-- ============================================
CREATE TABLE asset_types (
    type_id BIGINT PRIMARY KEY DEFAULT nextval('global_id_seq'),
    code VARCHAR(50) NOT NULL UNIQUE,
    type_name VARCHAR(255) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE
);

CREATE INDEX idx_asset_types_code ON asset_types(code);

-- ============================================
-- 9. ASSET_STATUSES
-- ============================================
CREATE TABLE asset_statuses (
    status_id BIGINT PRIMARY KEY DEFAULT nextval('global_id_seq'),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE
);

CREATE INDEX idx_asset_statuses_code ON asset_statuses(code);

-- ============================================
-- 10. ASSET_CONDITIONS
-- ============================================
CREATE TABLE asset_conditions (
    condition_id BIGINT PRIMARY KEY DEFAULT nextval('global_id_seq'),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE
);

CREATE INDEX idx_asset_conditions_code ON asset_conditions(code);

-- ============================================
-- 11. MODELS
-- ============================================
CREATE TABLE models (
    model_id BIGINT PRIMARY KEY DEFAULT nextval('global_id_seq'),
    name VARCHAR(255) NOT NULL,
    brand VARCHAR(255),
    type_id BIGINT NOT NULL,
    default_cpu VARCHAR(255),
    default_ram VARCHAR(100),
    default_storage VARCHAR(100),
    default_graphics_card VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_models_type FOREIGN KEY (type_id) REFERENCES asset_types(type_id)
);

CREATE INDEX idx_models_type ON models(type_id);
CREATE INDEX idx_models_name ON models(name);

-- ============================================
-- 12. SOFTWARE_CATALOG
-- ============================================
CREATE TABLE software_catalog (
    software_catalog_id BIGINT PRIMARY KEY DEFAULT nextval('global_id_seq'),
    name VARCHAR(255) NOT NULL,
    manufacturer VARCHAR(255),
    version VARCHAR(100),
    is_active BOOLEAN DEFAULT TRUE
);

CREATE INDEX idx_software_catalog_name ON software_catalog(name);

-- ============================================
-- 13. SOFTWARE_LICENSE_TYPES
-- ============================================
CREATE TABLE software_license_types (
    license_type_id BIGINT PRIMARY KEY DEFAULT nextval('global_id_seq'),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE
);

CREATE INDEX idx_software_license_types_code ON software_license_types(code);
