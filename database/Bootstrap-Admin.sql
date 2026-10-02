-- Explicit one-time promotion by the database owner using psql:
-- psql -d farm_management_erd_v1 -v admin_username=your.username -f database/Bootstrap-Admin.sql
\set ON_ERROR_STOP on
BEGIN;
SELECT pg_advisory_xact_lock(62024001);
CREATE TEMP TABLE bootstrap_admin ON COMMIT DROP AS
SELECT user_id FROM public.app_user WHERE username = :'admin_username' AND status = 'ACTIVE';
DO $$ BEGIN
    IF (SELECT count(*) FROM bootstrap_admin) <> 1 THEN
        RAISE EXCEPTION 'Exactly one existing ACTIVE user is required';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM public.role WHERE scope='SYSTEM' AND role_name='SYSTEM_ADMIN') THEN
        RAISE EXCEPTION 'Run migration 06 first';
    END IF;
END $$;
INSERT INTO public.user_role(user_id, role_id)
SELECT u.user_id, r.role_id FROM bootstrap_admin u CROSS JOIN public.role r
WHERE r.scope='SYSTEM' AND r.role_name='SYSTEM_ADMIN'
ON CONFLICT DO NOTHING;
INSERT INTO public.audit_log(actor_user_id,event_at,entity_type,entity_key,action,request_id)
SELECT NULL,CURRENT_TIMESTAMP,'USER_ROLE',user_id::text,'ROLE_CHANGE',gen_random_uuid()::text FROM bootstrap_admin;
COMMIT;
