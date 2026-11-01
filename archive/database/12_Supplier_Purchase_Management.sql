-- Run as database owner after 08 and 09.
BEGIN;

-- ─── Supplier (Nhà cung cấp vật tư nông nghiệp) ─────────────────────────────
CREATE TABLE IF NOT EXISTS public.supplier (
    supplier_id   SERIAL PRIMARY KEY,
    supplier_name VARCHAR(150) NOT NULL,
    contact_name  VARCHAR(100),
    email         VARCHAR(254),
    phone         VARCHAR(20),
    address       VARCHAR(255),
    tax_code      VARCHAR(50),
    notes         VARCHAR(1000),
    status        VARCHAR(10) NOT NULL DEFAULT 'ACTIVE'
                  CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_supplier_name ON public.supplier(lower(btrim(supplier_name)));

-- ─── Purchase Order (Đơn mua vật tư) ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.purchase_order (
    po_id                  SERIAL PRIMARY KEY,
    po_number              VARCHAR(50) NOT NULL UNIQUE,
    supplier_id            INTEGER NOT NULL REFERENCES public.supplier(supplier_id) ON DELETE RESTRICT,
    farm_id                INTEGER NOT NULL REFERENCES public.farm(farm_id) ON DELETE RESTRICT,
    warehouse_id           INTEGER NOT NULL REFERENCES public.warehouse(warehouse_id) ON DELETE RESTRICT,
    order_date             DATE NOT NULL DEFAULT CURRENT_DATE,
    expected_delivery_date DATE,
    actual_delivery_date   DATE,
    status                 VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                           CHECK (status IN ('DRAFT','ORDERED','RECEIVED','CANCELLED')),
    total_amount           NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
    notes                  VARCHAR(1000),
    created_by             INTEGER REFERENCES public.app_user(user_id) ON DELETE SET NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_po_supplier ON public.purchase_order(supplier_id);
CREATE INDEX IF NOT EXISTS ix_po_farm ON public.purchase_order(farm_id);
CREATE INDEX IF NOT EXISTS ix_po_warehouse ON public.purchase_order(warehouse_id);
CREATE INDEX IF NOT EXISTS ix_po_status ON public.purchase_order(status);
CREATE INDEX IF NOT EXISTS ix_po_order_date ON public.purchase_order(order_date);

-- ─── Purchase Order Item (Chi tiết vật tư mua) ────────────────────────────────
CREATE TABLE IF NOT EXISTS public.purchase_order_item (
    po_item_id        SERIAL PRIMARY KEY,
    po_id             INTEGER NOT NULL REFERENCES public.purchase_order(po_id) ON DELETE CASCADE,
    material_id       INTEGER NOT NULL REFERENCES public.material(material_id) ON DELETE RESTRICT,
    quantity          NUMERIC(15,3) NOT NULL CHECK (quantity > 0),
    unit_price        NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (unit_price >= 0),
    total_price       NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (total_price >= 0),
    received_quantity NUMERIC(15,3) NOT NULL DEFAULT 0 CHECK (received_quantity >= 0),
    notes             VARCHAR(500)
);
CREATE INDEX IF NOT EXISTS ix_po_item_po ON public.purchase_order_item(po_id);
CREATE INDEX IF NOT EXISTS ix_po_item_material ON public.purchase_order_item(material_id);

-- ─── Permissions ─────────────────────────────────────────────────────────────
INSERT INTO public.auth_permission VALUES
 ('suppliers:read',    'View suppliers')
,('suppliers:create',  'Create suppliers')
,('suppliers:update',  'Update supplier information')
,('suppliers:status',  'Activate or deactivate suppliers')
,('purchases:read',    'View purchase orders and items')
,('purchases:create',  'Create purchase orders')
,('purchases:update',  'Update purchase orders')
,('purchases:receive', 'Receive materials from purchase order into warehouse inventory')
,('purchases:cancel',  'Cancel purchase orders')
ON CONFLICT (permission_code) DO NOTHING;

-- Grant permissions to SYSTEM_ADMIN
INSERT INTO public.auth_role_permission(role_id, permission_code)
SELECT r.role_id, p.permission_code
FROM public.role r CROSS JOIN public.auth_permission p
WHERE r.scope = 'SYSTEM' AND r.role_name = 'SYSTEM_ADMIN'
AND p.permission_code ~ '^(suppliers|purchases):'
ON CONFLICT DO NOTHING;

-- Grants to farm_app
GRANT SELECT, INSERT, UPDATE ON public.supplier TO farm_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.purchase_order TO farm_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.purchase_order_item TO farm_app;

-- Grant sequences to farm_app
DO $$
DECLARE seq TEXT;
BEGIN
    FOREACH seq IN ARRAY ARRAY[
        pg_get_serial_sequence('public.supplier',            'supplier_id'),
        pg_get_serial_sequence('public.purchase_order',      'po_id'),
        pg_get_serial_sequence('public.purchase_order_item', 'po_item_id')
    ] LOOP
        IF seq IS NOT NULL THEN
            EXECUTE format('GRANT USAGE, SELECT ON SEQUENCE %s TO farm_app', seq);
        END IF;
    END LOOP;
END $$;

COMMIT;
