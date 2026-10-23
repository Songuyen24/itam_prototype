CREATE INDEX idx_audit_created_id ON audit_logs (created_at DESC, audit_log_id DESC);
CREATE INDEX idx_audit_action_created ON audit_logs (action, created_at DESC);
ALTER TABLE audit_logs ALTER COLUMN actor_user_id DROP NOT NULL;
