-- Run as database owner after migration 10. No fake task, purchase or ledger data.
BEGIN;
ALTER TABLE public.crop_season ADD COLUMN IF NOT EXISTS expected_yield NUMERIC(16,3) CHECK(expected_yield>=0);
CREATE INDEX IF NOT EXISTS ix_harvest_confirmed_period ON public.harvest(harvest_date,season_id) WHERE record_status='CONFIRMED';
CREATE TABLE IF NOT EXISTS public.production_report_snapshot (
 report_id UUID PRIMARY KEY,
 farm_id INTEGER NOT NULL REFERENCES public.farm(farm_id),
 user_id INTEGER NOT NULL REFERENCES public.app_user(user_id),
 generated_at TIMESTAMPTZ NOT NULL,
 expires_at TIMESTAMPTZ NOT NULL,
 payload JSONB NOT NULL,
 CHECK(expires_at>generated_at)
);
CREATE INDEX IF NOT EXISTS ix_report_expiry ON public.production_report_snapshot(expires_at);
INSERT INTO public.auth_permission VALUES
 ('dashboard:read','Read dashboard in assigned farms'),
 ('reports:read','Create and read own production reports'),
 ('reports:export:pdf','Export own production reports as PDF'),
 ('reports:export:xlsx','Export own production reports as Excel'),
 ('expenses:read','Read purchase value summary in assigned farms')
ON CONFLICT(permission_code) DO NOTHING;
GRANT SELECT,INSERT ON public.production_report_snapshot TO farm_app;
-- Explicit permission grants and FARM memberships remain an administrator decision.
COMMIT;
