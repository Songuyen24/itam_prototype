CREATE UNIQUE INDEX uq_assets_asset_tag_lower ON assets (LOWER(asset_tag));
CREATE UNIQUE INDEX uq_asset_hardware_serial_lower ON asset_hardware_details (LOWER(serial_number))
    WHERE serial_number IS NOT NULL;

ALTER TABLE import_rows ADD COLUMN validation_errors JSONB;
