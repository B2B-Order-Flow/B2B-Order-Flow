# Сборка ядра: контекст сборки это папка core-service (см. infra/compose.yaml).
# Тесты в образе не запускаются: интеграционным нужен Docker (Testcontainers), их гоняет CI отдельно.
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /workspace
COPY . .
# Кеш Maven между сборками; mvnw сам скачивает нужную версию Maven
RUN --mount=type=cache,target=/root/.m2 sh mvnw -B -q -DskipTests package \
    && cp target/*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-noble
RUN useradd --system --uid 10001 --no-create-home app
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
