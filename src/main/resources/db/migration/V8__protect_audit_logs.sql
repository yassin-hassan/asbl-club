-- Gel du journal d'audit au niveau de la base, la seule couche qui tient
-- meme quand l'application se trompe. La modification est toujours refusee,
-- la suppression n'est permise que passe la periode de retention (3 ans),
-- donc jamais de facon ciblee sur une ligne recente.

CREATE FUNCTION reject_audit_log_tampering() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.created_at >= now() - INTERVAL '3 years' THEN
            RAISE EXCEPTION 'audit_logs: deletion forbidden before the retention period elapses';
        END IF;
        RETURN OLD;
    END IF;
    -- UPDATE et TRUNCATE
    RAISE EXCEPTION 'audit_logs: rows are immutable, % is forbidden', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_logs_no_tampering
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW
    EXECUTE FUNCTION reject_audit_log_tampering();

CREATE TRIGGER audit_logs_no_truncate
    BEFORE TRUNCATE ON audit_logs
    FOR EACH STATEMENT
    EXECUTE FUNCTION reject_audit_log_tampering();
