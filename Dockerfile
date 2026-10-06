# syntax=docker/dockerfile:1.7
# ---------------------------------------------------------------------------
# Imagen genérica para cualquier módulo del proyecto (multi-stage build).
#   docker build --build-arg MODULE=ms-cuentas -t bancoxyz/ms-cuentas .
# ---------------------------------------------------------------------------

# Etapa 1: compilación con Maven (la caché de ~/.m2 se reutiliza entre módulos)
FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -q -B -pl ${MODULE} -am package -DskipTests && \
    cp ${MODULE}/target/${MODULE}.jar /app.jar

# Etapa 2: imagen de ejecución liviana (sólo JRE)
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 1001 --create-home app
WORKDIR /app
COPY --from=build /app.jar /app/app.jar
RUN mkdir -p /app/data /app/output && chown -R app /app
USER app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
