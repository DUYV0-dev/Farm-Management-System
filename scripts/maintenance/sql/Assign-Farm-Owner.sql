-- Explicit mapping of existing data; psql -v farm_id=123 -v owner_username=owner.name -f this-file.
\set ON_ERROR_STOP on
BEGIN;
SELECT pg_advisory_xact_lock(62024001);
CREATE TEMP TABLE owner_assignment ON COMMIT DROP AS
 SELECT f.farm_id,u.user_id FROM public.farm f CROSS JOIN public.app_user u
 WHERE f.farm_id=:'farm_id'::INTEGER AND u.username=:'owner_username' AND u.status='ACTIVE'
 AND (f.owner_user_id IS NULL OR f.owner_user_id=u.user_id);
DO $$ BEGIN
 IF(SELECT count(*) FROM owner_assignment)<>1 THEN RAISE EXCEPTION 'Require one ACTIVE user and an existing farm without a different owner'; END IF;
END $$;
UPDATE public.farm f SET owner_user_id=a.user_id FROM owner_assignment a WHERE f.farm_id=a.farm_id;
INSERT INTO public.user_role SELECT a.user_id,r.role_id FROM owner_assignment a CROSS JOIN public.role r
 WHERE r.scope='SYSTEM' AND r.role_name='USER' ON CONFLICT DO NOTHING;
INSERT INTO public.farm_member(farm_id,user_id) SELECT farm_id,user_id FROM owner_assignment
 ON CONFLICT(farm_id,user_id) DO UPDATE SET status='ACTIVE';
INSERT INTO public.farm_member_role SELECT a.farm_id,a.user_id,r.role_id FROM owner_assignment a CROSS JOIN public.role r
 WHERE r.scope='FARM' AND r.role_name='OWNER' ON CONFLICT DO NOTHING;
INSERT INTO public.audit_log(actor_user_id,event_at,entity_type,entity_key,action,request_id)
 SELECT NULL,CURRENT_TIMESTAMP,'FARM',farm_id::TEXT,'ROLE_CHANGE',gen_random_uuid()::TEXT FROM owner_assignment;
COMMIT;
