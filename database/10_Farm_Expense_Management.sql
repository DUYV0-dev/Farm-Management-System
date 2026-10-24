-- Run as database owner after 09_Management_Integrity.sql.
BEGIN;

CREATE TABLE IF NOT EXISTS public.farm_expense (
    expense_id SERIAL PRIMARY KEY,
    farm_id INTEGER NOT NULL REFERENCES public.farm(farm_id) ON DELETE RESTRICT,
    season_id INTEGER REFERENCES public.crop_season(season_id) ON DELETE RESTRICT,
    category VARCHAR(30) NOT NULL CHECK (category IN (
        'SEEDS','FERTILIZER','PESTICIDES','LABOR','EQUIPMENT','IRRIGATION','TRANSPORT','OTHER')),
    amount NUMERIC(18,2) NOT NULL CHECK (amount > 0 AND amount <= 9999999999999999.99),
    expense_date DATE NOT NULL CHECK (expense_date BETWEEN DATE '0001-01-01' AND DATE '9999-12-31'),
    description VARCHAR(1000),
    created_by INTEGER REFERENCES public.app_user(user_id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_expense_farm_date ON public.farm_expense(farm_id,expense_date DESC,expense_id DESC);
CREATE INDEX IF NOT EXISTS ix_expense_season_date ON public.farm_expense(season_id,expense_date DESC,expense_id DESC);
CREATE INDEX IF NOT EXISTS ix_expense_category_date ON public.farm_expense(category,expense_date DESC);
CREATE INDEX IF NOT EXISTS ix_expense_date ON public.farm_expense(expense_date DESC,expense_id DESC);

-- Enforce the farm/season relationship even for writes outside the application.
CREATE OR REPLACE FUNCTION public.expense_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.season_id IS NOT NULL THEN
        PERFORM s.season_id FROM public.crop_season s JOIN public.plot p ON p.plot_id=s.plot_id
        WHERE s.season_id=NEW.season_id AND p.farm_id=NEW.farm_id FOR SHARE OF s,p;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'Expense season does not belong to farm' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS farm_expense_guard ON public.farm_expense;
CREATE TRIGGER farm_expense_guard BEFORE INSERT OR UPDATE ON public.farm_expense
    FOR EACH ROW EXECUTE FUNCTION public.expense_guard();

INSERT INTO public.auth_permission(permission_code,description) VALUES
    ('expenses:read','View expense categories, history and totals'),
    ('expenses:create','Record farm expenses')
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO public.auth_role_permission(role_id,permission_code)
SELECT r.role_id,p.permission_code FROM public.role r CROSS JOIN public.auth_permission p
WHERE r.scope='SYSTEM' AND r.role_name='SYSTEM_ADMIN' AND p.permission_code LIKE 'expenses:%'
ON CONFLICT DO NOTHING;

GRANT SELECT,INSERT ON public.farm_expense TO farm_app;
DO $$
DECLARE seq TEXT;
BEGIN
    seq := pg_get_serial_sequence('public.farm_expense','expense_id');
    IF seq IS NOT NULL THEN EXECUTE format('GRANT USAGE,SELECT ON SEQUENCE %s TO farm_app',seq); END IF;
END $$;
COMMIT;
