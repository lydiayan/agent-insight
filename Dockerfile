FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress -DskipTests package \
    && cp target/agent-insight-*.jar /workspace/app.jar

FROM eclipse-temurin:17-jre-jammy

RUN useradd --system --uid 10001 --create-home appuser

WORKDIR /app
COPY --from=build --chown=appuser:appuser /workspace/app.jar ./app.jar

USER appuser
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
