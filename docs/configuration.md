# Configuration Reference

## Spring Profiles

| Profile | Purpose | Activation |
|---------|---------|------------|
| `default` | Local development with H2 file database and human-readable console logging | Active when no profile is specified |
| `postgres` | PostgreSQL database (replaces H2) | `SPRING_PROFILES_ACTIVE=postgres` |
| `kubernetes` | Kubernetes deployment: enables JSON logging, K8s health probes | `SPRING_PROFILES_ACTIVE=kubernetes` |
| `production` | Production hardening: TLS, quotas, JSON logging | `SPRING_PROFILES_ACTIVE=production` |
| `json-logging` | Structured JSON log output (standalone, without full production config) | `SPRING_PROFILES_ACTIVE=json-logging` |

Profiles can be combined: `SPRING_PROFILES_ACTIVE=postgres,production`

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8080` | HTTP listen port |
| `SPRING_PROFILES_ACTIVE` | `default` | Comma-separated list of active profiles |
| `DATABASE_URL` | `jdbc:h2:file:./data/jmeter-next` | JDBC connection URL (used by `postgres` profile) |
| `DATABASE_USER` | `sa` (H2) / `jmeter` (postgres) | Database username |
| `DATABASE_PASSWORD` | *(empty)* (H2) / `jmeter` (postgres) | Database password |
| `JMETER_JWT_SECRET` | auto-generated | JWT signing key for API authentication |
| `WORKER_SERVICE_DNS` | *(none)* | DNS name for discovering distributed workers (K8s) |
| `WORKER_GRPC_PORT` | `9090` | gRPC port for worker communication |

## Application Properties (`application.yml`)

### Server

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | `8080` | HTTP listen port |
| `server.shutdown` | `graceful` | Enables graceful shutdown; in-flight requests are completed before stopping |

### Spring Core

| Property | Default | Description |
|----------|---------|-------------|
| `spring.application.name` | `jmeter-next` | Application name (used in metrics tags) |
| `spring.threads.virtual.enabled` | `true` | Enables Project Loom virtual threads |
| `spring.servlet.multipart.max-file-size` | `50MB` | Max upload file size (JMX plans) |
| `spring.servlet.multipart.max-request-size` | `50MB` | Max total request size |
| `spring.lifecycle.timeout-per-shutdown-phase` | `30s` | Max time to wait for in-flight requests during graceful shutdown |

### Database

| Property | Default | Description |
|----------|---------|-------------|
| `spring.datasource.url` | `jdbc:h2:file:./data/jmeter-next;AUTO_SERVER=TRUE` | JDBC URL |
| `spring.datasource.driver-class-name` | `org.h2.Driver` | JDBC driver class |
| `spring.flyway.enabled` | `true` | Enables Flyway schema migrations |
| `spring.flyway.locations` | `classpath:db/migration` | Migration script locations |

### Management / Actuator

| Property | Default | Description |
|----------|---------|-------------|
| `management.endpoints.web.exposure.include` | `health,info,prometheus,metrics` | Exposed actuator endpoints |
| `management.endpoint.health.show-details` | `when_authorized` | Health detail visibility |
| `management.endpoint.health.probes.enabled` | `true` | Enables `/actuator/health/liveness` and `/actuator/health/readiness` |
| `management.health.livenessState.enabled` | `true` | Exposes Kubernetes liveness probe |
| `management.health.readinessState.enabled` | `true` | Exposes Kubernetes readiness probe |
| `management.metrics.tags.application` | `jmeter-next` | Global tag applied to all Prometheus metrics |

### JMeter

| Property | Default | Description |
|----------|---------|-------------|
| `jmeter.db.mode` | `sqlite` | Database mode selector |
| `jmeter.auth.multi-user` | `false` (`true` in production) | Enables multi-user authentication |
| `jmeter.quota.max-concurrent-runs` | *(unlimited)* (`3` in production) | Max concurrent test runs per user |
| `jmeter.quota.max-virtual-users` | *(unlimited)* (`10000` in production) | Max virtual users per run |
| `jmeter.quota.max-duration-seconds` | *(unlimited)* (`14400` in production) | Max run duration in seconds |

## Actuator Endpoints

| Endpoint | URL | Description |
|----------|-----|-------------|
| Health | `/actuator/health` | Overall health check |
| Liveness | `/actuator/health/liveness` | Kubernetes liveness probe |
| Readiness | `/actuator/health/readiness` | Kubernetes readiness probe |
| Prometheus | `/actuator/prometheus` | Prometheus metrics scrape endpoint |
| Metrics | `/actuator/metrics` | Spring Boot metrics browser |
| Info | `/actuator/info` | Application info |

## Custom Prometheus Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `jmeter_runs_total` | Counter | Total test runs started |
| `jmeter_samples_total` | Counter | Total samples collected |
| `jmeter_errors_total` | Counter | Total sample errors |
| `jmeter_active_runs` | Gauge | Currently active test runs |
| `jmeter_active_workers` | Gauge | Connected distributed workers |
| `jmeter_response_time_seconds` | Summary | Response time distribution (p50, p90, p95, p99) |

## Kubernetes Deployment

The Helm chart at `deploy/helm/jmeter-next/` configures:
- Liveness probe: `GET /actuator/health/liveness` (initialDelay: 30s, period: 10s)
- Readiness probe: `GET /actuator/health/readiness` (initialDelay: 15s, period: 10s)
- Spring profile: `kubernetes` (set via `SPRING_PROFILES_ACTIVE` env var)
- Graceful shutdown: the 30s timeout aligns with the default K8s `terminationGracePeriodSeconds`
