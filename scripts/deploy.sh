#!/usr/bin/env bash
set -e

# ==============================================================================
# Farm Management System N02 - Linux Deployment & Build Pipeline
# ==============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

echo "=========================================================="
echo " Farm Management System N02 - Linux Deployment Pipeline   "
echo "=========================================================="

# 1. Check Java version
echo "--> Checking Java runtime..."
if ! command -v java &> /dev/null; then
    echo "ERROR: Java runtime not found. Please install Java 21+."
    exit 1
fi
java -version 2>&1 | head -n 1

# 2. Source .env if present
if [ -f "$PROJECT_ROOT/.env" ]; then
    echo "--> Loading environment configuration from .env..."
    set -a
    source "$PROJECT_ROOT/.env"
    set +a
fi

# 3. Verify / Default environment variables
export DB_URL="${DB_URL:-jdbc:postgresql://localhost:5432/farm_management_erd_v1}"
export DB_USERNAME="${DB_USERNAME:-farm_app}"
export SERVER_PORT="${SERVER_PORT:-8080}"

if [ -z "$JWT_SECRET" ]; then
    echo "--> Generating random 256-bit JWT_SECRET..."
    export JWT_SECRET=$(openssl rand -base64 32)
fi

# 4. Build Application Package
echo "--> Compiling and packaging backend + frontend..."
chmod +x ./mvnw
./mvnw clean package -DskipTests -B

JAR_PATH="$PROJECT_ROOT/target/backend-0.0.1-SNAPSHOT.jar"
if [ ! -f "$JAR_PATH" ]; then
    echo "ERROR: Artifact $JAR_PATH not found."
    exit 1
fi

echo "--> Build SUCCESS: $(ls -lh "$JAR_PATH" | awk '{print $9, "(" $5 ")"}')"

# 5. Startup if --start passed
if [[ "$*" == *"--start"* ]]; then
    if [ -z "$DB_PASSWORD" ]; then
        echo -n "Enter password for PostgreSQL user '$DB_USERNAME': "
        read -s DB_PASSWORD
        echo
        export DB_PASSWORD
    fi

    echo "--> Launching Farm Management System N02 on port $SERVER_PORT..."
    trap 'unset DB_PASSWORD JWT_SECRET' EXIT
    java -XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -jar "$JAR_PATH"
else
    echo "==> Deployment artifact ready at $JAR_PATH."
    echo "    To launch: ./scripts/deploy.sh --start"
fi
