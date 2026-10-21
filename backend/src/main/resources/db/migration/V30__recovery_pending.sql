CREATE TABLE recovery_workflows (
 transaction_id bigint PRIMARY KEY REFERENCES transactions(transaction_id),
 request jsonb NOT NULL,
 preview jsonb NOT NULL
);
CREATE TABLE recovery_asset_reservations (
 asset_id bigint PRIMARY KEY REFERENCES assets(asset_id),
 transaction_id bigint NOT NULL REFERENCES transactions(transaction_id)
);
CREATE INDEX idx_recovery_reserved_tx ON recovery_asset_reservations(transaction_id);
CREATE TABLE recovery_allocation_reservations (
 allocation_id bigint PRIMARY KEY REFERENCES license_allocations(allocation_id),
 transaction_id bigint NOT NULL REFERENCES transactions(transaction_id)
);
CREATE INDEX idx_recovery_reserved_allocation_tx ON recovery_allocation_reservations(transaction_id);
