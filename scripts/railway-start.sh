#!/bin/bash
# ============================================================
# Render/Railway startup script for Farm Management System
# Runs DB migrations first, then starts the Spring Boot app.
# ============================================================
set -e

echo "=========================================="
echo "  Farm Management System - Cloud Start"
echo "=========================================="

# ----------------------------------------------------------
# 1. Convert postgres:// URL to JDBC format
#    Render format: postgres://USER:PASS@HOST/DB  (no port)
#    JDBC format:   jdbc:postgresql://HOST:5432/DB  (no credentials in URL)
# ----------------------------------------------------------
if [ -z "$DB_URL" ] && [ -n "$DATABASE_URL" ]; then
  # Strip "postgres://" or "postgresql://" prefix
  # Strip embedded user:password@  (everything before @)
  # Add :5432 port if not present
  RAW=$(echo "$DATABASE_URL" | sed -E 's|^postgres(ql)?://||')
  # Remove user:password@ prefix (up to and including @)
  HOST_DB=$(echo "$RAW" | sed -E 's|^[^@]+@||')
  # Add :5432 if no port present (host/db pattern without colon in host part)
  HOST=$(echo "$HOST_DB" | cut -d'/' -f1)
  DBNAME=$(echo "$HOST_DB" | cut -d'/' -f2-)
  if echo "$HOST" | grep -qv ':'; then
    HOST="${HOST}:5432"
  fi
  DB_URL="jdbc:postgresql://${HOST}/${DBNAME}"
  export DB_URL
  echo "[INFO] Converted DATABASE_URL to JDBC: $DB_URL"
fi

if [ -z "$DB_URL" ]; then
  echo "[ERROR] DB_URL is not set. Set DB_URL or DATABASE_URL."
  exit 1
fi

# ----------------------------------------------------------
# 2. Migration credentials (Render uses its own postgres user)
# ----------------------------------------------------------
export DB_MIGRATION_USERNAME="${DB_MIGRATION_USERNAME:-${PGUSER:-postgres}}"
export DB_MIGRATION_PASSWORD="${DB_MIGRATION_PASSWORD:-$PGPASSWORD}"

if [ -z "$DB_MIGRATION_PASSWORD" ]; then
  echo "[ERROR] DB_MIGRATION_PASSWORD or PGPASSWORD is not set."
  exit 1
fi

# ----------------------------------------------------------
# 3. App DB credentials
#    On Render/Railway managed databases, use the provided database user directly
#    and enable ALLOW_OWNER_CONNECTION for WorkspaceDatabaseCheck.
# ----------------------------------------------------------
if [ -n "$DB_APP_PASSWORD" ]; then
  echo "[INFO] DB_APP_PASSWORD is set - app will connect as: ${DB_USERNAME:-farm_app}"
  export DB_USERNAME="${DB_USERNAME:-farm_app}"
  export DB_PASSWORD="${DB_PASSWORD:-$DB_APP_PASSWORD}"
else
  echo "[INFO] DB_APP_PASSWORD not set - app will connect as database user: $DB_MIGRATION_USERNAME"
  export ALLOW_OWNER_CONNECTION="true"
  export DB_USERNAME="${DB_USERNAME:-$DB_MIGRATION_USERNAME}"
  export DB_PASSWORD="${DB_PASSWORD:-$DB_MIGRATION_PASSWORD}"
fi
echo "[INFO] App runtime will connect as: $DB_USERNAME"

# ----------------------------------------------------------
# 3. Runtime settings
# ----------------------------------------------------------
export SERVER_ADDRESS="${SERVER_ADDRESS:-0.0.0.0}"
export SERVER_PORT="${SERVER_PORT:-${PORT:-8080}}"
export APP_OPEN_BROWSER="false"
export APP_FRONTEND_WORKING_TREE="false"

echo "[INFO] Server will listen on $SERVER_ADDRESS:$SERVER_PORT"
echo "[INFO] Migration user: $DB_MIGRATION_USERNAME"

# ----------------------------------------------------------
# 4. Run Flyway migrations
# ----------------------------------------------------------
echo ""
echo ">>> Running database migrations..."
java \
  -Dloader.main=com.farmmanagement.backend.config.DatabaseMigrations \
  -cp target/backend-0.0.1-SNAPSHOT.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher \
  migrate

echo ">>> Migrations complete."

# ----------------------------------------------------------
# 5. Start Spring Boot
# ----------------------------------------------------------
echo ""
echo ">>> Starting Spring Boot on port $SERVER_PORT..."
exec java \
  -Dserver.address="$SERVER_ADDRESS" \
  -Dserver.port="$SERVER_PORT" \
  -Dapp.open-browser=false \
  -Dapp.frontend.working-tree=false \
  -jar target/backend-0.0.1-SNAPSHOT.jar
