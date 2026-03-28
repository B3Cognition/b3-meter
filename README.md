# jMeter Next

> A simple, portable, fork-friendly load testing platform for the cloud era.

![License](https://img.shields.io/badge/license-Apache%202.0-blue)
![Java](https://img.shields.io/badge/java-21-orange)
![React](https://img.shields.io/badge/react-19-61DAFB)

## What is this?

jMeter Next is a modern rewrite of Apache JMeter. It's designed to be:

- **Simple** — clone, build, run. No database, no Terraform, no cloud account needed.
- **Portable** — runs on your laptop, Docker, or Kubernetes. Fork it and add your own stuff.
- **Powerful** — 27 protocol samplers, arrival-rate executors, load shapes, distributed mode with accurate percentiles.

---

## Step-by-Step: Get Running in 5 Minutes

### Step 1: Check Prerequisites

You need Java 21 and Node.js 20. Check if you have them:

```bash
java -version    # needs 21 or higher
node -version    # needs 20 or higher
```

**Don't have Java 21?** Install it:
```bash
# macOS
brew install openjdk@21

# Ubuntu/Debian
sudo apt install openjdk-21-jdk

# Windows — download from https://adoptium.net/
```

**Don't have Node.js 20?** Install it:
```bash
# macOS
brew install node@20

# Ubuntu/Debian
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install nodejs
```

### Step 2: Clone the Repo

```bash
git clone https://github.com/Testimonial/jmeter-next.git
cd jmeter-next
```

### Step 3: Start the Backend

```bash
./gradlew :modules:web-api:bootRun
```

Wait until you see: `Started WebApiApplication in X seconds`

The API is now running at **http://localhost:8080**

### Step 4: Start the Frontend (new terminal)

```bash
cd web-ui
npm install
npm run dev
```

Open **http://localhost:3000** in your browser.

### Step 5: Run Your First Test

1. In the browser UI, create a new test plan
2. Add an HTTP Sampler pointing to any URL (e.g., `https://httpbin.org/get`)
3. Set thread count to 10 and duration to 30 seconds
4. Click **Run**
5. Watch real-time charts update live

**That's it. You're load testing.**

---

## Run from Command Line (No UI)

```bash
# Run a JMX test plan in headless mode
./gradlew :modules:engine-adapter:run --args="--plan test-plans/http-smoke.jmx --non-gui --duration 30"
```

---

## Run with Docker

### Single Node

```bash
docker build -f Dockerfile.controller -t jmeter-next .
docker run -p 8080:8080 jmeter-next
```

### Distributed Mode (1 Controller + 3 Workers)

```bash
docker compose -f docker-compose.distributed.yml up
```

This gives you 3 workers coordinated by 1 controller. Workers share the load equally (DIVIDE mode — 300 VUs total = 100 per worker).

### With Mock Servers (for testing the tool itself)

```bash
docker compose -f docker-compose.test.yml up
```

Starts 11 protocol mock servers (HTTP, WebSocket, SSE, HLS, MQTT, gRPC, DASH, WebRTC, FTP, LDAP, STUN) with health checks.

---

## Run on Kubernetes

```bash
helm install jmeter-next ./deploy/helm/jmeter-next \
  --set worker.replicaCount=5
```

That's it. The Helm chart creates a controller + 5 workers. No persistent storage required by default.

To enable persistent storage (keeps test plans across pod restarts):
```bash
helm install jmeter-next ./deploy/helm/jmeter-next \
  --set worker.replicaCount=5 \
  --set persistence.enabled=true \
  --set persistence.storageClass=gp3
```

---

## Features at a Glance

### 27 Protocol Samplers (Built-in, No Plugins Needed)

| Protocol | Description |
|----------|-------------|
| HTTP/HTTPS | Full HTTP client with connection pooling |
| WebSocket | Persistent connections, message send/receive |
| SSE | Server-Sent Events consumer |
| gRPC | HTTP/2 with protobuf framing |
| MQTT | Pub/sub with QoS levels |
| HLS | M3U8 playlist + segment download |
| DASH | MPEG-DASH streaming |
| WebRTC | Signaling + ICE/STUN |
| FTP | File upload/download |
| LDAP | Directory queries |
| **JDBC** | SQL database queries (5 query types, connection pool) |
| SMTP | Email sending |
| TCP | Raw socket |
| JSR223 | Groovy/JS/Python scripting |
| BeanShell | Legacy scripting |
| JUnit | Run JUnit tests as samplers |
| OS Process | Shell commands |
| Debug | Variable dump |

### Execution Models

| Model | Description |
|-------|-------------|
| **Constant VUs** | Fixed N virtual users looping for a duration (classic JMeter) |
| **Arrival Rate** | Fixed iterations/second — VU pool auto-scales to maintain rate |
| **Ramping Arrival Rate** | Rate changes across stages with linear interpolation |

### Load Shapes

| Shape | Description |
|-------|-------------|
| Constant | Fixed users for a duration |
| Ramp | Linear ramp up/down |
| Stages | Multi-stage with transitions |
| Step | Staircase pattern |
| Sinusoidal | Wave oscillation |
| Composite | Chain multiple shapes |

### Metrics Export (4 Backends)

| Backend | Config |
|---------|--------|
| **CSV** | JTL-compatible, configurable delimiter |
| **JSON** | NDJSON, one object per sample bucket |
| **InfluxDB** | Line protocol, supports v1 and v2 (Bearer token) |
| **Prometheus** | Pull endpoint on port 9270 |

### Distributed Mode

- **gRPC** primary transport with **WebSocket** fallback
- **DIVIDE mode** (default): 500 VUs / 5 workers = 100 each
- **Coordinated start**: all workers fire simultaneously
- **HDR Histogram merge**: accurate percentiles across workers (no averaging)
- **Circuit breaker** + health polling + auto-reconnect

### Security

- JWT authentication + RBAC
- SSRF protection (blocks RFC-1918, loopback, link-local)
- XStream security policy (JMX deserialization allowlist)
- Rate limiting on auth endpoints
- Resource ownership (users can only access their own runs)

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

The **engine-service** module has **zero framework dependencies** — pure JDK 21 types only. This means it can be embedded in any Java application, tested without Spring, and potentially compiled with GraalVM native-image.

---

## API Documentation

After starting the backend:
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs
- **Mock Server Status**: http://localhost:8080/api/v1/mocks/status

---

## Project Structure

```
jmeter-next/
├── modules/
│   ├── engine-service/          # Core engine (0 dependencies, pure JDK)
│   ├── engine-adapter/          # CLI + JMX parser + HTTP client
│   ├── web-api/                 # Spring Boot REST API
│   ├── web-ui/                  # React 19 frontend
│   ├── distributed-controller/  # gRPC controller
│   ├── worker-node/             # gRPC worker
│   └── worker-proto/            # Protobuf definitions
├── test-servers/                # 11 protocol mock servers (Node.js)
├── test-plans/                  # Sample JMX test plans
├── deploy/
│   ├── helm/                    # Kubernetes Helm chart
│   └── grafana/                 # Grafana dashboard
├── docker-compose.distributed.yml
├── docker-compose.test.yml
└── Makefile
```

---

## Running Tests

```bash
# All tests (backend)
./gradlew test

# Just engine tests (fastest — no Spring context)
./gradlew :modules:engine-service:test

# Frontend tests
cd web-ui && npm test
```

---

## Forking for Your Team

jMeter Next is designed to be forked. There are:

- **No company-specific URLs** in any workflow or config
- **No database to set up** (in-memory storage)
- **No cloud provider lock-in** (Helm works on any K8s)
- **No external service dependencies** for basic operation

To customize:
1. Fork the repo
2. Change image names in `.github/workflows/release.yml`
3. Add your own samplers in `modules/engine-service/src/main/java/.../interpreter/`
4. Add your own themes in `web-ui/src/themes/themes.ts`
5. Push to your registry

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup and guidelines.

## License

Apache License 2.0 — see [LICENSE](LICENSE).
