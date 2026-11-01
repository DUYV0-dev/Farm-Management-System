-- Additive upgrade after the complete legacy schema. Run as database owner.
CREATE TABLE IF NOT EXISTS public.farming_task (
 task_id SERIAL PRIMARY KEY, farm_id INTEGER NOT NULL REFERENCES public.farm,
 season_id INTEGER NOT NULL REFERENCES public.crop_season, title VARCHAR(200) NOT NULL CHECK(length(btrim(title))>0),
 assigned_to INTEGER NOT NULL REFERENCES public.app_user, category VARCHAR(30) NOT NULL DEFAULT 'OTHER',
 start_date DATE NOT NULL, due_date DATE NOT NULL CHECK(due_date>=start_date),
 status VARCHAR(20) NOT NULL DEFAULT 'TODO' CHECK(status IN ('TODO','IN_PROGRESS','COMPLETED','CANCELLED')),
 progress INTEGER NOT NULL DEFAULT 0 CHECK(progress BETWEEN 0 AND 100),
 description VARCHAR(2000) NOT NULL DEFAULT '', version INTEGER NOT NULL DEFAULT 0,
 created_by INTEGER NOT NULL REFERENCES public.app_user, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), CHECK((status='COMPLETED')=(progress=100))
);
CREATE INDEX IF NOT EXISTS ix_task_farm_due ON public.farming_task(farm_id,due_date,task_id);
CREATE TABLE IF NOT EXISTS public.task_event (
 event_id BIGSERIAL PRIMARY KEY, task_id INTEGER NOT NULL REFERENCES public.farming_task,
 actor_id INTEGER NOT NULL REFERENCES public.app_user, note VARCHAR(2000) NOT NULL,
 event_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS public.notification_read (
 farm_id INTEGER NOT NULL REFERENCES public.farm, user_id INTEGER NOT NULL REFERENCES public.app_user,
 notification_key VARCHAR(100) NOT NULL, read_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY(farm_id,user_id,notification_key)
);
CREATE TABLE IF NOT EXISTS public.crop_growth (
 growth_id BIGSERIAL PRIMARY KEY, season_id INTEGER NOT NULL REFERENCES public.crop_season,
 stage VARCHAR(100) NOT NULL, observed_date DATE NOT NULL, notes VARCHAR(2000) NOT NULL,
 created_by INTEGER NOT NULL REFERENCES public.app_user, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE public.crop_growth ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_scope ON public.crop_growth;
CREATE POLICY tenant_scope ON public.crop_growth USING(EXISTS(SELECT 1 FROM public.crop_season s WHERE s.season_id=crop_growth.season_id));
GRANT SELECT,INSERT ON public.crop_growth TO farm_app;
CREATE TABLE IF NOT EXISTS public.stock_request (
 farm_id INTEGER NOT NULL REFERENCES public.farm, request_key UUID NOT NULL,
 payload TEXT NOT NULL, created_by INTEGER NOT NULL REFERENCES public.app_user,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY(farm_id,request_key)
);
CREATE TABLE IF NOT EXISTS public.inventory_movement (
 movement_id BIGSERIAL PRIMARY KEY, warehouse_id INTEGER NOT NULL REFERENCES public.warehouse,
 material_id INTEGER NOT NULL REFERENCES public.material, delta NUMERIC(16,3) NOT NULL,
 balance NUMERIC(16,3) NOT NULL CHECK(balance>=0), actor_id INTEGER REFERENCES public.app_user,
 reason VARCHAR(200) NOT NULL, event_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE public.material_usage ADD COLUMN IF NOT EXISTS warehouse_id INTEGER REFERENCES public.warehouse;
ALTER TABLE public.material_usage ADD COLUMN IF NOT EXISTS request_key UUID UNIQUE;
GRANT UPDATE(warehouse_id,request_key) ON public.material_usage TO farm_app;
CREATE OR REPLACE FUNCTION public.record_inventory_movement() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
DECLARE previous NUMERIC;
BEGIN
 previous:=CASE WHEN TG_OP='INSERT' THEN 0 ELSE OLD.quantity END;
 IF NEW.quantity<>previous THEN
 INSERT INTO public.inventory_movement(warehouse_id,material_id,delta,balance,actor_id,reason)
 VALUES(NEW.warehouse_id,NEW.material_id,NEW.quantity-previous,NEW.quantity,
 NULLIF(current_setting('app.user_id',true),'')::INTEGER,
 COALESCE(NULLIF(current_setting('app.stock_reason',true),''),'INVENTORY_UPDATE'));
 END IF;
 RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION public.record_inventory_movement() FROM PUBLIC;
DROP TRIGGER IF EXISTS inventory_movement_audit ON public.warehouse_inventory;
CREATE TRIGGER inventory_movement_audit AFTER INSERT OR UPDATE OF quantity ON public.warehouse_inventory
 FOR EACH ROW EXECUTE FUNCTION public.record_inventory_movement();

-- Every business table is tenant protected, including modules merged after tenancy v12.
ALTER TABLE public.supplier ADD COLUMN IF NOT EXISTS tenant_farm_id INTEGER REFERENCES public.farm DEFAULT public.current_farm_id();
ALTER TABLE public.customer ADD COLUMN IF NOT EXISTS tenant_farm_id INTEGER REFERENCES public.farm DEFAULT public.current_farm_id();
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM public.supplier WHERE tenant_farm_id IS NULL) OR EXISTS(SELECT 1 FROM public.customer WHERE tenant_farm_id IS NULL) THEN
 RAISE EXCEPTION 'Legacy suppliers/customers need reviewed tenant_farm_id assignments before migration 17';
 END IF;
END $$;
DROP INDEX IF EXISTS public.uq_supplier_name;
DROP INDEX IF EXISTS public.uq_customer_name;
CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_supplier ON public.supplier(COALESCE(tenant_farm_id,0),lower(btrim(supplier_name)));
CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_customer ON public.customer(COALESCE(tenant_farm_id,0),lower(btrim(customer_name)));
DO $$ DECLARE t TEXT; BEGIN
 FOREACH t IN ARRAY ARRAY['supplier','customer'] LOOP
 EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY',t);
 EXECUTE format('DROP POLICY IF EXISTS tenant_scope ON public.%I',t);
 EXECUTE format('CREATE POLICY tenant_scope ON public.%I USING (public.can_access_farm(tenant_farm_id)) WITH CHECK(public.can_access_farm(tenant_farm_id))',t);
 END LOOP;
 FOREACH t IN ARRAY ARRAY['crop_production','purchase_order','sales_order','farming_task','stock_request','production_report_snapshot'] LOOP
 EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY',t);
 EXECUTE format('DROP POLICY IF EXISTS tenant_scope ON public.%I',t);
 EXECUTE format('CREATE POLICY tenant_scope ON public.%I USING (public.can_access_farm(farm_id)) WITH CHECK(public.can_access_farm(farm_id))',t);
 END LOOP;
END $$;
ALTER TABLE public.harvest ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_scope ON public.harvest;
CREATE POLICY tenant_scope ON public.harvest USING(EXISTS(SELECT 1 FROM public.crop_season s WHERE s.season_id=harvest.season_id));
ALTER TABLE public.task_event ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_scope ON public.task_event;
CREATE POLICY tenant_scope ON public.task_event USING(EXISTS(SELECT 1 FROM public.farming_task t WHERE t.task_id=task_event.task_id));
ALTER TABLE public.notification_read ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_scope ON public.notification_read;
CREATE POLICY tenant_scope ON public.notification_read USING(public.can_access_farm(farm_id) AND user_id=NULLIF(current_setting('app.user_id',true),'')::INTEGER);
ALTER TABLE public.inventory_movement ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_scope ON public.inventory_movement;
CREATE POLICY tenant_scope ON public.inventory_movement USING(EXISTS(SELECT 1 FROM public.warehouse w WHERE w.warehouse_id=inventory_movement.warehouse_id));
ALTER TABLE public.purchase_order_item ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_scope ON public.purchase_order_item;
CREATE POLICY tenant_scope ON public.purchase_order_item USING(EXISTS(SELECT 1 FROM public.purchase_order o WHERE o.po_id=purchase_order_item.po_id))
 WITH CHECK(EXISTS(SELECT 1 FROM public.purchase_order o WHERE o.po_id=purchase_order_item.po_id) AND EXISTS(SELECT 1 FROM public.material m WHERE m.material_id=purchase_order_item.material_id));
ALTER TABLE public.sales_order_item ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_scope ON public.sales_order_item;
CREATE POLICY tenant_scope ON public.sales_order_item USING(EXISTS(SELECT 1 FROM public.sales_order o WHERE o.so_id=sales_order_item.so_id))
 WITH CHECK(EXISTS(SELECT 1 FROM public.sales_order o WHERE o.so_id=sales_order_item.so_id) AND EXISTS(SELECT 1 FROM public.harvested_product p WHERE p.product_id=sales_order_item.product_id));

-- A harvest produces one saleable lot with a durable link back to the season.
ALTER TABLE public.crop_production ADD COLUMN IF NOT EXISTS season_id INTEGER UNIQUE REFERENCES public.crop_season;
ALTER TABLE public.harvested_product ADD COLUMN IF NOT EXISTS harvest_id INTEGER UNIQUE REFERENCES public.harvest;
ALTER TABLE public.harvested_product ALTER COLUMN quantity TYPE NUMERIC(16,3);
ALTER TABLE public.sales_order_item ALTER COLUMN quantity TYPE NUMERIC(16,3);
ALTER TABLE public.crop_production ALTER COLUMN expected_quantity TYPE NUMERIC(16,3);
ALTER TABLE public.crop_production ALTER COLUMN actual_quantity TYPE NUMERIC(16,3);
ALTER TABLE public.harvested_product DROP CONSTRAINT IF EXISTS ck_product_stock;
ALTER TABLE public.harvested_product ADD CONSTRAINT ck_product_stock CHECK(quantity IS NOT NULL AND quantity>=0) NOT VALID;
CREATE OR REPLACE FUNCTION public.harvest_create_lot() RETURNS trigger
LANGUAGE plpgsql SET search_path=pg_catalog,public AS $$
DECLARE production INTEGER;
BEGIN
 INSERT INTO public.crop_production(farm_id,crop_name,season_name,season_id,actual_quantity,unit,status)
 SELECT p.farm_id,c.crop_name,s.season_name,s.season_id,NEW.quantity,u.unit_name,'HARVESTING'
 FROM public.crop_season s JOIN public.plot p USING(plot_id) JOIN public.crop c USING(crop_id)
 JOIN public.unit u ON u.unit_id=s.yield_unit_id WHERE s.season_id=NEW.season_id
 ON CONFLICT(season_id) DO UPDATE SET actual_quantity=COALESCE(crop_production.actual_quantity,0)+EXCLUDED.actual_quantity,updated_at=now()
 RETURNING production_id INTO production;
 INSERT INTO public.harvested_product(production_id,harvest_id,product_name,quantity,quality,status)
 SELECT production,NEW.harvest_id,crop_name,NEW.quantity,NEW.quality,'AVAILABLE' FROM public.crop_production WHERE production_id=production;
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS harvest_saleable_lot ON public.harvest;
CREATE TRIGGER harvest_saleable_lot AFTER INSERT ON public.harvest FOR EACH ROW EXECUTE FUNCTION public.harvest_create_lot();
GRANT SELECT,INSERT,UPDATE,DELETE ON public.crop_production,public.harvested_product TO farm_app;
GRANT SELECT,INSERT,UPDATE ON public.farming_task,public.notification_read TO farm_app;
GRANT SELECT,INSERT ON public.task_event,public.stock_request TO farm_app;
GRANT SELECT ON public.inventory_movement TO farm_app;
GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO farm_app;
INSERT INTO public.auth_permission VALUES
 ('tasks:read','View farming tasks and calendar'),('tasks:create','Assign farming tasks'),
 ('tasks:update','Update progress and task history'),('notifications:read','Read operational reminders'),
 ('traceability:read','Read season to harvest traceability') ON CONFLICT DO NOTHING;
INSERT INTO public.farm_job_permission SELECT job,permission_code FROM public.auth_permission CROSS JOIN
 (VALUES('OWNER'),('MANAGER')) jobs(job)
 WHERE permission_code NOT LIKE 'users:%' AND permission_code NOT LIKE 'roles:%' AND permission_code<>'farms:create'
 ON CONFLICT DO NOTHING;
INSERT INTO public.farm_job_permission SELECT job,permission_code FROM public.auth_permission CROSS JOIN
 (VALUES('AGRONOMIST'),('WORKER'),('WAREHOUSE'),('ACCOUNTANT')) jobs(job)
 WHERE permission_code IN ('tasks:read','notifications:read','traceability:read')
 OR (job IN ('AGRONOMIST','WORKER') AND permission_code='tasks:update')
 OR (job='AGRONOMIST' AND permission_code='tasks:create')
 OR (job='WAREHOUSE' AND permission_code ~ '^(suppliers|purchases):')
 OR (job='ACCOUNTANT' AND permission_code ~ '^(customers|sales|reports|dashboard):') ON CONFLICT DO NOTHING;
CREATE OR REPLACE FUNCTION public.task_relationship_guard() RETURNS trigger
LANGUAGE plpgsql AS $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM public.crop_season s JOIN public.plot p USING(plot_id) WHERE s.season_id=NEW.season_id AND p.farm_id=NEW.farm_id)
 OR NOT EXISTS(SELECT 1 FROM public.farm_member m JOIN public.app_user u USING(user_id) WHERE m.farm_id=NEW.farm_id AND m.user_id=NEW.assigned_to AND m.status='ACTIVE' AND u.status='ACTIVE') THEN
 RAISE EXCEPTION 'Task requires season and active assignee in the same farm' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS task_relationships ON public.farming_task;
CREATE TRIGGER task_relationships BEFORE INSERT OR UPDATE ON public.farming_task FOR EACH ROW EXECUTE FUNCTION public.task_relationship_guard();
