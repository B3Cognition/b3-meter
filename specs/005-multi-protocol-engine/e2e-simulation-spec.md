# Local E2E Simulation Environment Specification

**Version**: 1.0
**Date**: 2026-03-25
**Status**: Draft

---

## 1. Overview

The `test-server` module provides a complete local simulation environment that recreates
all discovered protocol endpoints. Running `docker compose up` starts all mock servers
alongside the jMeter Next backend, enabling fully offline E2E testing of every protocol
sampler without external dependencies.

### Directory Structure

```
test-server/
  docker-compose.yml
  Dockerfile.jmeter-next        # Backend (Spring Boot + engine)
  mock-http/
    Dockerfile
    server.js                    # Node.js Express mock
    config/
      routes.json                # Configurable route definitions
  mock-websocket/
    Dockerfile
    server.js                    # ws library-based mock
  mock-hls/
    Dockerfile
    server.js                    # HLS manifest + segment server
    media/
      master.m3u8
      variant_720p.m3u8
      segment_001.ts ... segment_010.ts
  mock-sse/
    Dockerfile
    server.js                    # SSE event emitter
  mock-grpc/
    Dockerfile
    server.go                    # Go gRPC mock server
    proto/
      test_service.proto
  mock-mqtt/
    Dockerfile
    mosquitto.conf               # Eclipse Mosquitto broker config
  test-plans/
    http-basic.jmx
    ws-subscribe.jmx
    hls-playback.jmx
    sse-listen.jmx
    grpc-unary.jmx
    mqtt-pubsub.jmx
    multi-protocol.jmx           # Mixed protocol test plan
  scenarios/
    happy-path.sh
    error-injection.sh
    load-patterns.sh
    sla-validation.sh
```

---

## 2. Mock Servers

### 2.1 HTTP Mock Server

**Technology**: Node.js + Express
**Port**: 8081
**Image**: `test-server/mock-http`

#### Capabilities

- **Configurable routes**: Load route definitions from `config/routes.json`:
  ```json
  {
    "routes": [
      {
        "method": "GET",
        "path": "/api/users",
        "status": 200,
        "body": {"users": [{"id": 1, "name": "Alice"}]},
        "delay_ms": 50,
        "headers": {"Content-Type": "application/json"}
      },
      {
        "method": "POST",
        "path": "/api/users",
        "status": 201,
        "body": {"id": 2, "name": "Bob"},
        "delay_ms": 100
      }
    ]
  }
  ```

- **Latency injection**: Per-route `delay_ms` field. Also supports global override
  via env var `MOCK_DELAY_MS`.

- **Error rate injection**: `error_rate` field (0.0-1.0) returns 500 Internal Server
  Error with configurable probability:
  ```json
  {
    "method": "GET",
    "path": "/api/unstable",
    "status": 200,
    "body": {"ok": true},
    "error_rate": 0.1,
    "error_status": 503,
    "error_body": {"error": "Service Unavailable"}
  }
  ```

- **Request logging**: Every request is logged to stdout as JSON:
  ```json
  {"timestamp": "...", "method": "GET", "path": "/api/users", "status": 200, "latency_ms": 52}
  ```

- **Admin endpoints**:
  - `POST /__admin/routes` — hot-reload route configuration
  - `GET /__admin/stats` — request count, avg latency, error count
  - `POST /__admin/reset` — reset stats counters
  - `POST /__admin/chaos` — enable/disable chaos mode (random 5xx, slow responses)

#### Health Check
`GET /__health` returns 200 with `{"status": "ok"}`.

---

### 2.2 WebSocket Mock Server

**Technology**: Node.js + `ws` library
**Port**: 8082
**Image**: `test-server/mock-websocket`

#### Capabilities

- **Echo mode**: Default behavior; echoes back any received message with a configurable
  delay.

- **Subscribe/broadcast pattern**: Clients send `{"action": "subscribe", "channel": "sports"}`
  and receive periodic broadcasts:
  ```json
  {"channel": "sports", "data": {"event": "goal", "team": "Home", "minute": 42}}
  ```
  Broadcast interval configurable via env var `WS_BROADCAST_INTERVAL_MS` (default: 1000).

- **Subprotocol negotiation**: Accepts `jmeter-worker/v1` and `echo` subprotocols.

- **Binary frame support**: Echoes binary frames as-is.

- **Connection lifecycle simulation**:
  - `MOCK_WS_MAX_CONNECTIONS` — reject new connections after this limit (default: 1000).
  - `MOCK_WS_DROP_RATE` — randomly drop connections with this probability (0.0-1.0).
  - `MOCK_WS_CLOSE_AFTER_MS` — force-close connections after N ms (0 = never).

#### Admin Endpoints (via HTTP on port 8082)
- `GET /admin/connections` — active connection count
- `POST /admin/broadcast` — send a message to all connected clients
- `POST /admin/disconnect-all` — close all connections

---

### 2.3 HLS Mock Server

**Technology**: Node.js + Express
**Port**: 8083
**Image**: `test-server/mock-hls`

#### Capabilities

- **Static VOD**: Serves pre-generated M3U8 playlists and TS segments from the
  `media/` directory.

- **Master playlist** (`/live/master.m3u8`):
  ```
  #EXTM3U
  #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
  /live/360p/media.m3u8
  #EXT-X-STREAM-INF:BANDWIDTH=1400000,RESOLUTION=960x540
  /live/540p/media.m3u8
  #EXT-X-STREAM-INF:BANDWIDTH=2800000,RESOLUTION=1280x720
  /live/720p/media.m3u8
  ```

- **Live simulation**: When `MOCK_HLS_LIVE=true`, the media playlist is dynamically
  generated with a sliding window of 3 segments. The segment list advances every
  `EXT-X-TARGETDURATION` seconds (default: 6). No `#EXT-X-ENDLIST` tag.

- **Segment latency**: `MOCK_HLS_SEGMENT_DELAY_MS` adds artificial download delay
  per segment.

- **Error injection**:
  - `MOCK_HLS_404_SEGMENT` — segment number that returns 404 (simulates CDN miss).
  - `MOCK_HLS_STALL_SEGMENT` — segment number with 10x normal delay (simulates buffering).

#### Test Media Files

10 pre-generated 6-second TS segments (720p, ~1.4Mbps) totaling 60 seconds of content.
Generated at build time using FFmpeg with a synthetic test pattern + tone audio.

---

### 2.4 SSE Mock Server

**Technology**: Node.js + Express
**Port**: 8084
**Image**: `test-server/mock-sse`

#### Capabilities

- **Event stream endpoint** (`/events`):
  - Sends events at configurable intervals (`MOCK_SSE_INTERVAL_MS`, default: 1000).
  - Event format:
    ```
    event: update
    id: 42
    data: {"temperature": 22.5, "timestamp": "2026-03-25T10:00:00Z"}

    ```

- **Event types**: Cycles through `update`, `alert`, `heartbeat` event types.

- **Last-Event-ID support**: When `Last-Event-ID` header is present, resumes from
  that event number (events are numbered sequentially from 1).

- **Retry directive**: Sends `retry: 3000` on initial connection.

- **Error injection**:
  - `MOCK_SSE_DROP_AFTER_EVENTS` — close connection after N events (simulates disconnect).
  - `MOCK_SSE_ERROR_RATE` — probability of sending malformed event data.

- **Multi-stream**: `/events/{channel}` supports named channels with independent
  event sequences.

---

### 2.5 gRPC Mock Server

**Technology**: Go + `google.golang.org/grpc`
**Port**: 50051
**Image**: `test-server/mock-grpc`

#### Capabilities

- **Service definition** (`proto/test_service.proto`):
  ```protobuf
  syntax = "proto3";
  package test;

  service TestService {
    rpc GetUser(GetUserRequest) returns (GetUserResponse);
    rpc ListUsers(ListUsersRequest) returns (stream GetUserResponse);
    rpc CreateUsers(stream CreateUserRequest) returns (CreateUsersResponse);
    rpc Chat(stream ChatMessage) returns (stream ChatMessage);
  }

  message GetUserRequest { int32 id = 1; }
  message GetUserResponse {
    int32 id = 1;
    string name = 2;
    string email = 3;
  }
  message ListUsersRequest { int32 count = 1; }
  message CreateUserRequest { string name = 1; string email = 2; }
  message CreateUsersResponse { int32 created_count = 1; }
  message ChatMessage { string sender = 1; string text = 2; }
  ```

- **Server reflection**: Enabled, so `GrpcSampler` can discover services dynamically.

- **Configurable latency**: `MOCK_GRPC_LATENCY_MS` env var (default: 10).

- **Error injection**:
  - `MOCK_GRPC_ERROR_RATE` — probability of returning INTERNAL status.
  - `MOCK_GRPC_DEADLINE_EXCEED` — probability of exceeding deadline (sleeps 2x deadline).

- **All four call types**: Unary, server streaming, client streaming, bidi streaming.

---

### 2.6 MQTT Mock Broker

**Technology**: Eclipse Mosquitto
**Port**: 1883 (TCP), 8883 (TLS)
**Image**: `eclipse-mosquitto:2`

#### Configuration (`mosquitto.conf`)

```
listener 1883
protocol mqtt
allow_anonymous true

listener 8883
protocol mqtt
certfile /mosquitto/config/server.crt
keyfile /mosquitto/config/server.key
require_certificate false

max_connections 10000
max_inflight_messages 100
max_queued_messages 1000

log_type all
log_dest stdout
```

#### Capabilities

- **QoS 0/1/2**: Full support via standard Mosquitto.
- **Retained messages**: Supported.
- **Clean session**: Supported.
- **TLS**: Self-signed certificate generated at build time.
- **Topic wildcards**: Standard MQTT wildcards (`+`, `#`).

#### Test Topics

Pre-seeded retained messages on startup via a companion init container:
- `test/temperature` — `{"value": 22.5, "unit": "C"}`
- `test/humidity` — `{"value": 65, "unit": "%"}`
- `test/heartbeat` — `{"alive": true}` (published every 5s by init container)

---

## 3. E2E Test Scenarios

### 3.1 Happy Path Scenarios

| ID | Protocol | Test Plan | Description | Assertions |
|----|----------|-----------|-------------|------------|
| HP-01 | HTTP | `http-basic.jmx` | 10 VUs, 5 iterations, GET /api/users | All 50 samples succeed, status 200 |
| HP-02 | WebSocket | `ws-subscribe.jmx` | 5 VUs subscribe to "sports" channel, receive 3 messages each | 15 messages received, all success |
| HP-03 | HLS | `hls-playback.jmx` | 3 VUs fetch master playlist + 3 segments each | 9 segments downloaded, no stalls |
| HP-04 | SSE | `sse-listen.jmx` | 5 VUs listen for 10s each | Each VU receives ~10 events |
| HP-05 | gRPC | `grpc-unary.jmx` | 10 VUs call GetUser, 5 iterations | 50 RPCs, all OK status |
| HP-06 | MQTT | `mqtt-pubsub.jmx` | 5 publisher VUs + 5 subscriber VUs, QoS 1 | All published messages received by subscribers |
| HP-07 | Mixed | `multi-protocol.jmx` | HTTP login + WS subscribe + SSE listen | All protocol phases succeed |

### 3.2 Error Injection Scenarios

| ID | Protocol | Injection | Expected Behavior |
|----|----------|-----------|-------------------|
| EI-01 | HTTP | `error_rate: 0.5` on /api/users | ~50% error rate in SampleBucket, error samples have statusCode 503 |
| EI-02 | WebSocket | `MOCK_WS_DROP_RATE=0.3` | ~30% of VUs report connection drop, reconnect logic tested |
| EI-03 | HLS | `MOCK_HLS_404_SEGMENT=5` | Segment 5 fails, other segments succeed, overall sample marked partial failure |
| EI-04 | SSE | `MOCK_SSE_DROP_AFTER_EVENTS=3` | Connection drops after 3 events, reconnect with Last-Event-ID, total events > 3 |
| EI-05 | gRPC | `MOCK_GRPC_ERROR_RATE=0.2` | ~20% of RPCs return INTERNAL, error count matches |
| EI-06 | MQTT | Broker restart mid-test | Clients reconnect, messages after reconnect are delivered |
| EI-07 | HTTP | `MOCK_DELAY_MS=5000` | Response times ~5s, timeout if sampler timeout < 5s |

### 3.3 Load Pattern Scenarios

| ID | Pattern | Configuration | Duration | VUs | Protocol |
|----|---------|---------------|----------|-----|----------|
| LP-01 | Constant rate | 10 VU, infinite loop, 60s duration | 60s | 10 | HTTP |
| LP-02 | Ramp-up | 1 to 50 VUs over 30s, hold 30s | 60s | 1-50 | HTTP |
| LP-03 | Spike | 5 VU steady, spike to 100 VU for 10s | 60s | 5-100 | WebSocket |
| LP-04 | Soak | 10 VU, 300s duration | 300s | 10 | Mixed |
| LP-05 | Step | 10/20/30/40/50 VUs, 20s per step | 100s | 10-50 | gRPC |

**Note**: Load patterns LP-02, LP-03, LP-05 require the `RampingThreadGroup` or
`SteppingThreadGroup` plan elements (future enhancement). For Phase 1, these patterns
are simulated by running multiple sequential test plans with different VU counts.

### 3.4 Metrics Verification Scenarios

| ID | Protocol | Verification | Method |
|----|----------|-------------|--------|
| MV-01 | HTTP | SampleBucket.sampleCount == expected | Subscribe to broker, count buckets |
| MV-02 | HTTP | SampleBucket.errorCount == 0 for happy path | Assert errorPercent() == 0.0 |
| MV-03 | HTTP | p95 < MOCK_DELAY_MS + 50ms headroom | Assert percentile95 in range |
| MV-04 | WebSocket | Total messages == VUs * expected_messages | Sum across all VU results |
| MV-05 | HLS | Total segments == VUs * segment_count | Count segment downloads |
| MV-06 | gRPC | gRPC status OK count == total - (total * error_rate) | Tolerate +/- 5% |
| MV-07 | MQTT | Pub count == Sub received count (QoS 1) | Assert message delivery completeness |

### 3.5 SLA Validation Scenarios

| ID | SLA Rule | Threshold | Protocol | How Tested |
|----|----------|-----------|----------|------------|
| SV-01 | p50 latency | < 100ms | HTTP (50ms mock delay) | Assert SampleBucket.percentile90 < 100 |
| SV-02 | p95 latency | < 500ms | HTTP (100ms mock delay + jitter) | Assert percentile95 < 500 |
| SV-03 | p99 latency | < 1000ms | HTTP | Assert percentile99 < 1000 |
| SV-04 | Error rate | < 1% | HTTP (0% error injection) | Assert errorPercent() < 1.0 |
| SV-05 | Throughput | > 100 req/s | HTTP (10 VUs, fast mock) | Assert samplesPerSecond > 100 |
| SV-06 | WS connect latency | < 200ms | WebSocket | Assert connectTimeMs < 200 for all samples |
| SV-07 | HLS stall rate | 0% | HLS (no error injection) | Assert zero buffer stalls |

---

## 4. Docker Compose Design

```yaml
# docker-compose.yml
version: "3.9"

services:
  # ---------------------------------------------------------------
  # jMeter Next Backend (Spring Boot)
  # ---------------------------------------------------------------
  jmeter-next:
    build:
      context: ../
      dockerfile: test-server/Dockerfile.jmeter-next
    ports:
      - "8080:8080"       # REST API + SSE
    environment:
      - SPRING_PROFILES_ACTIVE=test
      - SERVER_PORT=8080
    depends_on:
      mock-http:
        condition: service_healthy
      mock-websocket:
        condition: service_healthy
      mock-hls:
        condition: service_healthy
      mock-sse:
        condition: service_healthy
      mock-grpc:
        condition: service_healthy
      mock-mqtt:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 5s
      timeout: 3s
      retries: 10

  # ---------------------------------------------------------------
  # HTTP Mock Server
  # ---------------------------------------------------------------
  mock-http:
    build: ./mock-http
    ports:
      - "8081:8081"
    environment:
      - PORT=8081
      - MOCK_DELAY_MS=50
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/__health"]
      interval: 3s
      timeout: 2s
      retries: 5

  # ---------------------------------------------------------------
  # WebSocket Mock Server
  # ---------------------------------------------------------------
  mock-websocket:
    build: ./mock-websocket
    ports:
      - "8082:8082"
    environment:
      - PORT=8082
      - WS_BROADCAST_INTERVAL_MS=1000
      - MOCK_WS_MAX_CONNECTIONS=1000
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8082/admin/connections"]
      interval: 3s
      timeout: 2s
      retries: 5

  # ---------------------------------------------------------------
  # HLS Mock Server
  # ---------------------------------------------------------------
  mock-hls:
    build: ./mock-hls
    ports:
      - "8083:8083"
    environment:
      - PORT=8083
      - MOCK_HLS_LIVE=false
      - MOCK_HLS_SEGMENT_DELAY_MS=0
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8083/live/master.m3u8"]
      interval: 3s
      timeout: 2s
      retries: 5

  # ---------------------------------------------------------------
  # SSE Mock Server
  # ---------------------------------------------------------------
  mock-sse:
    build: ./mock-sse
    ports:
      - "8084:8084"
    environment:
      - PORT=8084
      - MOCK_SSE_INTERVAL_MS=1000
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8084/__health"]
      interval: 3s
      timeout: 2s
      retries: 5

  # ---------------------------------------------------------------
  # gRPC Mock Server
  # ---------------------------------------------------------------
  mock-grpc:
    build: ./mock-grpc
    ports:
      - "50051:50051"
    environment:
      - PORT=50051
      - MOCK_GRPC_LATENCY_MS=10
    healthcheck:
      test: ["CMD", "/bin/grpc_health_probe", "-addr=:50051"]
      interval: 3s
      timeout: 2s
      retries: 5

  # ---------------------------------------------------------------
  # MQTT Broker (Mosquitto)
  # ---------------------------------------------------------------
  mock-mqtt:
    image: eclipse-mosquitto:2
    ports:
      - "1883:1883"
      - "8883:8883"
    volumes:
      - ./mock-mqtt/mosquitto.conf:/mosquitto/config/mosquitto.conf
    healthcheck:
      test: ["CMD", "mosquitto_sub", "-t", "$$SYS/broker/uptime", "-C", "1", "-W", "2"]
      interval: 5s
      timeout: 3s
      retries: 5

  # ---------------------------------------------------------------
  # MQTT Init Container (seed retained messages)
  # ---------------------------------------------------------------
  mqtt-init:
    image: eclipse-mosquitto:2
    depends_on:
      mock-mqtt:
        condition: service_healthy
    entrypoint: /bin/sh
    command: >
      -c "
        mosquitto_pub -h mock-mqtt -t test/temperature -m '{\"value\":22.5,\"unit\":\"C\"}' -r -q 1 &&
        mosquitto_pub -h mock-mqtt -t test/humidity -m '{\"value\":65,\"unit\":\"%\"}' -r -q 1 &&
        echo 'MQTT topics seeded'
      "
    restart: "no"

# ---------------------------------------------------------------
# Networks
# ---------------------------------------------------------------
networks:
  default:
    name: jmeter-next-test
```

### Usage

```bash
# Start everything
docker compose up -d

# Run E2E tests (from project root)
./gradlew :test-server:e2eTest

# Run specific scenario
./test-server/scenarios/happy-path.sh

# Tear down
docker compose down -v

# Start with error injection
MOCK_DELAY_MS=5000 MOCK_WS_DROP_RATE=0.3 docker compose up -d
```

---

## 5. Test Runner Integration

E2E tests are Gradle tasks in a new `test-server` module:

```kotlin
// test-server/build.gradle.kts
plugins {
    java
}

tasks.register<Test>("e2eTest") {
    description = "Runs E2E tests against dockerized mock servers"
    group = "verification"
    useJUnitPlatform {
        includeTags("e2e")
    }
    // Ensure Docker Compose is up before tests run
    dependsOn("dockerComposeUp")
    finalizedBy("dockerComposeDown")
}
```

E2E test classes use `EngineServiceImpl` directly (in-process) and point sampler
URLs at the Docker mock servers:

```java
@Tag("e2e")
class HttpHappyPathE2ETest {
    // Use EngineServiceImpl with real HTTP client, pointed at mock-http:8081
    // Assert SampleBucket values from the broker
}
```

---

## 6. CI Integration

The Docker Compose stack runs in CI (GitHub Actions) as part of a dedicated E2E job:

```yaml
# .github/workflows/e2e.yml
jobs:
  e2e:
    runs-on: ubuntu-latest
    services:
      # Services started by docker-compose in the step below
    steps:
      - uses: actions/checkout@v4
      - name: Start mock servers
        run: docker compose -f test-server/docker-compose.yml up -d --wait
      - name: Run E2E tests
        run: ./gradlew :test-server:e2eTest
      - name: Collect logs on failure
        if: failure()
        run: docker compose -f test-server/docker-compose.yml logs > e2e-logs.txt
      - name: Upload logs
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: e2e-logs
          path: e2e-logs.txt
      - name: Tear down
        if: always()
        run: docker compose -f test-server/docker-compose.yml down -v
```
