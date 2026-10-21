CREATE TABLE handover_workflows (
 transaction_id bigint PRIMARY KEY REFERENCES transactions(transaction_id),
 request jsonb NOT NULL,
 preview jsonb NOT NULL
);
CREATE TABLE handover_asset_reservations (
 asset_id bigint PRIMARY KEY REFERENCES assets(asset_id),
 transaction_id bigint NOT NULL REFERENCES transactions(transaction_id)
);
CREATE INDEX idx_handover_reserved_tx ON handover_asset_reservations(transaction_id);
