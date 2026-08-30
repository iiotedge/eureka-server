# syntax=docker/dockerfile:1.7
#
# Runtime-only image: the jar is built beforehand by CI (`mvn verify`), not
# inside this Dockerfile - same pattern as every other service on this
# platform. Replaces the previous single-stage `FROM openjdk:17` image,
# which was long since deprecated/unmaintained on Docker Hub, ran as root
# (no USER directive), and baked the whole JDK (not just a JRE) into the
# final image.

# ---- Extract stage: split the fat jar into layers for better caching ----
FROM gcr.io/distroless/java21-debian12:nonroot AS builder

WORKDIR /app
COPY target/eureka-server.jar /app/app.jar

RUN ["java", "-Djarmode=tools", "-jar", "/app/app.jar", "extract", "--layers", "--launcher", "--destination", "/app/extracted"]

# ---- Runtime stage ----
# Google's distroless Java 21 (Debian 12), nonroot variant - no shell, no
# package manager, no coreutils, minimal CVE surface, and it already runs
# as a non-root user by default. Every microservice on this platform
# depends on this one being reachable, so its own attack surface matters
# more than most.
FROM gcr.io/distroless/java21-debian12:nonroot

ARG VCS_REF=unknown
ARG BUILD_DATE=unknown
ARG VERSION=unknown

LABEL org.opencontainers.image.title="eureka-server" \
      org.opencontainers.image.description="Service registry every IoTMining/IIoTEdge microservice depends on" \
      org.opencontainers.image.source="https://github.com/IotMining/eureka-server" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.licenses="MIT"

WORKDIR /app
COPY --from=builder /app/extracted/dependencies/ ./
COPY --from=builder /app/extracted/spring-boot-loader/ ./
COPY --from=builder /app/extracted/snapshot-dependencies/ ./
COPY --from=builder /app/extracted/application/ ./

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseZGC -Dfile.encoding=UTF-8 -Duser.timezone=UTC -Djava.security.egd=file:/dev/./urandom" \
    SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8761

EXPOSE 8761

# No Docker-level HEALTHCHECK: distroless has no shell/curl/wget to run one
# from. /actuator/health (unauthenticated - see SecurityConfig) is meant
# to be probed by the orchestrator instead - a Kubernetes readiness/
# liveness probe, Nginx's own upstream check, or `docker compose`'s own
# healthcheck: block (which runs from outside the container, not inside).
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
