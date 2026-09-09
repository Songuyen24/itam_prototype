ALTER TABLE transactions DROP CONSTRAINT chk_transaction_status;
ALTER TABLE transactions ADD CONSTRAINT chk_transaction_status CHECK (
    status IN ('PENDING', 'COMPLETED', 'REJECTED') OR (status = 'DRAFT' AND type = 'IMPORT'));
ALTER TABLE transactions ADD COLUMN content_version bigint NOT NULL DEFAULT 0;
ALTER TABLE transactions ADD COLUMN submitted_revision integer NOT NULL DEFAULT 0;
ALTER TABLE transactions ADD COLUMN source_transaction_id bigint REFERENCES transactions(transaction_id);

INSERT INTO asset_statuses(code,name,is_active) VALUES ('PENDING_IMPORT','Chờ duyệt nhập',true);
CREATE TABLE import_asset_reservations (
    asset_id bigint PRIMARY KEY REFERENCES assets(asset_id),
    transaction_id bigint NOT NULL REFERENCES transactions(transaction_id)
);

CREATE TABLE transaction_document_links (
    transaction_id bigint NOT NULL REFERENCES transactions(transaction_id),
    document_id bigint NOT NULL REFERENCES documents(document_id),
    PRIMARY KEY(transaction_id, document_id)
);
INSERT INTO transaction_document_links SELECT transaction_id, document_id FROM documents;

-- Inserts also keep legacy/system-generated document writers compatible.
CREATE FUNCTION link_new_document() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO transaction_document_links VALUES (NEW.transaction_id, NEW.document_id);
    RETURN NEW;
END;
$$;
CREATE TRIGGER document_link_insert AFTER INSERT ON documents
FOR EACH ROW EXECUTE FUNCTION link_new_document();

CREATE TABLE transaction_revisions (
    transaction_id bigint NOT NULL REFERENCES transactions(transaction_id),
    revision integer NOT NULL CHECK (revision > 0),
    content_version bigint NOT NULL,
    submitted_by bigint NOT NULL REFERENCES users(user_id),
    submitted_at timestamptz NOT NULL DEFAULT now(),
    snapshot jsonb NOT NULL,
    PRIMARY KEY(transaction_id, revision)
);
CREATE FUNCTION reject_revision_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Submitted transaction revisions are immutable';
END;
$$;
CREATE TRIGGER revisions_immutable BEFORE UPDATE OR DELETE ON transaction_revisions
FOR EACH ROW EXECUTE FUNCTION reject_revision_mutation();

CREATE FUNCTION guard_historical_document() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.is_locked THEN
        RAISE EXCEPTION 'Historical document metadata is immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER historical_document_immutable BEFORE UPDATE OR DELETE ON documents
FOR EACH ROW EXECUTE FUNCTION guard_historical_document();
