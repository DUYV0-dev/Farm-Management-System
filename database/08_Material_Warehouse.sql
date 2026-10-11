-- Run once as database owner against ERD v1.
BEGIN;

-- ─── Material Category (Danh mục phân loại vật tư) ───────────────────────────
CREATE TABLE IF NOT EXISTS public.material_category (
    category_id   SERIAL PRIMARY KEY,
    category_name VARCHAR(100) NOT NULL,
    description   VARCHAR(1000),
    status        VARCHAR(10) NOT NULL DEFAULT 'ACTIVE'
                  CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ─── Material (Danh mục vật tư) ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.material (
    material_id   SERIAL PRIMARY KEY,
    category_id   INTEGER NOT NULL REFERENCES public.material_category(category_id) ON DELETE RESTRICT,
    material_name VARCHAR(150) NOT NULL,
    unit          VARCHAR(50) NOT NULL, -- e.g., kg, liter, bag, bottle
    manufacturer  VARCHAR(150),
    description   VARCHAR(1000),
    status        VARCHAR(10) NOT NULL DEFAULT 'ACTIVE'
                  CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ─── Warehouse (Kho lưu trữ) ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.warehouse (
    warehouse_id   SERIAL PRIMARY KEY,
    farm_id        INTEGER NOT NULL REFERENCES public.farm(farm_id) ON DELETE RESTRICT,
    warehouse_name VARCHAR(100) NOT NULL,
    location       VARCHAR(255),
    capacity_text  VARCHAR(100), -- Description of capacity, e.g., "500 sq meters" or "10 tons"
    status         VARCHAR(10) NOT NULL DEFAULT 'ACTIVE'
                   CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ─── Warehouse Inventory (Tồn kho vật tư) ─────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.warehouse_inventory (
    inventory_id   SERIAL PRIMARY KEY,
    warehouse_id   INTEGER NOT NULL REFERENCES public.warehouse(warehouse_id) ON DELETE RESTRICT,
    material_id    INTEGER NOT NULL REFERENCES public.material(material_id) ON DELETE RESTRICT,
    quantity       NUMERIC(15,3) NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    last_updated   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (warehouse_id, material_id)
);

-- ─── Permissions ──────────────────────────────────────────────────────────────
CREATE UNIQUE INDEX IF NOT EXISTS uq_material_category_name ON public.material_category(lower(btrim(category_name)));
CREATE UNIQUE INDEX IF NOT EXISTS uq_material_name ON public.material(lower(btrim(material_name)));
CREATE UNIQUE INDEX IF NOT EXISTS uq_warehouse_name ON public.warehouse(farm_id,lower(btrim(warehouse_name)));

INSERT INTO public.auth_permission VALUES
 ('materials:read',    'View materials and categories')
,('materials:create',  'Create materials and categories')
,('materials:update',  'Update materials and categories')
,('materials:status',  'Activate or deactivate materials')
,('warehouses:read',   'View warehouses and inventory')
,('warehouses:create', 'Create warehouses')
,('warehouses:update', 'Update warehouse information')
,('warehouses:status', 'Activate or deactivate warehouses')
,('inventory:manage',  'Manage inventory stock availability')
ON CONFLICT (permission_code) DO NOTHING;

-- Grant permissions to SYSTEM_ADMIN
INSERT INTO public.auth_role_permission(role_id, permission_code)
SELECT r.role_id, p.permission_code
FROM public.role r CROSS JOIN public.auth_permission p
WHERE r.scope = 'SYSTEM' AND r.role_name = 'SYSTEM_ADMIN'
AND p.permission_code ~ '^(materials|warehouses|inventory):'
ON CONFLICT DO NOTHING;

-- ─── GRANTS ───────────────────────────────────────────────────────────────────
GRANT SELECT, INSERT, UPDATE ON public.material_category TO farm_app;
GRANT SELECT, INSERT, UPDATE ON public.material TO farm_app;
GRANT SELECT, INSERT, UPDATE ON public.warehouse TO farm_app;
GRANT SELECT, INSERT, UPDATE ON public.warehouse_inventory TO farm_app;

-- Sequences
DO $$
DECLARE seq TEXT;
BEGIN
    FOREACH seq IN ARRAY ARRAY[
        pg_get_serial_sequence('public.material_category',  'category_id'),
        pg_get_serial_sequence('public.material',           'material_id'),
        pg_get_serial_sequence('public.warehouse',          'warehouse_id'),
        pg_get_serial_sequence('public.warehouse_inventory','inventory_id')
    ] LOOP
        IF seq IS NOT NULL THEN
            EXECUTE format('GRANT USAGE, SELECT ON SEQUENCE %s TO farm_app', seq);
        END IF;
    END LOOP;
END $$;

COMMIT;
