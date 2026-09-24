ALTER TABLE license_allocations
    DROP CONSTRAINT IF EXISTS license_allocations_check2;

ALTER TABLE license_allocations
    ADD CONSTRAINT ck_license_allocation_active_target
    CHECK (status <> 'ACTIVE' OR user_id IS NOT NULL OR device_asset_id IS NOT NULL);
