CREATE TABLE workflow_progress (
    transaction_id BIGINT PRIMARY KEY REFERENCES transactions(transaction_id),
    groups JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(groups) = 'array')
);
ALTER TABLE recovery_workflows ADD COLUMN result JSONB;
ALTER TABLE transactions DROP CONSTRAINT chk_transaction_status;
ALTER TABLE transactions ADD CONSTRAINT chk_transaction_status CHECK (
    status IN ('PENDING','COMPLETED','REJECTED') OR
    (status IN ('DRAFT','CANCELLED') AND type IN ('IMPORT','DISPOSAL','HANDOVER','RECOVERY')) OR
    (status='PARTIAL' AND type IN ('HANDOVER','RECOVERY','DISPOSAL')) OR
    (status='APPROVED' AND type='DISPOSAL'));
