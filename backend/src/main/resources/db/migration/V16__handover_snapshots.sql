-- A committed row is the durable completion event/input for T17 PDF and email.
CREATE TABLE handover_snapshots (
    transaction_id bigint PRIMARY KEY REFERENCES transaction_handover_details(transaction_id),
    snapshot jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE FUNCTION protect_handover_snapshot() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Handover snapshots are immutable';
END;
$$;
CREATE TRIGGER handover_snapshot_immutable BEFORE UPDATE OR DELETE ON handover_snapshots
FOR EACH ROW EXECUTE FUNCTION protect_handover_snapshot();
