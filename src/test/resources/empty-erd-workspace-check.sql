BEGIN;
DO $$ BEGIN
 IF current_database()<>'management_test' OR inet_server_port()<>55432 THEN
  RAISE EXCEPTION 'Requires isolated management_test on port 55432';
 END IF;
 IF NOT EXISTS(SELECT FROM public.farm WHERE farm_id=1 AND farm_name='Legacy retained farm'
   AND address='Old address' AND area_ha=20 AND owner_user_id IS NULL) THEN
  RAISE EXCEPTION 'Existing farm changed or was assigned an owner automatically';
 END IF;
 IF NOT EXISTS(SELECT FROM public.crop_season WHERE season_id=1 AND yield_unit_id=1
   AND season_name='Legacy season' AND planned_start_date='2026-01-01'
   AND planned_end_date='2026-02-01' AND created_by=1) THEN
  RAISE EXCEPTION 'Legacy season values changed';
 END IF;
 IF to_regclass('public.inventory') IS NULL OR to_regclass('public.inventory_transaction') IS NULL
   OR to_regclass('public.low_stock_alert') IS NULL OR to_regclass('public.unit') IS NULL THEN
  RAISE EXCEPTION 'Legacy tables removed';
 END IF;
 IF EXISTS(SELECT FROM pg_trigger WHERE tgname IN ('fpc_farm_guard','fpc_plot_guard','fpc_crop_guard','fpc_season_guard')) THEN
  RAISE EXCEPTION 'Old guards were not replaced';
 END IF;
END $$;
-- Assign a fixture owner for this transaction only, then exercise writes with the
-- actual restricted application role and the same column lists as the application.
UPDATE public.farm SET owner_user_id=1 WHERE farm_id=1;
INSERT INTO public.farm_member(farm_id,user_id) VALUES(1,1);
SELECT set_config('app.user_id','1',true),set_config('app.farm_id','1',true);
INSERT INTO public.farm(farm_id,farm_name,address,area_ha) VALUES(201,'Other fixture farm','Other address',20);
INSERT INTO public.plot(plot_id,farm_id,plot_name,area_ha) VALUES(201,201,'Other plot',2);
INSERT INTO public.crop_season(season_id,plot_id,crop_id,status) VALUES(201,201,1,'IN_PROGRESS');
SET LOCAL ROLE farm_app;
INSERT INTO public.material_category(category_name) VALUES('Compatibility fertilizer');
INSERT INTO public.material(category_id,material_name,unit,manufacturer,description)
 SELECT category_id,'Compatibility material','kg',NULL,NULL FROM public.material_category
 WHERE category_name='Compatibility fertilizer';
INSERT INTO public.warehouse(farm_id,warehouse_name,location,capacity_text)
 VALUES(1,'Compatibility warehouse',NULL,'100 kg');
INSERT INTO public.warehouse_inventory(warehouse_id,material_id,quantity)
 SELECT w.warehouse_id,m.material_id,10 FROM public.warehouse w CROSS JOIN public.material m;
INSERT INTO public.crop_season(plot_id,crop_id,status) VALUES(1,1,'IN_PROGRESS');
INSERT INTO public.material_usage(season_id,material_id,material_name,usage_type,quantity,unit,application_date,created_by)
 SELECT s.season_id,m.material_id,'will be normalized','FERTILIZER',1,'will be normalized',CURRENT_DATE,1
 FROM public.crop_season s CROSS JOIN public.material m WHERE s.status='IN_PROGRESS';
DO $$ BEGIN
 IF NOT EXISTS(SELECT FROM public.material WHERE unit='kg' AND unit_id IS NULL AND status='ACTIVE') THEN
  RAISE EXCEPTION 'New material creation still requires the legacy unit_id';
 END IF;
 IF NOT EXISTS(SELECT FROM public.crop_season WHERE status='IN_PROGRESS' AND created_by=1
   AND record_status='ACTIVE' AND planned_start_date IS NULL AND yield_unit_id IS NULL) THEN
  RAISE EXCEPTION 'Current season form cannot write to the converted ERD';
 END IF;
 IF NOT EXISTS(SELECT FROM public.material_usage WHERE material_name='Compatibility material' AND unit='kg') THEN
  RAISE EXCEPTION 'Material usage trigger still depends on the legacy unit catalog';
 END IF;
END $$;
-- SECURITY DEFINER must still reject a known season from another farm.
DO $$ DECLARE material INTEGER; BEGIN
 SELECT material_id INTO material FROM public.material WHERE material_name='Compatibility material';
 BEGIN
  INSERT INTO public.material_usage(season_id,material_id,material_name,usage_type,quantity,unit,application_date,created_by)
   VALUES(201,material,'forbidden','FERTILIZER',1,'kg',CURRENT_DATE,1);
  RAISE EXCEPTION 'Cross-farm season was accepted';
 EXCEPTION WHEN check_violation THEN NULL;
 END;
END $$;
ROLLBACK;
