# ---------------------------------------------------------------------------
# Single parameterised Dockerfile for every service.
#
# One file rather than seven near-identical ones: the modules differ only in
# which jar gets copied, so duplicating the build stage would mean seven places
# to update on a Java or Maven version bump.
#
# Build context must be the backend/ directory, because every service depends on
# common-lib and Maven needs the whole reactor present to resolve it:
#   docker build -f docker/backend.Dockerfile --build-arg MODULE=auth-service \
#       -t earthscan/auth-service ./backend
# ---------------------------------------------------------------------------

# ----------------------------------------------------------------- build stage
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Copy only the POMs first, then warm the dependency cache. Docker caches this
# layer, so editing Java source no longer re-downloads the whole dependency
# tree on every build — which is the difference between a 20-second and a
# five-minute rebuild.
COPY pom.xml .
COPY common-lib/pom.xml common-lib/
COPY discovery-server/pom.xml discovery-server/
COPY api-gateway/pom.xml api-gateway/
COPY auth-service/pom.xml auth-service/
COPY land-service/pom.xml land-service/
COPY forum-service/pom.xml forum-service/
COPY notification-service/pom.xml notification-service/

RUN mvn -B -q dependency:go-offline -DskipTests || true

COPY common-lib/src common-lib/src
COPY discovery-server/src discovery-server/src
COPY api-gateway/src api-gateway/src
COPY auth-service/src auth-service/src
COPY land-service/src land-service/src
COPY forum-service/src forum-service/src
COPY notification-service/src notification-service/src

ARG MODULE
ARG SKIP_TESTS=true

# -am builds common-lib alongside the requested module. Tests are skipped by
# default here because the image build is not the place to run them: CI runs
# `mvn verify` once across the whole reactor instead of once per image.
RUN mvn -B -pl ${MODULE} -am clean package -DskipTests=${SKIP_TESTS}

RUN cp ${MODULE}/target/*.jar /build/application.jar

# --------------------------------------------------------------- runtime stage
FROM eclipse-temurin:17-jre-jammy

# Runs as a non-root user: a container escape from an application listening on a
# public port should not land on a root shell.
RUN groupadd --system --gid 1001 earthscan \
    && useradd --system --uid 1001 --gid earthscan --create-home earthscan

WORKDIR /app

# curl is needed for the compose healthchecks; nothing else is installed.
RUN apt-get update \
    && apt-get install --no-install-recommends -y curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder --chown=earthscan:earthscan /build/application.jar /app/application.jar

USER earthscan

ARG PORT=8080
ENV SERVER_PORT=${PORT}
EXPOSE ${PORT}

# MaxRAMPercentage rather than a fixed -Xmx: the JVM then respects whatever
# memory limit the container is given, instead of ignoring it and being
# OOM-killed by the kernel.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/application.jar"]
