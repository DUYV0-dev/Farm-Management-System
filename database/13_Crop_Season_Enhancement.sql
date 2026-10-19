-- ─── 13_Crop_Season_Enhancement.sql ─────────────────────────────────────────
-- Nâng cấp bảng crop_season: bổ sung tên mùa vụ, ngày bắt đầu, ngày kết thúc và ghi chú.

ALTER TABLE public.crop_season ADD COLUMN IF NOT EXISTS season_name VARCHAR(100);
ALTER TABLE public.crop_season ADD COLUMN IF NOT EXISTS start_date DATE;
ALTER TABLE public.crop_season ADD COLUMN IF NOT EXISTS end_date DATE;
ALTER TABLE public.crop_season ADD COLUMN IF NOT EXISTS description TEXT;

ALTER TABLE public.crop_season DROP CONSTRAINT IF EXISTS ck_season_dates;
ALTER TABLE public.crop_season ADD CONSTRAINT ck_season_dates 
    CHECK (start_date IS NULL OR end_date IS NULL OR start_date <= end_date);

CREATE INDEX IF NOT EXISTS ix_season_name ON public.crop_season(lower(btrim(season_name)));
CREATE INDEX IF NOT EXISTS ix_season_dates ON public.crop_season(start_date, end_date);

GRANT SELECT, INSERT, UPDATE, DELETE ON public.crop_season TO farm_app;
