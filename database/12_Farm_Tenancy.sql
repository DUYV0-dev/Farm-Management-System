-- Apply as database owner after 05/06/07/08/09/10. Preserve existing records.
BEGIN;
ALTER TABLE public.farm ADD COLUMN IF NOT EXISTS onboarding_status VARCHAR(20) NOT NULL DEFAULT 'READY';
ALTER TABLE public.farm ADD COLUMN IF NOT EXISTS owner_user_id INTEGER REFERENCES public.app_user(user_id);
INSERT INTO public.role(role_name,scope,description) VALUES
 ('OWNER','FARM','Chủ trang trại'),('MANAGER','FARM','Quản lý'),
 ('AGRONOMIST','FARM','Kỹ sư nông nghiệp'),('WAREHOUSE','FARM','Nhân viên kho'),
 ('ACCOUNTANT','FARM','Kế toán'),('WORKER','FARM','Nhân viên canh tác')
ON CONFLICT(scope,role_name) DO NOTHING;
CREATE TABLE IF NOT EXISTS public.farm_job_permission (
 role_name VARCHAR(50) NOT NULL, permission_code VARCHAR(80) NOT NULL REFERENCES public.auth_permission,
 PRIMARY KEY(role_name,permission_code)
);
INSERT INTO public.farm_job_permission
 SELECT 'OWNER',permission_code FROM public.auth_permission
 WHERE permission_code NOT LIKE 'users:%' AND permission_code NOT LIKE 'roles:%' AND permission_code <> 'farms:create'
ON CONFLICT DO NOTHING;
INSERT INTO public.farm_job_permission
 SELECT 'MANAGER',permission_code FROM public.farm_job_permission WHERE role_name='OWNER' AND permission_code NOT IN ('farms:status')
ON CONFLICT DO NOTHING;
INSERT INTO public.farm_job_permission
 SELECT job,permission_code FROM public.auth_permission CROSS JOIN (VALUES ('AGRONOMIST'),('WAREHOUSE'),('ACCOUNTANT'),('WORKER')) jobs(job)
 WHERE permission_code='farms:read'
 OR (job='AGRONOMIST' AND (permission_code LIKE 'plots:%' OR permission_code LIKE 'crops:%' OR permission_code LIKE 'usage:%'))
 OR (job='WAREHOUSE' AND (permission_code LIKE 'materials:%' OR permission_code LIKE 'warehouses:%' OR permission_code LIKE 'inventory:%' OR permission_code LIKE 'categories:%'))
 OR (job='ACCOUNTANT' AND permission_code LIKE 'expenses:%')
 OR (job='WORKER' AND permission_code IN ('plots:read','crops:read','usage:read'))
ON CONFLICT DO NOTHING;
GRANT SELECT ON public.farm_job_permission TO farm_app;
CREATE OR REPLACE FUNCTION public.workspace_memberships(target_user INTEGER)
RETURNS TABLE(farm_id INTEGER,farm_name VARCHAR,farm_status VARCHAR,member_status VARCHAR,onboarding_status VARCHAR,roles TEXT,permissions TEXT)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog,public AS $$
 SELECT f.farm_id,f.farm_name,f.status,m.status,f.onboarding_status,
 COALESCE(string_agg(DISTINCT r.role_name,',' ORDER BY r.role_name),''),
 COALESCE(string_agg(DISTINCT p.permission_code,',' ORDER BY p.permission_code),'')
 FROM public.farm_member m JOIN public.farm f USING(farm_id)
 LEFT JOIN public.farm_member_role mr ON mr.farm_id=m.farm_id AND mr.user_id=m.user_id
 LEFT JOIN public.role r ON r.role_id=mr.role_id AND r.scope='FARM'
 LEFT JOIN public.farm_job_permission p ON p.role_name=r.role_name
 WHERE m.user_id=target_user
 GROUP BY f.farm_id,f.farm_name,f.status,m.status,f.onboarding_status
$$;
REVOKE ALL ON FUNCTION public.workspace_memberships(INTEGER) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.workspace_memberships(INTEGER) TO farm_app;

-- Resolve memberships as owner to avoid recursion in row security policies.
CREATE OR REPLACE FUNCTION public.current_farm_id() RETURNS INTEGER
LANGUAGE sql STABLE AS $$ SELECT NULLIF(current_setting('app.farm_id',true),'')::INTEGER $$;
CREATE OR REPLACE FUNCTION public.can_access_farm(target INTEGER) RETURNS BOOLEAN
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog,public AS $$
 SELECT target=public.current_farm_id() AND EXISTS (
 SELECT 1 FROM public.farm f JOIN public.farm_member m USING(farm_id)
 JOIN public.app_user u USING(user_id)
 WHERE f.farm_id=target AND m.user_id=NULLIF(current_setting('app.user_id',true),'')::INTEGER
 AND m.status='ACTIVE' AND f.status='ACTIVE' AND u.status='ACTIVE')
$$;
REVOKE ALL ON FUNCTION public.can_access_farm(INTEGER) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.can_access_farm(INTEGER) TO farm_app;

-- Catalog entries created in the new flow belong to one farm. Existing global
-- entries remain readable, but are not editable by farm accounts.
ALTER TABLE public.crop ADD COLUMN IF NOT EXISTS tenant_farm_id INTEGER REFERENCES public.farm;
ALTER TABLE public.material_category ADD COLUMN IF NOT EXISTS tenant_farm_id INTEGER REFERENCES public.farm;
ALTER TABLE public.material ADD COLUMN IF NOT EXISTS tenant_farm_id INTEGER REFERENCES public.farm;
ALTER TABLE public.crop ALTER COLUMN tenant_farm_id SET DEFAULT public.current_farm_id();
ALTER TABLE public.material_category ALTER COLUMN tenant_farm_id SET DEFAULT public.current_farm_id();
ALTER TABLE public.material ALTER COLUMN tenant_farm_id SET DEFAULT public.current_farm_id();
DROP INDEX IF EXISTS public.uq_crop_name;
DROP INDEX IF EXISTS public.uq_material_name;
DROP INDEX IF EXISTS public.uq_category_name;
DROP INDEX IF EXISTS public.uq_material_category_name;
CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_crop_name ON public.crop(COALESCE(tenant_farm_id,0),lower(btrim(crop_name)));
CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_material_name ON public.material(COALESCE(tenant_farm_id,0),lower(btrim(material_name)));
CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_category_name ON public.material_category(COALESCE(tenant_farm_id,0),lower(btrim(category_name)));
DO $$ DECLARE t TEXT; BEGIN
 FOREACH t IN ARRAY ARRAY['crop','material_category','material'] LOOP
 EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY',t);
 EXECUTE format('DROP POLICY IF EXISTS tenant_read ON public.%I',t);
 EXECUTE format('DROP POLICY IF EXISTS tenant_write ON public.%I',t);
 EXECUTE format('CREATE POLICY tenant_read ON public.%I FOR SELECT USING (tenant_farm_id IS NULL OR public.can_access_farm(tenant_farm_id))',t);
 EXECUTE format('CREATE POLICY tenant_write ON public.%I FOR ALL USING (public.can_access_farm(tenant_farm_id)) WITH CHECK (public.can_access_farm(tenant_farm_id))',t);
 END LOOP;
 FOREACH t IN ARRAY ARRAY['plot','warehouse','farm_expense','crop_production'] LOOP
 IF to_regclass('public.'||t) IS NOT NULL THEN
 EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY',t);
 EXECUTE format('DROP POLICY IF EXISTS tenant_scope ON public.%I',t);
 EXECUTE format('CREATE POLICY tenant_scope ON public.%I USING (public.can_access_farm(farm_id)) WITH CHECK (public.can_access_farm(farm_id))',t);
 END IF; END LOOP;
END $$;
ALTER TABLE public.farm ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_scope ON public.farm;
CREATE POLICY tenant_scope ON public.farm USING (
 public.can_access_farm(farm_id) OR owner_user_id=NULLIF(current_setting('app.user_id',true),'')::INTEGER
) WITH CHECK (public.can_access_farm(farm_id) OR owner_user_id=NULLIF(current_setting('app.user_id',true),'')::INTEGER);
ALTER TABLE public.crop_season ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_scope ON public.crop_season;
CREATE POLICY tenant_scope ON public.crop_season USING (EXISTS(SELECT 1 FROM public.plot p WHERE p.plot_id=crop_season.plot_id))
 WITH CHECK (EXISTS(SELECT 1 FROM public.plot p WHERE p.plot_id=crop_season.plot_id) AND EXISTS(SELECT 1 FROM public.crop c WHERE c.crop_id=crop_season.crop_id));
ALTER TABLE public.warehouse_inventory ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_scope ON public.warehouse_inventory;
CREATE POLICY tenant_scope ON public.warehouse_inventory USING (EXISTS(SELECT 1 FROM public.warehouse w WHERE w.warehouse_id=warehouse_inventory.warehouse_id))
 WITH CHECK (EXISTS(SELECT 1 FROM public.warehouse w WHERE w.warehouse_id=warehouse_inventory.warehouse_id) AND EXISTS(SELECT 1 FROM public.material m WHERE m.material_id=warehouse_inventory.material_id));
DO $$ BEGIN
 IF to_regclass('public.harvested_product') IS NOT NULL THEN
 ALTER TABLE public.harvested_product ENABLE ROW LEVEL SECURITY;
 DROP POLICY IF EXISTS tenant_scope ON public.harvested_product;
 CREATE POLICY tenant_scope ON public.harvested_product USING (EXISTS(SELECT 1 FROM public.crop_production p WHERE p.production_id=harvested_product.production_id));
 END IF;
 IF to_regclass('public.material_usage') IS NOT NULL THEN
 ALTER TABLE public.material_usage ENABLE ROW LEVEL SECURITY;
 DROP POLICY IF EXISTS tenant_scope ON public.material_usage;
 CREATE POLICY tenant_scope ON public.material_usage USING (EXISTS(SELECT 1 FROM public.crop_season s WHERE s.season_id=material_usage.season_id))
 WITH CHECK (EXISTS(SELECT 1 FROM public.crop_season s WHERE s.season_id=material_usage.season_id) AND EXISTS(SELECT 1 FROM public.material m WHERE m.material_id=material_usage.material_id));
 END IF;
END $$;
COMMIT;
