-- Run as database owner against ERD v1 after auth migrations 05/06.
-- Existing catalog: material.unit_id -> unit.unit_id, material.category.
-- Independent of the alternate warehouse schema in scripts 08/09.
BEGIN;

-- Fail with an actionable message before creating anything on an incompatible schema.
DO $$
BEGIN
    IF to_regclass('public.material') IS NULL OR to_regclass('public.unit') IS NULL
            OR to_regclass('public.crop_season') IS NULL THEN
        RAISE EXCEPTION 'Material usage requires ERD v1 tables material, unit and crop_season';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema='public' AND table_name='material' AND column_name='unit_id') THEN
        RAISE EXCEPTION 'Expected ERD v1 material.unit_id referencing unit.unit_id';
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS public.material_usage (
    usage_id SERIAL PRIMARY KEY,
    season_id INTEGER NOT NULL REFERENCES public.crop_season(season_id) ON DELETE RESTRICT,
    material_id INTEGER NOT NULL REFERENCES public.material(material_id) ON DELETE RESTRICT,
    material_name VARCHAR(150) NOT NULL CHECK (btrim(material_name) <> ''),
    usage_type VARCHAR(20) NOT NULL CHECK (usage_type IN ('FERTILIZER','PESTICIDE')),
    quantity NUMERIC(15,3) NOT NULL CHECK (quantity > 0 AND quantity <= 999999999999.999),
    unit VARCHAR(50) NOT NULL CHECK (btrim(unit) <> ''),
    application_date DATE NOT NULL CHECK (application_date BETWEEN DATE '0001-01-01' AND DATE '9999-12-31'),
    notes VARCHAR(1000),
    created_by INTEGER REFERENCES public.app_user(user_id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_usage_season_date ON public.material_usage(season_id,application_date DESC,usage_id DESC);
CREATE INDEX IF NOT EXISTS ix_usage_material_date ON public.material_usage(material_id,application_date DESC,usage_id DESC);
CREATE INDEX IF NOT EXISTS ix_usage_type_date ON public.material_usage(usage_type,application_date DESC,usage_id DESC);
CREATE INDEX IF NOT EXISTS ix_usage_date ON public.material_usage(application_date DESC,usage_id DESC);

-- Declare the trigger function here so this migration does not depend on script 09.
CREATE OR REPLACE FUNCTION public.lock_material_usage() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM pg_advisory_xact_lock(740207);
    RETURN NULL;
END $$;

-- The owner acquires parent row locks; farm_app only needs SELECT on the catalogs.
CREATE OR REPLACE FUNCTION public.material_usage_guard() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public AS $$
BEGIN
    IF NEW.application_date > (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Bangkok')::date THEN
        RAISE EXCEPTION 'Usage date cannot be in the future' USING ERRCODE='23514';
    END IF;
    PERFORM s.season_id FROM public.crop_season s JOIN public.plot p USING(plot_id)
        JOIN public.farm f USING(farm_id) JOIN public.crop c USING(crop_id)
        WHERE s.season_id=NEW.season_id AND s.status IN ('IN_PROGRESS','COMPLETED')
            AND p.status='ACTIVE' AND f.status='ACTIVE' AND c.status='ACTIVE'
        FOR SHARE OF s,p,f,c;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Usage requires an available season' USING ERRCODE='23514';
    END IF;
    SELECT m.material_name,u.unit_code INTO NEW.material_name,NEW.unit
    FROM public.material m JOIN public.unit u USING(unit_id)
    WHERE m.material_id=NEW.material_id AND m.status='ACTIVE'
        FOR SHARE OF m,u;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Usage requires active material and valid unit' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;

REVOKE ALL ON FUNCTION public.material_usage_guard() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.material_usage_guard() TO farm_app;

CREATE OR REPLACE TRIGGER management_write_lock BEFORE INSERT ON public.material_usage
    FOR EACH STATEMENT EXECUTE FUNCTION public.lock_material_usage();
CREATE OR REPLACE TRIGGER material_usage_guard BEFORE INSERT ON public.material_usage
    FOR EACH ROW EXECUTE FUNCTION public.material_usage_guard();

INSERT INTO public.auth_permission(permission_code,description) VALUES
    ('usage:read','View fertilizer and pesticide usage history and totals'),
    ('usage:create','Record fertilizer and pesticide applications')
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO public.auth_role_permission(role_id,permission_code)
SELECT r.role_id,p.permission_code FROM public.role r CROSS JOIN public.auth_permission p
WHERE r.scope='SYSTEM' AND r.role_name='SYSTEM_ADMIN' AND p.permission_code LIKE 'usage:%'
ON CONFLICT DO NOTHING;

GRANT SELECT,INSERT ON public.material_usage TO farm_app;
GRANT SELECT ON public.material,public.unit,public.crop_season,public.plot,public.farm,public.crop TO farm_app;
DO $$ DECLARE seq TEXT; BEGIN
    seq := pg_get_serial_sequence('public.material_usage','usage_id');
    IF seq IS NOT NULL THEN EXECUTE format('GRANT USAGE,SELECT ON SEQUENCE %s TO farm_app',seq); END IF;
END $$;
COMMIT;
