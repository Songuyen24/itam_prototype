-- One allocator for devices, components and license packages. Never renumber old assets.
CREATE SEQUENCE asset_tag_sequence;

CREATE FUNCTION next_asset_tag() RETURNS text LANGUAGE plpgsql AS $$
DECLARE candidate text;
BEGIN
    PERFORM pg_advisory_xact_lock(74101301);
    LOOP
        candidate := 'AST-' || lpad(nextval('asset_tag_sequence')::text, 10, '0');
        EXIT WHEN NOT EXISTS (SELECT 1 FROM assets WHERE asset_tag = candidate);
    END LOOP;
    RETURN candidate;
END;
$$;

CREATE FUNCTION guard_asset_tag_insert() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    -- Manual inserts take the same lock as the allocator, including direct SQL imports.
    PERFORM pg_advisory_xact_lock(74101301);
    IF NEW.asset_tag IS NULL OR btrim(NEW.asset_tag) = '' THEN
        NEW.asset_tag := next_asset_tag();
    ELSE
        NEW.asset_tag := btrim(NEW.asset_tag);
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER assets_tag_insert BEFORE INSERT ON assets
FOR EACH ROW EXECUTE FUNCTION guard_asset_tag_insert();
