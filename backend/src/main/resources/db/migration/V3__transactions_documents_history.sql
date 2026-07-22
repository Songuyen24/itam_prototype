-- V3: Transactions, documents and history
-- Creates: transactions, transaction_assets, documents, asset_history

-- ============================================
-- 18. TRANSACTIONS
-- ============================================
CREATE TABLE transactions (
    transaction_id BIGINT PRIMARY KEY DEFAULT nextval('global_id_seq'),
    transaction_code VARCHAR(100) NOT NULL UNIQUE,
    type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    requester_id BIGINT NOT NULL,
    related_user_id BIGINT,
    notes TEXT,
    processed_by BIGINT,
    processed_at TIMESTAMPTZ,
    rejection_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT fk_transactions_requester FOREIGN KEY (requester_id) REFERENCES users(user_id),
    CONSTRAINT fk_transactions_related_user FOREIGN KEY (related_user_id) REFERENCES users(user_id),
    CONSTRAINT fk_transactions_processed_by FOREIGN KEY (processed_by) REFERENCES users(user_id)
);

CREATE INDEX idx_transactions_code ON transactions(transaction_code);
CREATE INDEX idx_transactions_type ON transactions(type);
CREATE INDEX idx_transactions_status ON transactions(status);
CREATE INDEX idx_transactions_type_status ON transactions(type, status);
CREATE INDEX idx_transactions_requester ON transactions(requester_id);
CREATE INDEX idx_transactions_created_at ON transactions(created_at);
CREATE INDEX idx_transactions_processed_by ON transactions(processed_by);

-- ============================================
-- 19. TRANSACTION_ASSETS
-- ============================================
CREATE TABLE transaction_assets (
    transaction_asset_id BIGINT PRIMARY KEY DEFAULT nextval('global_id_seq'),
    transaction_id BIGINT NOT NULL,
    asset_id BIGINT,
    draft_data JSONB,
    notes TEXT,
    CONSTRAINT fk_transaction_assets_transaction FOREIGN KEY (transaction_id) REFERENCES transactions(transaction_id) ON DELETE CASCADE,
    CONSTRAINT fk_transaction_assets_asset FOREIGN KEY (asset_id) REFERENCES assets(asset_id),
    CONSTRAINT chk_asset_or_draft CHECK (asset_id IS NOT NULL OR draft_data IS NOT NULL)
);

CREATE INDEX idx_transaction_assets_transaction ON transaction_assets(transaction_id);
CREATE INDEX idx_transaction_assets_asset ON transaction_assets(asset_id);

-- ============================================
-- 20. DOCUMENTS
-- ============================================
CREATE TABLE documents (
    document_id BIGINT PRIMARY KEY DEFAULT nextval('global_id_seq'),
    transaction_id BIGINT,
    asset_id BIGINT,
    document_type VARCHAR(50) NOT NULL,
    original_file_name VARCHAR(500) NOT NULL,
    stored_file_name VARCHAR(500) NOT NULL,
    storage_path VARCHAR(1000) NOT NULL,
    mime_type VARCHAR(100),
    file_size BIGINT CHECK (file_size >= 0),
    checksum VARCHAR(128),
    uploaded_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_documents_transaction FOREIGN KEY (transaction_id) REFERENCES transactions(transaction_id),
    CONSTRAINT fk_documents_asset FOREIGN KEY (asset_id) REFERENCES assets(asset_id),
    CONSTRAINT fk_documents_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users(user_id),
    CONSTRAINT chk_transaction_or_asset CHECK (transaction_id IS NOT NULL OR asset_id IS NOT NULL)
);

CREATE INDEX idx_documents_transaction ON documents(transaction_id);
CREATE INDEX idx_documents_asset ON documents(asset_id);
CREATE INDEX idx_documents_type ON documents(document_type);
CREATE INDEX idx_documents_uploaded_by ON documents(uploaded_by);

-- ============================================
-- 21. ASSET_HISTORY
-- ============================================
CREATE TABLE asset_history (
    history_id BIGINT PRIMARY KEY DEFAULT nextval('global_id_seq'),
    asset_id BIGINT NOT NULL,
    transaction_id BIGINT,
    actor_user_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    old_data JSONB,
    new_data JSONB,
    details TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_asset_history_asset FOREIGN KEY (asset_id) REFERENCES assets(asset_id),
    CONSTRAINT fk_asset_history_transaction FOREIGN KEY (transaction_id) REFERENCES transactions(transaction_id),
    CONSTRAINT fk_asset_history_actor FOREIGN KEY (actor_user_id) REFERENCES users(user_id)
);

CREATE INDEX idx_asset_history_asset ON asset_history(asset_id);
CREATE INDEX idx_asset_history_asset_created ON asset_history(asset_id, created_at);
CREATE INDEX idx_asset_history_transaction ON asset_history(transaction_id);
CREATE INDEX idx_asset_history_action ON asset_history(action);
