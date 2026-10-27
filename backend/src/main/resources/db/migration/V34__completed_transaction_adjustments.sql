CREATE TABLE transaction_adjustments (
    adjustment_id bigserial PRIMARY KEY,
    transaction_id bigint NOT NULL REFERENCES transactions(transaction_id),
    version integer NOT NULL CHECK(version > 0),
    kind varchar(30) NOT NULL CHECK(kind IN ('DESCRIPTION','DOCUMENT','WORKFLOW')),
    reason text NOT NULL CHECK(length(trim(reason)) BETWEEN 1 AND 4000),
    proposed_text text NOT NULL CHECK(length(trim(proposed_text)) BETWEEN 1 AND 8000),
    corrective_transaction_id bigint REFERENCES transactions(transaction_id),
    requested_by bigint NOT NULL REFERENCES users(user_id),
    created_at timestamptz NOT NULL DEFAULT now(),
    status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','APPROVED','REJECTED')),
    decided_by bigint REFERENCES users(user_id),
    decided_at timestamptz,
    decision_reason text,
    impact_snapshot jsonb,
    UNIQUE(transaction_id,version),
    CHECK(corrective_transaction_id IS NULL OR (kind='WORKFLOW' AND corrective_transaction_id<>transaction_id)),
    CHECK((status='PENDING' AND decided_by IS NULL AND decided_at IS NULL AND decision_reason IS NULL AND impact_snapshot IS NULL)
       OR (status<>'PENDING' AND decided_by IS NOT NULL AND decided_at IS NOT NULL AND length(trim(decision_reason)) BETWEEN 1 AND 4000 AND impact_snapshot IS NOT NULL))
);
CREATE TABLE adjustment_files (
    file_id bigserial PRIMARY KEY,
    adjustment_id bigint NOT NULL REFERENCES transaction_adjustments(adjustment_id),
    original_file_name varchar(500) NOT NULL,
    stored_file_name varchar(500) NOT NULL,
    storage_path varchar(1000) NOT NULL,
    mime_type varchar(100) NOT NULL,
    file_size bigint NOT NULL,
    checksum varchar(128) NOT NULL
);
CREATE INDEX adjustment_files_request ON adjustment_files(adjustment_id);
CREATE FUNCTION guard_adjustment_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Adjustment requests are immutable'; END IF;
    IF OLD.status<>'PENDING' OR NEW.status='PENDING'
       OR (to_jsonb(OLD)-ARRAY['status','decided_by','decided_at','decision_reason','impact_snapshot'])
          IS DISTINCT FROM (to_jsonb(NEW)-ARRAY['status','decided_by','decided_at','decision_reason','impact_snapshot']) THEN
        RAISE EXCEPTION 'Submitted adjustment content is immutable';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER adjustment_immutable BEFORE UPDATE OR DELETE ON transaction_adjustments
FOR EACH ROW EXECUTE FUNCTION guard_adjustment_mutation();
CREATE TRIGGER adjustment_file_immutable BEFORE UPDATE OR DELETE ON adjustment_files
FOR EACH ROW EXECUTE FUNCTION reject_revision_mutation();
