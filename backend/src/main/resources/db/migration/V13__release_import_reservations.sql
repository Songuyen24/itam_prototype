-- Keep reservations aligned with the lifecycle even when a future receiving
-- approval/rejection service performs the state transition.
CREATE FUNCTION release_import_reservations() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.type='IMPORT' AND NEW.status <> 'PENDING' THEN
        DELETE FROM import_asset_reservations WHERE transaction_id=NEW.transaction_id;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER transaction_release_reservations AFTER UPDATE OF status ON transactions
FOR EACH ROW EXECUTE FUNCTION release_import_reservations();
