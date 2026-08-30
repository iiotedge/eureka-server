# eureka-server

Service registry every microservice on the IoTMining/IIoTEdge platform
depends on for discovery (Spring Cloud Netflix Eureka).

## Why this pass happened

This service had two disagreeing versions of its own security config - one
committed (secures everything), one sitting uncommitted on disk (only the
registry API secured, dashboard wide open with a comment flagging that as
an unresolved choice). Reconciled into one real answer: see
`config/SecurityConfig.java`. Also had a real, live bug in its dev config
(`spring.config.import: "optional:configserver::http://..."` - a stray
double colon), no dedicated credential (the "admin"/"8506" every client
connects with was the platform's Postgres password, reused, not a distinct
managed secret), and Java 17/local-parent drift against the rest of the
platform's Java 21/`iiotedge-parent` standard.

## Architecture

- **`config/SecurityConfig`** - Basic Auth required for both `/eureka/**`
  (the registry API every client uses) and the dashboard UI at `/` - an
  open dashboard leaks the platform's entire internal topology (every
  registered service's name, IP, port, instance count) to anyone who can
  reach the port. Only `/actuator/health` and `/error` stay open.
- **Self-preservation**: kept enabled in every profile (never disable this
  in a real deployment - it's what stops a network blip from mass-evicting
  every registered service) and tuned consistently across dev/prod/qa
  (`renewal-percent-threshold`, lease intervals) - prod previously fell
  back to framework defaults that didn't match every client's actual 10s
  renewal interval.
- **Peer-readiness, not peer-deployment**: `application-peer1.yml`/
  `application-peer2.yml` are a ready-to-activate template for real 2-node
  HA (each pointing at the other, `register-with-eureka`/`fetch-registry`
  flipped to `true`) - not activated by default, no second instance
  deployed as part of this pass. Standing up real HA later is "activate
  the profile and deploy a second instance," not a redesign.

## Deployment - three supported paths, same image

- **`iiotedge-cli.sh`** (bare VM, systemd) - the platform's real production
  reference; see that script's own comments for how this service's env
  vars are generated.
- **Plain Docker / docker-compose** - see this repo's own
  `docker-compose.yml` for standalone use, or
  `data-ingestion-service/docker-compose.yml` for the whole local stack
  together.
- **Kubernetes** - see `infrastructure/eureka/` at the platform root for
  Deployment/Service/ConfigMap manifests, with readiness/liveness probes
  against `/actuator/health` and resource requests/limits set.

All three read the exact same env vars (`SPRING_SECURITY_EUREKA_USERNAME`/
`PASSWORD`, `EUREKA_HOSTNAME`, `EUREKA_SERVICE_URL`, `SPRING_PROFILES_ACTIVE`)
- nothing path-specific baked into the image itself.

## Local development

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Default port: `8761`. Dev credentials default to `admin`/`eureka-dev-only`
(overridable via `SPRING_SECURITY_EUREKA_USERNAME`/`PASSWORD`) - a real
deployment (`prod`/`qa` profiles) requires both, no fallback.

## Build

```bash
./mvnw clean verify
```

## Docker

```bash
./mvnw clean package -DskipTests
docker build -t eureka-server .
docker run -p 8761:8761 -e SPRING_SECURITY_EUREKA_PASSWORD=<real-value> eureka-server
```

## License

MIT - see `LICENSE`.
