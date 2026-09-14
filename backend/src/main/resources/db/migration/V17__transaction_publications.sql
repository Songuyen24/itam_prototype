ALTER TABLE documents ADD COLUMN publication_version INTEGER;
ALTER TABLE documents ADD COLUMN template_version VARCHAR(30);
ALTER TABLE documents ADD COLUMN issued_at TIMESTAMPTZ;

ALTER TABLE email_logs ADD COLUMN event_type VARCHAR(50);
ALTER TABLE email_logs ADD COLUMN document_id BIGINT REFERENCES documents(document_id);

CREATE TABLE transaction_publication_snapshots (
    transaction_id BIGINT PRIMARY KEY REFERENCES transactions(transaction_id),
    snapshot JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE FUNCTION protect_publication_snapshot() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Transaction publication snapshots are immutable';
END;
$$;
CREATE TRIGGER publication_snapshot_immutable BEFORE UPDATE OR DELETE ON transaction_publication_snapshots
FOR EACH ROW EXECUTE FUNCTION protect_publication_snapshot();

CREATE UNIQUE INDEX uq_documents_publication_version
    ON documents(transaction_id, document_type, publication_version)
    WHERE publication_version IS NOT NULL;
CREATE INDEX idx_email_logs_transaction_created ON email_logs(transaction_id, created_at DESC);
