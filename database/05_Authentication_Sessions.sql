-- Run ONCE as postgres in farm_management_erd_v1, after schema v1.
-- Additive migration. 26 business tables become 27 including auth_session.
BEGIN;
CREATE TABLE public.auth_session (
    session_id UUID PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES public.app_user(user_id) ON DELETE RESTRICT,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CHECK (expires_at > issued_at),
    CHECK (revoked_at IS NULL OR revoked_at >= issued_at)
);
CREATE INDEX ix_auth_session_user ON public.auth_session(user_id);
CREATE INDEX ix_auth_session_expiry ON public.auth_session(expires_at);
-- Revocation remains effective even if an account is later reactivated.
CREATE FUNCTION public.revoke_user_auth_sessions() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public AS $$
BEGIN
    IF (NEW.status = 'INACTIVE' AND OLD.status IS DISTINCT FROM NEW.status)
       OR OLD.password_hash IS DISTINCT FROM NEW.password_hash THEN
        UPDATE public.auth_session SET revoked_at = GREATEST(clock_timestamp(),issued_at)
        WHERE user_id = NEW.user_id AND revoked_at IS NULL;
    END IF;
    RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION public.revoke_user_auth_sessions() FROM PUBLIC;
CREATE TRIGGER trg_revoke_user_auth_sessions
AFTER UPDATE OF status,password_hash ON public.app_user
FOR EACH ROW EXECUTE FUNCTION public.revoke_user_auth_sessions();
GRANT SELECT, INSERT, UPDATE ON public.auth_session TO farm_app;
-- Protect audit history from the runtime account; seed uses a separate admin connection.
REVOKE UPDATE, DELETE, TRUNCATE ON public.audit_log FROM farm_app;
COMMIT;

--phuong da sua--
