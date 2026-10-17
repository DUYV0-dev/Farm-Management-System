-- Run after 07 and 08 as database owner. Repeatable, transactional, preserves records.
BEGIN;
-- Add optional fields to existing ERD installations as well as fresh installations.
ALTER TABLE public.farm ADD COLUMN IF NOT EXISTS address VARCHAR(255);
ALTER TABLE public.farm ADD COLUMN IF NOT EXISTS description VARCHAR(1000);
ALTER TABLE public.plot ADD COLUMN IF NOT EXISTS location VARCHAR(255);
ALTER TABLE public.plot ADD COLUMN IF NOT EXISTS description VARCHAR(1000);
ALTER TABLE public.crop ADD COLUMN IF NOT EXISTS scientific_name VARCHAR(150);
ALTER TABLE public.crop ADD COLUMN IF NOT EXISTS category VARCHAR(100);
ALTER TABLE public.crop ADD COLUMN IF NOT EXISTS growth_days INTEGER;
ALTER TABLE public.crop ADD COLUMN IF NOT EXISTS description VARCHAR(1000);
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='farm' AND column_name='location') THEN
  EXECUTE 'UPDATE public.farm SET address=location WHERE address IS NULL';
 END IF;
 IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='crop' AND column_name='plot_id') THEN
  RAISE EXCEPTION 'Legacy crop.plot_id schema: convert legacy crops to crop catalog and seasons before installing. No data has been deleted.';
 END IF;
END $$;
INSERT INTO public.auth_role_permission(role_id,permission_code)
SELECT role_id,'crops:status' FROM public.auth_role_permission WHERE permission_code='crops:delete' ON CONFLICT DO NOTHING;

-- Acquire before row locks so competing parent/child writes use the same lock order.
CREATE OR REPLACE FUNCTION public.lock_farm_management() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN PERFORM pg_advisory_xact_lock(740207); RETURN NULL; END $$;

CREATE OR REPLACE FUNCTION public.fpc_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE parent_status TEXT; capacity NUMERIC; used NUMERIC;
BEGIN
 IF TG_TABLE_NAME <> 'crop_season' AND (NEW.status IS NULL OR NEW.status NOT IN ('ACTIVE','INACTIVE')) THEN
  RAISE EXCEPTION 'Invalid status' USING ERRCODE='23514'; END IF;
 IF TG_TABLE_NAME = 'farm' THEN
  IF btrim(NEW.farm_name)='' OR NEW.address IS NULL OR btrim(NEW.address)='' OR NEW.area_ha IS NULL
    OR NEW.area_ha <= 0 OR NEW.area_ha > 9999999 THEN
   RAISE EXCEPTION 'Invalid farm information' USING ERRCODE='23514'; END IF;
  SELECT COALESCE(sum(area_ha),0) INTO used FROM public.plot WHERE farm_id=NEW.farm_id;
  IF used > NEW.area_ha THEN RAISE EXCEPTION 'Farm area is smaller than its plots' USING ERRCODE='23514'; END IF;
  IF NEW.status='INACTIVE' AND (
    EXISTS(SELECT 1 FROM public.plot WHERE farm_id=NEW.farm_id AND status='ACTIVE') OR
    EXISTS(SELECT 1 FROM public.warehouse WHERE farm_id=NEW.farm_id AND status='ACTIVE')) THEN
   RAISE EXCEPTION 'Deactivate active plots and warehouses first' USING ERRCODE='23514'; END IF;
 ELSIF TG_TABLE_NAME = 'plot' THEN
  SELECT status,area_ha INTO parent_status,capacity FROM public.farm WHERE farm_id=NEW.farm_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'Farm not found' USING ERRCODE='23503'; END IF;
  IF (TG_OP='INSERT' OR NEW.status='ACTIVE') AND parent_status <> 'ACTIVE' THEN
   RAISE EXCEPTION 'Farm is inactive' USING ERRCODE='23514'; END IF;
  IF TG_OP='UPDATE' AND NEW.farm_id<>OLD.farm_id THEN
   RAISE EXCEPTION 'Plot farm cannot change' USING ERRCODE='23514'; END IF;
  IF btrim(NEW.plot_name)='' OR NEW.area_ha IS NULL OR NEW.area_ha <= 0 OR NEW.area_ha > 9999999 THEN
   RAISE EXCEPTION 'Invalid plot information' USING ERRCODE='23514'; END IF;
  SELECT COALESCE(sum(area_ha),0) INTO used FROM public.plot WHERE farm_id=NEW.farm_id AND plot_id<>NEW.plot_id;
  IF used+NEW.area_ha > capacity THEN RAISE EXCEPTION 'Plot areas exceed farm area' USING ERRCODE='23514'; END IF;
  IF NEW.status='INACTIVE' AND EXISTS(SELECT 1 FROM public.crop_season WHERE plot_id=NEW.plot_id AND status IN ('PLANNED','IN_PROGRESS')) THEN
   RAISE EXCEPTION 'Plot has open seasons' USING ERRCODE='23514'; END IF;
 ELSIF TG_TABLE_NAME = 'crop' THEN
  IF btrim(NEW.crop_name)='' OR (NEW.growth_days IS NOT NULL AND (NEW.growth_days<=0 OR NEW.growth_days>9999)) THEN
   RAISE EXCEPTION 'Invalid crop information' USING ERRCODE='23514'; END IF;
  IF NEW.status='INACTIVE' AND EXISTS(SELECT 1 FROM public.crop_season WHERE crop_id=NEW.crop_id AND status IN ('PLANNED','IN_PROGRESS')) THEN
   RAISE EXCEPTION 'Crop has open seasons' USING ERRCODE='23514'; END IF;
 ELSE
  IF NEW.status IS NULL OR NEW.status NOT IN ('PLANNED','IN_PROGRESS','COMPLETED','CANCELLED') THEN
   RAISE EXCEPTION 'Invalid season status' USING ERRCODE='23514'; END IF;
  IF TG_OP='UPDATE' AND (NEW.plot_id<>OLD.plot_id OR NEW.crop_id<>OLD.crop_id) THEN
   RAISE EXCEPTION 'Season relationships cannot change' USING ERRCODE='23514'; END IF;
  IF TG_OP='INSERT' OR NEW.status IN ('PLANNED','IN_PROGRESS') THEN
   IF NOT EXISTS(SELECT 1 FROM public.plot p JOIN public.farm f USING(farm_id)
     WHERE p.plot_id=NEW.plot_id AND p.status='ACTIVE' AND f.status='ACTIVE')
     OR NOT EXISTS(SELECT 1 FROM public.crop WHERE crop_id=NEW.crop_id AND status='ACTIVE') THEN
    RAISE EXCEPTION 'Season requires active farm, plot and crop' USING ERRCODE='23514'; END IF;
  END IF;
 END IF;
 RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION public.inventory_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_TABLE_NAME <> 'warehouse_inventory' THEN
  IF NEW.status IS NULL OR NEW.status NOT IN ('ACTIVE','INACTIVE') THEN
   RAISE EXCEPTION 'Invalid status' USING ERRCODE='23514'; END IF;
 END IF;
 IF TG_TABLE_NAME='material_category' THEN
  IF btrim(NEW.category_name)='' THEN RAISE EXCEPTION 'Empty category' USING ERRCODE='23514'; END IF;
  IF NEW.status='INACTIVE' AND EXISTS(SELECT 1 FROM public.material WHERE category_id=NEW.category_id AND status='ACTIVE') THEN
   RAISE EXCEPTION 'Category has active materials' USING ERRCODE='23514'; END IF;
 ELSIF TG_TABLE_NAME='material' THEN
  IF btrim(NEW.material_name)='' OR btrim(NEW.unit)='' THEN RAISE EXCEPTION 'Empty material or unit' USING ERRCODE='23514'; END IF;
  IF (TG_OP='INSERT' OR NEW.status='ACTIVE') AND NOT EXISTS(SELECT 1 FROM public.material_category WHERE category_id=NEW.category_id AND status='ACTIVE') THEN
   RAISE EXCEPTION 'Category is missing or inactive' USING ERRCODE='23514'; END IF;
  IF TG_OP='UPDATE' AND NEW.category_id<>OLD.category_id THEN RAISE EXCEPTION 'Category cannot change' USING ERRCODE='23514'; END IF;
  IF EXISTS(SELECT 1 FROM public.warehouse_inventory WHERE material_id=NEW.material_id AND quantity>0) THEN
   IF NEW.status='INACTIVE' OR (TG_OP='UPDATE' AND NEW.unit<>OLD.unit) THEN
    RAISE EXCEPTION 'Clear stock before deactivating material or changing its unit' USING ERRCODE='23514'; END IF;
  END IF;
 ELSIF TG_TABLE_NAME='warehouse' THEN
  IF btrim(NEW.warehouse_name)='' THEN RAISE EXCEPTION 'Empty warehouse' USING ERRCODE='23514'; END IF;
  IF (TG_OP='INSERT' OR NEW.status='ACTIVE') AND NOT EXISTS(SELECT 1 FROM public.farm WHERE farm_id=NEW.farm_id AND status='ACTIVE') THEN
   RAISE EXCEPTION 'Farm is missing or inactive' USING ERRCODE='23514'; END IF;
  IF TG_OP='UPDATE' AND NEW.farm_id<>OLD.farm_id THEN RAISE EXCEPTION 'Warehouse farm cannot change' USING ERRCODE='23514'; END IF;
  IF NEW.status='INACTIVE' AND EXISTS(SELECT 1 FROM public.warehouse_inventory WHERE warehouse_id=NEW.warehouse_id AND quantity>0) THEN
   RAISE EXCEPTION 'Warehouse has stock' USING ERRCODE='23514'; END IF;
 ELSE
  IF NEW.quantity IS NULL OR NEW.quantity<0 OR NEW.quantity>999999999.999 THEN
   RAISE EXCEPTION 'Invalid stock quantity' USING ERRCODE='23514'; END IF;
  IF TG_OP='UPDATE' AND (NEW.warehouse_id<>OLD.warehouse_id OR NEW.material_id<>OLD.material_id) THEN
   RAISE EXCEPTION 'Inventory relationships cannot change' USING ERRCODE='23514'; END IF;
  IF NOT EXISTS(SELECT 1 FROM public.warehouse w JOIN public.farm f USING(farm_id) WHERE w.warehouse_id=NEW.warehouse_id AND w.status='ACTIVE' AND f.status='ACTIVE')
    OR NOT EXISTS(SELECT 1 FROM public.material m JOIN public.material_category c USING(category_id) WHERE m.material_id=NEW.material_id AND m.status='ACTIVE' AND c.status='ACTIVE') THEN
   RAISE EXCEPTION 'Inventory requires active warehouse, farm, material and category' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
DO $$ DECLARE t TEXT; BEGIN
 FOREACH t IN ARRAY ARRAY['farm','plot','crop','crop_season','material_category','material','warehouse','warehouse_inventory'] LOOP
  EXECUTE format('CREATE OR REPLACE TRIGGER management_write_lock BEFORE INSERT OR UPDATE OR DELETE ON public.%I FOR EACH STATEMENT EXECUTE FUNCTION public.lock_farm_management()',t);
  EXECUTE format('CREATE OR REPLACE TRIGGER management_guard BEFORE INSERT OR UPDATE ON public.%I FOR EACH ROW EXECUTE FUNCTION public.%I()',t,
    CASE WHEN t IN ('farm','plot','crop','crop_season') THEN 'fpc_guard' ELSE 'inventory_guard' END);
 END LOOP;
END $$;
COMMIT;
