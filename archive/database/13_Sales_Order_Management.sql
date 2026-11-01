-- Run as database owner after 07_Crop_And_Product_Management.
BEGIN;

-- ─── Customer (Khách hàng mua nông sản) ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.customer (
    customer_id   SERIAL PRIMARY KEY,
    customer_name VARCHAR(150) NOT NULL,
    contact_name  VARCHAR(100),
    email         VARCHAR(254),
    phone         VARCHAR(20),
    address       VARCHAR(255),
    customer_type VARCHAR(20) NOT NULL DEFAULT 'INDIVIDUAL'
                  CHECK (customer_type IN ('INDIVIDUAL','WHOLESALER','RETAILER','ENTERPRISE')),
    tax_code      VARCHAR(50),
    notes         VARCHAR(1000),
    status        VARCHAR(10) NOT NULL DEFAULT 'ACTIVE'
                  CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_customer_name ON public.customer(lower(btrim(customer_name)));

-- ─── Sales Order (Đơn bán nông sản sau thu hoạch) ───────────────────────────
CREATE TABLE IF NOT EXISTS public.sales_order (
    so_id            SERIAL PRIMARY KEY,
    so_number        VARCHAR(50) NOT NULL UNIQUE,
    customer_id      INTEGER NOT NULL REFERENCES public.customer(customer_id) ON DELETE RESTRICT,
    farm_id          INTEGER REFERENCES public.farm(farm_id) ON DELETE RESTRICT,
    order_date       DATE NOT NULL DEFAULT CURRENT_DATE,
    delivery_date    DATE,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                     CHECK (status IN ('PENDING','CONFIRMED','SHIPPED','DELIVERED','CANCELLED')),
    payment_status   VARCHAR(20) NOT NULL DEFAULT 'UNPAID'
                     CHECK (payment_status IN ('UNPAID','PARTIALLY_PAID','PAID')),
    total_amount     NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
    delivery_address VARCHAR(255),
    notes            VARCHAR(1000),
    created_by       INTEGER REFERENCES public.app_user(user_id) ON DELETE SET NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_so_customer ON public.sales_order(customer_id);
CREATE INDEX IF NOT EXISTS ix_so_farm ON public.sales_order(farm_id);
CREATE INDEX IF NOT EXISTS ix_so_status ON public.sales_order(status);
CREATE INDEX IF NOT EXISTS ix_so_order_date ON public.sales_order(order_date);

-- ─── Sales Order Item (Chi tiết nông sản bán) ────────────────────────────────
CREATE TABLE IF NOT EXISTS public.sales_order_item (
    so_item_id  SERIAL PRIMARY KEY,
    so_id       INTEGER NOT NULL REFERENCES public.sales_order(so_id) ON DELETE CASCADE,
    product_id  INTEGER NOT NULL REFERENCES public.harvested_product(product_id) ON DELETE RESTRICT,
    quantity    NUMERIC(15,2) NOT NULL CHECK (quantity > 0),
    unit_price  NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (unit_price >= 0),
    total_price NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (total_price >= 0),
    notes       VARCHAR(500)
);
CREATE INDEX IF NOT EXISTS ix_so_item_so ON public.sales_order_item(so_id);
CREATE INDEX IF NOT EXISTS ix_so_item_product ON public.sales_order_item(product_id);

-- ─── Permissions ─────────────────────────────────────────────────────────────
INSERT INTO public.auth_permission VALUES
 ('customers:read',   'View customers')
,('customers:create', 'Create customers')
,('customers:update', 'Update customer information')
,('customers:status', 'Activate or deactivate customers')
,('sales:read',       'View sales orders and items')
,('sales:create',     'Create sales orders')
,('sales:update',     'Update sales orders')
,('sales:status',     'Update sales order status and product stock')
ON CONFLICT (permission_code) DO NOTHING;

-- Grant permissions to SYSTEM_ADMIN
INSERT INTO public.auth_role_permission(role_id, permission_code)
SELECT r.role_id, p.permission_code
FROM public.role r CROSS JOIN public.auth_permission p
WHERE r.scope = 'SYSTEM' AND r.role_name = 'SYSTEM_ADMIN'
AND p.permission_code ~ '^(customers|sales):'
ON CONFLICT DO NOTHING;

-- Grants to farm_app
GRANT SELECT, INSERT, UPDATE ON public.customer TO farm_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.sales_order TO farm_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.sales_order_item TO farm_app;

-- Grant sequences to farm_app
DO $$
DECLARE seq TEXT;
BEGIN
    FOREACH seq IN ARRAY ARRAY[
        pg_get_serial_sequence('public.customer',         'customer_id'),
        pg_get_serial_sequence('public.sales_order',      'so_id'),
        pg_get_serial_sequence('public.sales_order_item', 'so_item_id')
    ] LOOP
        IF seq IS NOT NULL THEN
            EXECUTE format('GRANT USAGE, SELECT ON SEQUENCE %s TO farm_app', seq);
        END IF;
    END LOOP;
END $$;

COMMIT;
