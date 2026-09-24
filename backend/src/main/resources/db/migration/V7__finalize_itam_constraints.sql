-- Destructive cleanup is isolated from the backfill migration so row counts can
-- be checked before applying this version to a populated environment.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM documents WHERE transaction_id IS NULL) THEN
    RAISE EXCEPTION 'Manual remediation required: documents without transaction_id exist';
  END IF;
  IF EXISTS (
      SELECT 1
      FROM asset_license_details d
      JOIN license_term_types t ON t.license_term_type_id = d.license_term_type_id
      WHERE t.code = 'SUBSCRIPTION' AND d.expiry_date IS NULL
  ) THEN
    RAISE EXCEPTION 'Manual remediation required: subscription licenses without expiry_date exist';
  END IF;
  IF EXISTS (SELECT 1 FROM assets WHERE created_by IS NULL) THEN
    RAISE EXCEPTION 'Manual remediation required: assets without created_by exist';
  END IF;
END $$;

ALTER TABLE documents ALTER COLUMN transaction_id SET NOT NULL;
ALTER TABLE documents DROP CONSTRAINT chk_transaction_or_asset;

ALTER TABLE assets DROP CONSTRAINT fk_assets_category;
DROP INDEX idx_assets_category;
ALTER TABLE assets DROP COLUMN category_id;
ALTER TABLE assets ALTER COLUMN created_by SET NOT NULL;
ALTER TABLE transactions DROP CONSTRAINT fk_transactions_related_user;
ALTER TABLE transactions DROP COLUMN related_user_id;

ALTER TABLE asset_license_details DROP COLUMN seat_count;
ALTER TABLE asset_license_details DROP COLUMN license_type_id;
DROP TABLE software_license_types;

DROP TABLE asset_history;
ALTER TABLE users DROP COLUMN is_active;

ALTER TABLE departments ALTER COLUMN is_active SET NOT NULL;
ALTER TABLE departments ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE locations ALTER COLUMN code SET NOT NULL;
ALTER TABLE locations ALTER COLUMN is_active SET NOT NULL;
ALTER TABLE locations ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE suppliers ALTER COLUMN code SET NOT NULL;
ALTER TABLE suppliers ALTER COLUMN is_active SET NOT NULL;
ALTER TABLE suppliers ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE asset_categories ALTER COLUMN is_active SET NOT NULL;
ALTER TABLE asset_types ALTER COLUMN is_active SET NOT NULL;
ALTER TABLE asset_statuses ALTER COLUMN is_active SET NOT NULL;
ALTER TABLE asset_conditions ALTER COLUMN is_active SET NOT NULL;
ALTER TABLE assets ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE asset_relationships ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE import_batches ALTER COLUMN total_rows SET NOT NULL;
ALTER TABLE import_batches ALTER COLUMN valid_rows SET NOT NULL;
ALTER TABLE import_batches ALTER COLUMN invalid_rows SET NOT NULL;
ALTER TABLE import_batches ALTER COLUMN imported_rows SET NOT NULL;
