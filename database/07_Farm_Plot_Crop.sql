-- Run once as the database owner, after 06_User_Role_Management.sql.
-- Adds: farm, plot, crop tables + farm_member/farm_member_role if not existing.
-- Adds farm/plot/crop permissions to auth_permission.
BEGIN;

-- ─── Farm table ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.farm (
    farm_id     SERIAL PRIMARY KEY,
    farm_name   VARCHAR(150) NOT NULL,
    address     VARCHAR(255) NOT NULL,
    area_ha     NUMERIC(10,2) NOT NULL CHECK (area_ha > 0 AND area_ha <= 9999999),
    description VARCHAR(1000),
    status      VARCHAR(10) NOT NULL DEFAULT 'ACTIVE'
                CHECK (status IN ('ACTIVE','INACTIVE')),
    created_by  INTEGER REFERENCES public.app_user(user_id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_farm_normalized_name ON public.farm(lower(btrim(farm_name)));

-- ─── Plot (khu vực canh tác) ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.plot (
    plot_id     SERIAL PRIMARY KEY,
    farm_id     INTEGER NOT NULL REFERENCES public.farm(farm_id) ON DELETE RESTRICT,
    plot_name   VARCHAR(150) NOT NULL,
    area_ha     NUMERIC(10,2) NOT NULL CHECK (area_ha > 0 AND area_ha <= 9999999),
    soil_type   VARCHAR(100),
    location    VARCHAR(255),
    description VARCHAR(1000),
    status      VARCHAR(10) NOT NULL DEFAULT 'ACTIVE'
                CHECK (status IN ('ACTIVE','INACTIVE')),
    created_by  INTEGER REFERENCES public.app_user(user_id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (farm_id, plot_name)
);

-- ─── Crop (cây trồng) ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.crop (
    crop_id SERIAL PRIMARY KEY, crop_name VARCHAR(100) NOT NULL,
    scientific_name VARCHAR(150), category VARCHAR(100),
    growth_days INTEGER CHECK (growth_days > 0 AND growth_days <= 9999),
    description VARCHAR(1000),
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS public.crop_season (
    season_id SERIAL PRIMARY KEY,
    plot_id INTEGER NOT NULL REFERENCES public.plot(plot_id) ON DELETE RESTRICT,
    crop_id INTEGER NOT NULL REFERENCES public.crop(crop_id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED'
        CHECK (status IN ('PLANNED','IN_PROGRESS','COMPLETED','CANCELLED'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_crop_name ON public.crop(lower(btrim(crop_name)));
CREATE UNIQUE INDEX IF NOT EXISTS uq_plot_name ON public.plot(farm_id,lower(btrim(plot_name)));
CREATE INDEX IF NOT EXISTS ix_season_plot ON public.crop_season(plot_id,status);
CREATE INDEX IF NOT EXISTS ix_season_crop ON public.crop_season(crop_id,status);

CREATE TABLE IF NOT EXISTS public.farm_member (
    farm_id     INTEGER NOT NULL REFERENCES public.farm(farm_id) ON DELETE CASCADE,
    user_id     INTEGER NOT NULL REFERENCES public.app_user(user_id) ON DELETE CASCADE,
    status      VARCHAR(10) NOT NULL DEFAULT 'ACTIVE'
                CHECK (status IN ('ACTIVE','INACTIVE')),
    joined_at   DATE NOT NULL DEFAULT CURRENT_DATE,
    PRIMARY KEY (farm_id, user_id)
);

CREATE TABLE IF NOT EXISTS public.farm_member_role (
    farm_id     INTEGER NOT NULL,
    user_id     INTEGER NOT NULL,
    role_id     INTEGER NOT NULL REFERENCES public.role(role_id) ON DELETE CASCADE,
    PRIMARY KEY (farm_id, user_id, role_id),
    FOREIGN KEY (farm_id, user_id) REFERENCES public.farm_member(farm_id, user_id) ON DELETE CASCADE
);

-- ─── Permissions ─────────────────────────────────────────────────────────────
INSERT INTO public.auth_permission VALUES
 ('farms:read',    'View and search farms')
,('farms:create',  'Create new farms')
,('farms:update',  'Update farm information')
,('farms:status',  'Activate or deactivate farms')
,('plots:read',    'View and search plots')
,('plots:create',  'Create new plots')
,('plots:update',  'Update plot information')
,('plots:status',  'Activate or deactivate plots')
,('crops:read',    'View and search crops')
,('crops:create',  'Create new crops')
,('crops:update',  'Update crop information')
,('crops:status',  'Delete or deactivate crops')
ON CONFLICT (permission_code) DO NOTHING;

-- Grant all new permissions to SYSTEM_ADMIN
INSERT INTO public.auth_role_permission(role_id, permission_code)
SELECT r.role_id, p.permission_code
FROM public.role r CROSS JOIN public.auth_permission p
WHERE r.scope = 'SYSTEM' AND r.role_name = 'SYSTEM_ADMIN'
ON CONFLICT DO NOTHING;

-- ─── GRANTS ──────────────────────────────────────────────────────────────────
GRANT SELECT, INSERT, UPDATE ON public.farm TO farm_app;
GRANT SELECT, INSERT, UPDATE ON public.plot TO farm_app;
GRANT SELECT, INSERT, UPDATE ON public.crop, public.crop_season TO farm_app;
GRANT SELECT, INSERT, UPDATE ON public.farm_member TO farm_app;
GRANT SELECT, INSERT, DELETE ON public.farm_member_role TO farm_app;

-- Sequences
DO $$
DECLARE seq TEXT;
BEGIN
    FOREACH seq IN ARRAY ARRAY[
        pg_get_serial_sequence('public.farm',  'farm_id'),
        pg_get_serial_sequence('public.plot',  'plot_id'),
        pg_get_serial_sequence('public.crop',  'crop_id'),
        pg_get_serial_sequence('public.crop_season', 'season_id')
    ] LOOP
        IF seq IS NOT NULL THEN
            EXECUTE format('GRANT USAGE, SELECT ON SEQUENCE %s TO farm_app', seq);
        END IF;
    END LOOP;
END $$;

COMMIT;
