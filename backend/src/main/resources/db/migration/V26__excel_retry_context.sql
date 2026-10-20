-- Preserve existing imports; new retries are bound to their owner and workflow.
ALTER TABLE import_batches ADD COLUMN retry_batch_id BIGINT REFERENCES import_batches(import_batch_id);
ALTER TABLE import_batches ADD COLUMN receiving_id BIGINT REFERENCES transactions(transaction_id);
CREATE INDEX idx_import_batches_retry ON import_batches(retry_batch_id);
ALTER TABLE import_batches ADD COLUMN request_id UUID UNIQUE;
