ALTER TABLE transactions DROP CONSTRAINT chk_transaction_state;
ALTER TABLE transactions ADD CONSTRAINT chk_transaction_state CHECK (
    (status IN ('DRAFT','PENDING') AND processed_by IS NULL AND processed_at IS NULL
       AND completed_at IS NULL AND rejection_reason IS NULL)
    OR (status='COMPLETED' AND processed_by IS NOT NULL AND processed_at IS NOT NULL
       AND completed_at IS NOT NULL AND rejection_reason IS NULL)
    OR (status='REJECTED' AND processed_by IS NOT NULL AND processed_at IS NOT NULL
       AND completed_at IS NULL AND NULLIF(BTRIM(rejection_reason),'') IS NOT NULL)
);
