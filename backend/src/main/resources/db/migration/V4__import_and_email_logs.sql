-- V4: Excel import and email logs
-- Creates: import_batches, import_rows, email_logs

-- ============================================
-- 22. IMPORT_BATCHES
-- ============================================
CREATE TABLE import_batches (
    import_batch_id BIGINT PRIMARY KEY DEFAULT nextval('global_id_seq'),
    file_name VARCHAR(500) NOT NULL,
    status VARCHAR(30) NOT NULL,
    total_rows INTEGER DEFAULT 0 CHECK (total_rows >= 0),
    valid_rows INTEGER DEFAULT 0 CHECK (valid_rows >= 0),
    invalid_rows INTEGER DEFAULT 0 CHECK (invalid_rows >= 0),
    imported_rows INTEGER DEFAULT 0 CHECK (imported_rows >= 0),
    uploaded_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT fk_import_batches_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users(user_id)
);

CREATE INDEX idx_import_batches_status ON import_batches(status);
CREATE INDEX idx_import_batches_uploaded_by ON import_batches(uploaded_by);
CREATE INDEX idx_import_batches_created_at ON import_batches(created_at);

-- ============================================
-- 23. IMPORT_ROWS
-- ============================================
CREATE TABLE import_rows (
    import_row_id BIGINT PRIMARY KEY DEFAULT nextval('global_id_seq'),
    import_batch_id BIGINT NOT NULL,
    row_number INTEGER NOT NULL,
    raw_data JSONB NOT NULL,
    validation_status VARCHAR(30) NOT NULL,
    error_message TEXT,
    asset_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_import_rows_batch FOREIGN KEY (import_batch_id) REFERENCES import_batches(import_batch_id) ON DELETE CASCADE,
    CONSTRAINT fk_import_rows_asset FOREIGN KEY (asset_id) REFERENCES assets(asset_id),
    CONSTRAINT uq_import_rows_batch_row UNIQUE (import_batch_id, row_number)
);

CREATE INDEX idx_import_rows_batch ON import_rows(import_batch_id);
CREATE INDEX idx_import_rows_batch_status ON import_rows(import_batch_id, validation_status);
CREATE INDEX idx_import_rows_validation_status ON import_rows(validation_status);
CREATE INDEX idx_import_rows_asset ON import_rows(asset_id);

-- ============================================
-- 24. EMAIL_LOGS
-- ============================================
CREATE TABLE email_logs (
    email_log_id BIGINT PRIMARY KEY DEFAULT nextval('global_id_seq'),
    transaction_id BIGINT,
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(500) NOT NULL,
    content TEXT,
    status VARCHAR(30) NOT NULL,
    error_message TEXT,
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_email_logs_transaction FOREIGN KEY (transaction_id) REFERENCES transactions(transaction_id)
);

CREATE INDEX idx_email_logs_transaction ON email_logs(transaction_id);
CREATE INDEX idx_email_logs_recipient ON email_logs(recipient);
CREATE INDEX idx_email_logs_status ON email_logs(status);
CREATE INDEX idx_email_logs_created_at ON email_logs(created_at);
