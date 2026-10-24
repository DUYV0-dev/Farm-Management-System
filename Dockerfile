# Stage 1: Build backend and bundle frontend assets
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy maven wrapper and pom.xml
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy frontend source and java sources
COPY frontend/ frontend/
COPY src/ src/

# Build JAR package (includes compiled classes and frontend static files)
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Hardened Runtime Container
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Security: Create non-root system user and group
RUN addgroup -S -g 10001 farmgroup && \
    adduser -S -u 10001 -G farmgroup farmuser && \
    mkdir -p /app/logs && \
    chown -R farmuser:farmgroup /app

# Copy built JAR from builder stage
COPY --from=builder --chown=farmuser:farmgroup /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

USER farmuser

# Expose default HTTP application port
EXPOSE 8080

# Healthcheck probe using Spring Boot Actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
