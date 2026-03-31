# b3meter

**Modern load testing platform — compatible with Apache JMeter JMX test plans.**

[![CI](https://github.com/B3Cognition/b3-meter/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/B3Cognition/b3-meter/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/java-21-orange.svg)](https://adoptium.net/)
[![React](https://img.shields.io/badge/react-19-61DAFB.svg)](https://react.dev/)
[![Version](https://img.shields.io/badge/version-0.1.0-green.svg)](https://github.com/B3Cognition/b3-meter/releases)

---

b3meter is a drop-in replacement for Apache JMeter — not a wrapper around it. It reads `.jmx` test plans directly and executes them through a ground-up engine built on Java 21 virtual threads. You get a React 19 web UI, 27 built-in protocol samplers, arrival-rate execution models, distributed mode with accurate HDR histogram merging, and four metrics backends — all with zero framework dependencies in the core engine.

> **New here?** See [GETTING-STARTED.md](GETTING-STARTED.md) for a complete step-by-step walkthrough — prerequisites, startup, Docker/K8s deployment, and corrected API examples.

---

## Features

### Protocol Samplers (27 built-in)

| Category | Samplers |
|----------|----------|
| HTTP | HTTP/HTTPS (HC4 + HC5), AJP, SOAP |
| Realtime | WebSocket, SSE, gRPC, MQTT |
| Streaming | HLS, DASH, WebRTC |
| Data | JDBC, FTP, LDAP, SMTP, TCP |
| Scripting | JSR223 (Groovy/JS/Python), BeanShell |
| Other | JUnit, OS Process, Debug, Mail Reader, Access Log |

### Execution Models & Load Shapes

| Execution Model | Description |
|----------------|-------------|
| Constant VUs | Fixed N virtual users looping for a duration |
| Arrival Rate | Fixed iterations/sec — VU pool auto-scales |
| Ramping Arrival Rate | Rate changes across stages with linear interpolation |

Load shapes: **Constant · Ramp · Stages · Step · Sinusoidal · Composite**

### Metrics Backends

| Backend | Notes |
|---------|-------|
| CSV | JTL-compatible, configurable delimiter |
| JSON | NDJSON, one object per sample bucket |
| InfluxDB | Line protocol, v1 and v2 (Bearer token) |
| Prometheus | Pull endpoint on `:9270` |

### Distributed Mode

- gRPC primary transport, WebSocket fallback
- **DIVIDE** mode (500 VUs / 5 workers = 100 each) or **MULTIPLY** (500 VUs × 5 workers)
- Coordinated start — all workers fire simultaneously
- HDR histogram merge — accurate percentiles across workers, no averaging
- Circuit breaker + health polling + auto-reconnect

### Other

- React 19 web UI: plan editor, live charts, 24 themes
- JWT + RBAC, SSRF protection, XStream deserialization allowlist
- SLA evaluation + coordinated omission detection
- HTML report generation
- Spring Boot 3.x REST API with OpenAPI/Swagger docs
- 11 protocol mock servers for self-smoke testing

---

## Quick Start

**Prerequisites:** Java 21 (`brew install openjdk@21` / `apt install openjdk-21-jdk`), Node.js 20 (`brew install node@20`)

```bash
# 1. Clone
git clone https://github.com/B3Cognition/b3-meter.git && cd b3-meter

# 2. Start the backend
./gradlew :modules:web-api:bootRun
# → API running at http://localhost:8080

# 3. Start the frontend (new terminal)
cd web-ui && npm install && npm run dev
# → UI running at http://localhost:3000

# 4. Open http://localhost:3000, create a test plan, click Run
```

API docs available at **http://localhost:8080/swagger-ui.html** once the backend is up.

---

## CLI (Headless Mode)

```bash
./gradlew :modules:engine-adapter:run \
  --args="--plan test-plans/http-smoke.jmx --non-gui --duration 30"
```

---

## Docker

```bash
# Single node
docker build -f Dockerfile.controller -t b3meter .
docker run -p 8080:8080 b3meter

# Distributed: 1 controller + 3 workers
docker compose -f docker-compose.distributed.yml up

# Self-smoke: 11 protocol mock servers
docker compose -f docker-compose.test.yml up
```

---

## Kubernetes

```bash
helm install b3meter ./deploy/helm/b3meter --set worker.replicaCount=5

# With persistent storage
helm install b3meter ./deploy/helm/b3meter \
  --set worker.replicaCount=5 \
  --set persistence.enabled=true \
  --set persistence.storageClass=gp3
```

A Grafana dashboard JSON is included at `deploy/grafana/b3meter-dashboard.json`.

---

## Architecture

```
┌─────────────────────────────────────────────────┐
│                React 19 Web UI                   │
│    (Plan Editor, Live Charts, 24 Themes)        │
├─────────────────────────────────────────────────┤
│             Spring Boot REST API                 │
│      (Plans, Runs, Streaming, Mocks)            │
├─────────────────────────────────────────────────┤
│              Engine Service                      │
│   NodeInterpreter → 27 Sampler Executors        │
│   VirtualUserExecutor (Java 21 virtual threads) │
│   ArrivalRateExecutor (semaphore VU pool)       │
│   LoadShapeController (6 built-in shapes)       │
│   HdrHistogramAccumulator (mergeable)           │
│   MetricsOutputManager → CSV/JSON/Influx/Prom  │
├─────────────────────────────────────────────────┤
│           Distributed Controller                 │
│   gRPC + WebSocket dual transport               │
│   DIVIDE/MULTIPLY modes                         │
│   ResultAggregator (histogram merge)            │
│   WorkerHealthPoller + CircuitBreaker           │
└─────────────────────────────────────────────────┘
```

`engine-service` has **zero framework dependencies** — pure JDK 21. It can be embedded in any Java application, tested without Spring, and is a candidate for GraalVM native-image compilation.

---

## Project Structure

```
modules/
  engine-service/          # Core engine — zero deps, pure JDK 21
  engine-adapter/          # CLI, JMX parser, HTTP clients (HC4/HC5)
  web-api/                 # Spring Boot REST API
  distributed-controller/  # gRPC distributed controller
  worker-node/             # gRPC worker
  worker-proto/            # Protobuf definitions
web-ui/                    # React 19 frontend
test-servers/              # 11 protocol mock servers (Node.js)
test-plans/                # Sample JMX test plans
deploy/
  helm/                    # Kubernetes Helm chart
  grafana/                 # Grafana dashboard
```

---

## Tests

```bash
./gradlew test                           # all backend tests
./gradlew :modules:engine-service:test   # engine only (no Spring context, fastest)
cd web-ui && npm test                    # frontend tests
```

CI runs on every push to `main`: spotless-check, backend-test, frontend-test, docker-build. OWASP dependency check runs weekly.

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup, code style, and contribution guidelines.

---

## License

Apache License 2.0 — see [LICENSE](LICENSE).

---

_Apache JMeter is a trademark of the Apache Software Foundation. b3meter is an independent project and is not affiliated with or endorsed by the Apache Software Foundation._
