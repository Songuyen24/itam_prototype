-- V2: Assets and asset details
-- Creates: assets, asset_hardware_details, asset_software_details, asset_relationships

-- ============================================
-- 14. ASSETS (main asset table)
-- ============================================
CREATE TABLE assets (
    asset_id BIGINT PRIMARY KEY DEFAULT nextval('global_id_seq'),
    asset_tag VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    category_id BIGINT NOT NULL,
    status_id BIGINT NOT NULL,
    assigned_to BIGINT,
    department_id BIGINT,
    location_id BIGINT,
    supplier_id BIGINT,
    po_number VARCHAR(100),
    purchase_date DATE,
    purchase_cost NUMERIC(18, 2) DEFAULT 0 CHECK (purchase_cost >= 0),
    created_by BIGINT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_assets_category FOREIGN KEY (category_id) REFERENCES asset_categories(category_id),
    CONSTRAINT fk_assets_status FOREIGN KEY (status_id) REFERENCES asset_statuses(status_id),
    CONSTRAINT fk_assets_assigned_to FOREIGN KEY (assigned_to) REFERENCES users(user_id),
    CONSTRAINT fk_assets_department FOREIGN KEY (department_id) REFERENCES departments(department_id),
    CONSTRAINT fk_assets_location FOREIGN KEY (location_id) REFERENCES locations(location_id),
    CONSTRAINT fk_assets_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id),
    CONSTRAINT fk_assets_created_by FOREIGN KEY (created_by) REFERENCES users(user_id),
    CONSTRAINT fk_assets_updated_by FOREIGN KEY (updated_by) REFERENCES users(user_id)
);

-- Indexes for common queries
CREATE INDEX idx_assets_asset_tag ON assets(asset_tag);
CREATE INDEX idx_assets_name ON assets(name);
CREATE INDEX idx_assets_status ON assets(status_id);
CREATE INDEX idx_assets_assigned_to ON assets(assigned_to);
CREATE INDEX idx_assets_department ON assets(department_id);
CREATE INDEX idx_assets_location ON assets(location_id);
CREATE INDEX idx_assets_category ON assets(category_id);
CREATE INDEX idx_assets_created_at ON assets(created_at);

-- ============================================
-- 15. ASSET_HARDWARE_DETAILS (one-to-one with assets)
-- ============================================
CREATE TABLE asset_hardware_details (
    asset_id BIGINT PRIMARY KEY,
    serial_number VARCHAR(255) NOT NULL UNIQUE,
    model_id BIGINT,
    warranty_expiration DATE,
    condition_id BIGINT,
    actual_cpu VARCHAR(255),
    actual_ram VARCHAR(100),
    actual_storage VARCHAR(100),
    actual_graphics_card VARCHAR(255),
    CONSTRAINT fk_asset_hw_asset FOREIGN KEY (asset_id) REFERENCES assets(asset_id) ON DELETE CASCADE,
    CONSTRAINT fk_asset_hw_model FOREIGN KEY (model_id) REFERENCES models(model_id),
    CONSTRAINT fk_asset_hw_condition FOREIGN KEY (condition_id) REFERENCES asset_conditions(condition_id)
);

CREATE INDEX idx_asset_hardware_details_serial ON asset_hardware_details(serial_number);
CREATE INDEX idx_asset_hardware_details_model ON asset_hardware_details(model_id);

-- ============================================
-- 16. ASSET_SOFTWARE_DETAILS (one-to-one with assets)
-- ============================================
CREATE TABLE asset_software_details (
    asset_id BIGINT PRIMARY KEY,
    software_catalog_id BIGINT NOT NULL,
    license_key VARCHAR(500),
    license_type_id BIGINT,
    seat_count INTEGER CHECK (seat_count > 0),
    expiry_date DATE,
    CONSTRAINT fk_asset_sw_asset FOREIGN KEY (asset_id) REFERENCES assets(asset_id) ON DELETE CASCADE,
    CONSTRAINT fk_asset_sw_catalog FOREIGN KEY (software_catalog_id) REFERENCES software_catalog(software_catalog_id),
    CONSTRAINT fk_asset_sw_license_type FOREIGN KEY (license_type_id) REFERENCES software_license_types(license_type_id)
);

CREATE INDEX idx_asset_software_details_catalog ON asset_software_details(software_catalog_id);

-- ============================================
-- 17. ASSET_RELATIONSHIPS
-- ============================================
CREATE TABLE asset_relationships (
    relationship_id BIGINT PRIMARY KEY DEFAULT nextval('global_id_seq'),
    parent_asset_id BIGINT NOT NULL,
    child_asset_id BIGINT NOT NULL,
    relationship_type VARCHAR(30) NOT NULL,
    created_by BIGINT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_asset_rel_parent FOREIGN KEY (parent_asset_id) REFERENCES assets(asset_id),
    CONSTRAINT fk_asset_rel_child FOREIGN KEY (child_asset_id) REFERENCES assets(asset_id),
    CONSTRAINT fk_asset_rel_created_by FOREIGN KEY (created_by) REFERENCES users(user_id),
    CONSTRAINT chk_no_self_reference CHECK (parent_asset_id != child_asset_id),
    CONSTRAINT uq_asset_relationship UNIQUE (parent_asset_id, child_asset_id, relationship_type)
);

CREATE INDEX idx_asset_relationships_parent ON asset_relationships(parent_asset_id);
CREATE INDEX idx_asset_relationships_child ON asset_relationships(child_asset_id);
CREATE INDEX idx_asset_relationships_type ON asset_relationships(relationship_type);
