CREATE TABLE IF NOT EXISTS public.crop_production (
    production_id SERIAL PRIMARY KEY,
    farm_id INTEGER NOT NULL,
    crop_name VARCHAR(100) NOT NULL,
    season_name VARCHAR(100),
    growth_stage VARCHAR(50),
    expected_quantity NUMERIC(10, 2),
    actual_quantity NUMERIC(10, 2),
    unit VARCHAR(20),
    status VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS public.harvested_product (
    product_id SERIAL PRIMARY KEY,
    production_id INTEGER NOT NULL REFERENCES public.crop_production(production_id),
    product_name VARCHAR(100) NOT NULL,
    quantity NUMERIC(10, 2),
    quality VARCHAR(50),
    status VARCHAR(50),
    storage_location VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
