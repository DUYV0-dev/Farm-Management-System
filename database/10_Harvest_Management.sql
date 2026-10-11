-- Run as database owner after 05, 06, 07_Farm_Plot_Crop, 08 and 09.
-- Repeatable; existing seasons keep NULL metadata until configured by a manager.
BEGIN;
CREATE TABLE IF NOT EXISTS public.unit (
    unit_id SERIAL PRIMARY KEY,
    unit_code VARCHAR(20) NOT NULL UNIQUE,
    unit_name VARCHAR(50) NOT NULL
);
INSERT INTO public.unit(unit_code,unit_name) VALUES ('kg','kg'),('ton','Tấn')
ON CONFLICT(unit_code) DO NOTHING;
ALTER TABLE public.crop_season ADD COLUMN IF NOT EXISTS season_name VARCHAR(100);
ALTER TABLE public.crop_season ADD COLUMN IF NOT EXISTS planned_start_date DATE;
ALTER TABLE public.crop_season ADD COLUMN IF NOT EXISTS planned_end_date DATE;
ALTER TABLE public.crop_season ADD COLUMN IF NOT EXISTS actual_start_date DATE;
ALTER TABLE public.crop_season ADD COLUMN IF NOT EXISTS actual_end_date DATE;
ALTER TABLE public.crop_season ADD COLUMN IF NOT EXISTS yield_unit_id INTEGER REFERENCES public.unit(unit_id);
ALTER TABLE public.crop_season ADD COLUMN IF NOT EXISTS record_status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED';
CREATE TABLE IF NOT EXISTS public.harvest (
    harvest_id SERIAL PRIMARY KEY,
    season_id INTEGER NOT NULL REFERENCES public.crop_season(season_id) ON DELETE RESTRICT,
    created_by INTEGER NOT NULL REFERENCES public.app_user(user_id) ON DELETE RESTRICT,
    harvest_date DATE NOT NULL,
    quantity NUMERIC(16,3) NOT NULL CHECK(quantity>0),
    quality VARCHAR(50),
    record_status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED' CHECK(record_status IN ('DRAFT','CONFIRMED','CANCELED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_harvest_history ON public.harvest(season_id,harvest_date DESC,harvest_id DESC);
-- The current six-function implementation confirms immediately; records are immutable.
CREATE OR REPLACE FUNCTION public.harvest_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE s public.crop_season%ROWTYPE; first_day DATE; last_day DATE;
BEGIN
 IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Confirmed harvest history is immutable' USING ERRCODE='23514'; END IF;
 SELECT * INTO s FROM public.crop_season WHERE season_id=NEW.season_id FOR UPDATE;
 first_day:=COALESCE(s.actual_start_date,s.planned_start_date);
 last_day:=COALESCE(s.actual_end_date,s.planned_end_date);
 IF s.status<>'IN_PROGRESS' OR s.record_status<>'CONFIRMED' OR first_day IS NULL OR last_day IS NULL OR s.yield_unit_id IS NULL
 OR NEW.harvest_date<first_day OR NEW.harvest_date>last_day
 OR NEW.harvest_date>(CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::date THEN
  RAISE EXCEPTION 'Harvest requires a configured active season and valid date' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE OR REPLACE TRIGGER harvest_write_lock BEFORE INSERT OR UPDATE OR DELETE ON public.harvest
 FOR EACH STATEMENT EXECUTE FUNCTION public.lock_farm_management();
CREATE OR REPLACE TRIGGER harvest_validate BEFORE INSERT OR UPDATE OR DELETE ON public.harvest
 FOR EACH ROW EXECUTE FUNCTION public.harvest_guard();
CREATE OR REPLACE FUNCTION public.harvest_season_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.planned_start_date IS NOT NULL AND NEW.planned_end_date IS NOT NULL AND NEW.planned_end_date<=NEW.planned_start_date THEN
  RAISE EXCEPTION 'Invalid season dates' USING ERRCODE='23514';
 END IF;
 IF NEW.actual_end_date IS NOT NULL AND (NEW.actual_start_date IS NULL OR NEW.actual_end_date<NEW.actual_start_date) THEN
  RAISE EXCEPTION 'Invalid actual season dates' USING ERRCODE='23514';
 END IF;
 IF EXISTS(SELECT 1 FROM public.harvest WHERE season_id=NEW.season_id) AND
 (NEW.plot_id IS DISTINCT FROM OLD.plot_id OR NEW.crop_id IS DISTINCT FROM OLD.crop_id
 OR NEW.yield_unit_id IS DISTINCT FROM OLD.yield_unit_id OR NEW.season_name IS DISTINCT FROM OLD.season_name
 OR NEW.planned_start_date IS DISTINCT FROM OLD.planned_start_date OR NEW.planned_end_date IS DISTINCT FROM OLD.planned_end_date
 OR NEW.actual_start_date IS DISTINCT FROM OLD.actual_start_date OR NEW.actual_end_date IS DISTINCT FROM OLD.actual_end_date) THEN
  RAISE EXCEPTION 'Season metadata with harvest history is locked' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE OR REPLACE TRIGGER harvest_season_validate BEFORE UPDATE ON public.crop_season
 FOR EACH ROW EXECUTE FUNCTION public.harvest_season_guard();
INSERT INTO public.auth_permission VALUES ('harvests:read','View harvest history in assigned farms'),('harvests:create','Record confirmed harvests as farm manager')
ON CONFLICT(permission_code) DO NOTHING;
-- No automatic harvest permissions or farm-role assignments to existing users.
GRANT SELECT ON public.unit TO farm_app;
GRANT SELECT,INSERT ON public.harvest TO farm_app;
DO $$ DECLARE seq TEXT; BEGIN
 seq:=pg_get_serial_sequence('public.harvest','harvest_id');
 IF seq IS NOT NULL THEN EXECUTE format('GRANT USAGE,SELECT ON SEQUENCE %s TO farm_app',seq); END IF;
END $$;
COMMIT;
