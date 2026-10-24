#!/usr/bin/env bash
set -e

# PostgreSQL container initialization script
echo "==> Initializing Farm Management PostgreSQL database..."

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    DO \$\$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '${APP_DB_USER:-farm_app}') THEN
            CREATE ROLE ${APP_DB_USER:-farm_app} WITH LOGIN PASSWORD '${APP_DB_PASSWORD:-farm_app_secure_pass}';
        ELSE
            ALTER ROLE ${APP_DB_USER:-farm_app} WITH LOGIN PASSWORD '${APP_DB_PASSWORD:-farm_app_secure_pass}';
        END IF;
    END
    \$\$;
    GRANT CONNECT ON DATABASE "$POSTGRES_DB" TO ${APP_DB_USER:-farm_app};
    GRANT USAGE ON SCHEMA public TO ${APP_DB_USER:-farm_app};
EOSQL

echo "==> Running migration SQL scripts in strict sequence..."
MIGRATION_FILES=(
    "/database/05_Authentication_Sessions.sql"
    "/database/06_User_Role_Management.sql"
    "/database/07_Farm_Plot_Crop.sql"
    "/database/07_Crop_And_Product_Management.sql"
    "/database/08_Material_Warehouse.sql"
    "/database/09_Management_Integrity.sql"
    "/database/10_Harvest_Management.sql"
    "/database/11_Advanced_Dashboard_Reports.sql"
    "/database/12_Supplier_Purchase_Management.sql"
    "/database/13_Sales_Order_Management.sql"
    "/database/Bootstrap-Admin.sql"
)

for sql_file in "${MIGRATION_FILES[@]}"; do
    if [ -f "$sql_file" ]; then
        echo "--> Applying migration: $sql_file"
        psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -f "$sql_file"
    else
        echo "--> Skipping missing migration: $sql_file"
    fi
done

echo "==> Database initialization completed successfully."
