ALTER TABLE transactions DROP CONSTRAINT chk_transaction_status;
ALTER TABLE transactions ADD CONSTRAINT chk_transaction_status CHECK (
    status IN ('PENDING','COMPLETED','REJECTED') OR
    (status IN ('DRAFT','CANCELLED') AND type IN ('IMPORT','DISPOSAL','HANDOVER','RECOVERY')));
