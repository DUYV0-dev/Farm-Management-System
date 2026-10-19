-- Warehouse schema (08/09) counterpart of ERD-v1 migration 11.
BEGIN;
CREATE TABLE IF NOT EXISTS public.material_usage (
 usage_id SERIAL PRIMARY KEY,
 season_id INTEGER NOT NULL REFERENCES public.crop_season,
 material_id INTEGER NOT NULL REFERENCES public.material,
 material_name VARCHAR(150) NOT NULL,
 usage_type VARCHAR(20) NOT NULL CHECK(usage_type IN ('FERTILIZER','PESTICIDE')),
 quantity NUMERIC(15,3) NOT NULL CHECK(quantity>0), unit VARCHAR(50) NOT NULL,
 application_date DATE NOT NULL, notes VARCHAR(1000),
 created_by INTEGER REFERENCES public.app_user,created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_usage_season_date ON public.material_usage(season_id,application_date DESC);
CREATE OR REPLACE FUNCTION public.material_usage_warehouse_guard() RETURNS trigger
LANGUAGE plpgsql AS $$ BEGIN
 PERFORM pg_advisory_xact_lock(740207);
 IF NEW.application_date>(CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Bangkok')::date THEN
 RAISE EXCEPTION 'Usage date cannot be in the future' USING ERRCODE='23514'; END IF;
 PERFORM s.season_id FROM public.crop_season s JOIN public.plot p USING(plot_id)
 JOIN public.farm f USING(farm_id) JOIN public.crop c USING(crop_id)
 WHERE s.season_id=NEW.season_id AND s.status IN ('IN_PROGRESS','COMPLETED')
 AND p.status='ACTIVE' AND f.status='ACTIVE' AND c.status='ACTIVE' FOR SHARE OF s,p,f,c;
 IF NOT FOUND THEN RAISE EXCEPTION 'Usage requires an available season' USING ERRCODE='23514'; END IF;
 SELECT m.material_name,m.unit INTO NEW.material_name,NEW.unit FROM public.material m
 JOIN public.material_category c USING(category_id) WHERE m.material_id=NEW.material_id
 AND m.status='ACTIVE' AND c.status='ACTIVE' FOR SHARE OF m,c;
 IF NOT FOUND THEN RAISE EXCEPTION 'Usage requires active material and valid unit' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS material_usage_guard ON public.material_usage;
CREATE TRIGGER material_usage_guard BEFORE INSERT ON public.material_usage
 FOR EACH ROW EXECUTE FUNCTION public.material_usage_warehouse_guard();
INSERT INTO public.auth_permission VALUES ('usage:read','View material applications'),('usage:create','Record material applications') ON CONFLICT DO NOTHING;
GRANT SELECT,INSERT ON public.material_usage TO farm_app;
GRANT USAGE,SELECT ON SEQUENCE public.material_usage_usage_id_seq TO farm_app;
COMMIT;
