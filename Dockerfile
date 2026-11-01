# ============================================================
# Multi-stage Dockerfile for Render.com (Docker mode)
# Stage 1: Build JAR with Maven
# Stage 2: Lightweight runtime image
# ============================================================

# --- Stage 1: Build ---
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /build
# Copy Maven wrapper and pom.xml first (layer caching)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:resolve -q || true
# Copy source and build
COPY src/ src/
COPY frontend/ frontend/
RUN ./mvnw clean package -DskipTests -q

# --- Stage 2: Runtime ---
FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl bash \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy JAR from builder
COPY --from=builder /build/target/backend-0.0.1-SNAPSHOT.jar /app/target/backend-0.0.1-SNAPSHOT.jar

# Copy startup script
COPY scripts/railway-start.sh /app/start.sh
RUN chmod +x /app/start.sh && mkdir -p /app/logs

# Default env for cloud deployment
ENV SERVER_ADDRESS=0.0.0.0 \
    SERVER_PORT=8080 \
    SERVER_SSL_ENABLED=false \
    APP_OPEN_BROWSER=false \
    APP_FRONTEND_WORKING_TREE=false \
    LOG_FILE=/app/logs/backend.log

EXPOSE 8080

ENTRYPOINT ["/bin/bash", "/app/start.sh"]
