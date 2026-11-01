FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 farm && useradd --uid 10001 --gid farm --no-create-home farm
WORKDIR /app
ARG JAR_FILE=target/backend-0.0.1-SNAPSHOT.jar
COPY --chown=farm:farm ${JAR_FILE} /app/backend.jar
RUN mkdir /app/logs && chown farm:farm /app/logs
USER farm
ENV SERVER_ADDRESS=0.0.0.0 SERVER_PORT=8080 SERVER_SSL_ENABLED=false \
    APP_OPEN_BROWSER=false APP_FRONTEND_WORKING_TREE=false LOG_FILE=/app/logs/backend.log
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/backend.jar"]
