-- Compatibility bridge ONLY for ERD v1 with empty material/warehouse/inventory data.
-- Use scripts/Convert-EmptyErdWorkspace.ps1, which applies this and 07-12 atomically.
-- No tables, IDs or existing business rows are deleted; old tables remain available.
BEGIN;
SET LOCAL lock_timeout = '10s';
SELECT pg_advisory_xact_lock(62024001);
DO $$
DECLARE t TEXT; has_rows BOOLEAN;
BEGIN
 IF to_regprocedure('public.workspace_memberships(integer)') IS NOT NULL THEN
  RAISE EXCEPTION 'Workspace already installed; do not run the ERD bridge again';
 END IF;
 FOREACH t IN ARRAY ARRAY['app_user','role','auth_permission','auth_role_permission',
   'farm','plot','crop','crop_season','farm_member','farm_member_role',
   'material','unit','warehouse','inventory','inventory_transaction','material_usage'] LOOP
  IF to_regclass('public.'||t) IS NULL THEN RAISE EXCEPTION 'Required ERD table missing: %',t; END IF;
 END LOOP;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema='public'
   AND table_name='material' AND column_name='unit_id') THEN
  RAISE EXCEPTION 'Expected ERD material.unit_id';
 END IF;
 IF to_regclass('public.material_category') IS NOT NULL OR to_regclass('public.warehouse_inventory') IS NOT NULL THEN
  RAISE EXCEPTION 'Expected an unconverted ERD without material_category/warehouse_inventory';
 END IF;
 -- Locks prevent writes between the empty-table checks and the conversion.
 FOREACH t IN ARRAY ARRAY['material','warehouse','inventory','inventory_transaction','material_usage'] LOOP
  EXECUTE format('LOCK TABLE public.%I IN ACCESS EXCLUSIVE MODE',t);
  IF EXISTS(SELECT FROM pg_class WHERE oid=to_regclass('public.'||t) AND relkind<>'r') THEN
   RAISE EXCEPTION 'Expected an ordinary table: %',t;
  END IF;
  EXECUTE format('SELECT EXISTS(SELECT 1 FROM public.%I)',t) INTO has_rows;
  IF has_rows THEN RAISE EXCEPTION 'Table % is not empty; this conversion does not migrate stock/history',t; END IF;
 END LOOP;
END $$;

-- Keep the old unit relationship for historical clients, but new material writes
-- use the explicit unit text. Never fabricate a unit_id or historical season dates.
ALTER TABLE public.material ADD COLUMN IF NOT EXISTS unit VARCHAR(50);
ALTER TABLE public.material ADD COLUMN IF NOT EXISTS category_id INTEGER;
ALTER TABLE public.material ADD COLUMN IF NOT EXISTS manufacturer VARCHAR(150);
ALTER TABLE public.material ALTER COLUMN unit SET NOT NULL;
ALTER TABLE public.material ALTER COLUMN category_id SET NOT NULL;
ALTER TABLE public.material ALTER COLUMN unit_id DROP NOT NULL;
ALTER TABLE public.warehouse ADD COLUMN IF NOT EXISTS capacity_text VARCHAR(100);
ALTER TABLE public.farm ADD COLUMN IF NOT EXISTS created_by INTEGER REFERENCES public.app_user(user_id);

-- ERD status columns have no defaults; the current application omits them on create.
ALTER TABLE public.app_user ALTER COLUMN status SET DEFAULT 'ACTIVE';
ALTER TABLE public.farm ALTER COLUMN status SET DEFAULT 'ACTIVE';
ALTER TABLE public.plot ALTER COLUMN status SET DEFAULT 'ACTIVE';
ALTER TABLE public.crop ALTER COLUMN status SET DEFAULT 'ACTIVE';
ALTER TABLE public.material ALTER COLUMN status SET DEFAULT 'ACTIVE';
ALTER TABLE public.warehouse ALTER COLUMN status SET DEFAULT 'ACTIVE';
ALTER TABLE public.farm_member ALTER COLUMN status SET DEFAULT 'ACTIVE';
ALTER TABLE public.farm_member ALTER COLUMN joined_at SET DEFAULT CURRENT_TIMESTAMP;

-- The new season form only supplies plot, crop and status. Retain the old fields,
-- but allow them to be unspecified rather than inserting invented history.
ALTER TABLE public.crop_season ALTER COLUMN yield_unit_id DROP NOT NULL;
ALTER TABLE public.crop_season ALTER COLUMN season_name DROP NOT NULL;
ALTER TABLE public.crop_season ALTER COLUMN planned_start_date DROP NOT NULL;
ALTER TABLE public.crop_season ALTER COLUMN planned_end_date DROP NOT NULL;
ALTER TABLE public.crop_season ALTER COLUMN record_status SET DEFAULT 'ACTIVE';
ALTER TABLE public.crop_season ALTER COLUMN created_by
 SET DEFAULT NULLIF(current_setting('app.user_id',true),'')::INTEGER;

-- 09 replaces these functions and installs guards under management_guard.
DROP TRIGGER IF EXISTS fpc_farm_guard ON public.farm;
DROP TRIGGER IF EXISTS fpc_plot_guard ON public.plot;
DROP TRIGGER IF EXISTS fpc_crop_guard ON public.crop;
DROP TRIGGER IF EXISTS fpc_season_guard ON public.crop_season;
COMMIT;
