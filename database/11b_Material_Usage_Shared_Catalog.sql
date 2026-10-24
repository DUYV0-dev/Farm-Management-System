-- After 12: old shared crop catalogs are readable but cannot be row-locked by
-- farm_app under the tenant UPDATE policy. Lock as owner, with explicit tenant
-- checks inside this SECURITY DEFINER function before accepting the application.
BEGIN;
CREATE OR REPLACE FUNCTION public.material_usage_warehouse_guard() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$ BEGIN
 PERFORM pg_advisory_xact_lock(740207);
 IF NEW.application_date>(CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Bangkok')::date THEN
  RAISE EXCEPTION 'Usage date cannot be in the future' USING ERRCODE='23514';
 END IF;
 PERFORM s.season_id FROM public.crop_season s JOIN public.plot p USING(plot_id)
 JOIN public.farm f USING(farm_id) JOIN public.crop c USING(crop_id)
 WHERE s.season_id=NEW.season_id AND s.status IN ('IN_PROGRESS','COMPLETED')
 AND p.status='ACTIVE' AND f.status='ACTIVE' AND c.status='ACTIVE'
 AND public.can_access_farm(f.farm_id)
 AND (c.tenant_farm_id IS NULL OR public.can_access_farm(c.tenant_farm_id))
 FOR SHARE OF s,p,f,c;
 IF NOT FOUND THEN
  RAISE EXCEPTION 'Usage requires an available season in the selected farm' USING ERRCODE='23514';
 END IF;
 SELECT m.material_name,m.unit INTO NEW.material_name,NEW.unit FROM public.material m
 JOIN public.material_category c USING(category_id)
 WHERE m.material_id=NEW.material_id AND m.status='ACTIVE' AND c.status='ACTIVE'
 AND (m.tenant_farm_id IS NULL OR public.can_access_farm(m.tenant_farm_id))
 AND (c.tenant_farm_id IS NULL OR public.can_access_farm(c.tenant_farm_id))
 FOR SHARE OF m,c;
 IF NOT FOUND THEN
  RAISE EXCEPTION 'Usage requires an available material in the selected farm' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION public.material_usage_warehouse_guard() FROM PUBLIC;
COMMIT;
