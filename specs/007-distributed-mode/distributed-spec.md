# 007 -- Distributed Mode Specification

**Status:** Draft
**Author:** INVESTIGATOR
**Date:** 2026-03-26

---

## 1. Current State Audit

### 1.1 What Already Exists

The jMeter Next codebase has **substantial distributed mode infrastructure** already implemented across four modules:

| Module | Purpose | Key Classes |
|--------|---------|-------------|
| `modules/worker-proto` | gRPC service definition (protobuf) | `worker.proto` -- defines `WorkerService` with 6 RPCs |
| `modules/distributed-controller` | Controller node (Spring Boot) | `DistributedRunService`, `WorkerClient`, `ResultAggregator`, `WorkerHealthPoller`, `WorkerTransportSelector`, `GrpcWorkerTransport`, `WebSocketWorkerTransport` |
| `modules/worker-node` | Worker node (Spring Boot) | `WorkerServiceImpl`, `WorkerGrpcServer`, `WorkerNodeApplication` |
| `modules/web-ui` | React UI panel | `DistributedConfig.tsx`, `DistributedConfig.css`, `workers.ts` API client |

### 1.2 Wire Protocol (worker.proto)

The `WorkerService` gRPC service is fully defined with six RPCs:

```
service WorkerService {
    rpc Configure(TestPlanMessage) returns (ConfigureAck);
    rpc Start(StartMessage) returns (StartAck);
    rpc Stop(StopMessage) returns (StopAck);
    rpc StopNow(StopMessage) returns (StopAck);
    rpc StreamResults(StopMessage) returns (stream SampleResultBatch);
    rpc GetHealth(HealthRequest) returns (HealthStatus);
}
```

Worker lifecycle states: `IDLE -> CONFIGURED -> RUNNING -> STOPPING -> IDLE` (plus `ERROR`).

### 1.3 Controller-Side Implementation

**DistributedRunService** orchestrates the full lifecycle:
- Phase 1: Configure -- sends test plan to each worker with evenly-split VUs (remainder goes to worker 0)
- Phase 2: Coordinated Start -- sets a future timestamp 5 seconds ahead so all workers begin at the same wall-clock instant
- Phase 3: Stream Results -- opens `StreamResults` on each worker, routed through `ResultAggregator`
- Graceful and immediate stop supported, with rollback on configure failure

**WorkerClient** wraps a gRPC `ManagedChannel` with:
- 30-second deadline on all blocking RPCs
- Circuit breaker: 3 consecutive failures trip the circuit; successful call resets it
- Async `streamResults` with `Consumer<SampleResultBatch>` callback

**ResultAggregator** merges streams from N workers:
- Converts proto `SampleResultBatch` to domain `SampleBucket`
- Publishes to `SampleStreamBroker` (same broker used by SSE, SLA evaluator, dashboard)
- Tracks per-run sample/error totals
- Manages active worker subscriptions per run

**WorkerHealthPoller** provides liveness monitoring:
- Polls every 5 seconds via `GetHealth` RPC
- Declares worker unavailable after 3 consecutive missed heartbeats (15 seconds)
- Emits `AVAILABLE`/`UNAVAILABLE` state-change events to registered listeners

**WorkerTransportSelector** provides transport negotiation:
- Probes gRPC first (5-second timeout)
- Falls back to WebSocket if gRPC is unreachable (e.g., behind a proxy)
- Returns whichever `WorkerTransport` succeeds health probe

### 1.4 Worker-Side Implementation

**WorkerServiceImpl** implements all 6 gRPC RPCs:
- Validates state transitions (must be IDLE to accept Configure, CONFIGURED to accept Start)
- Creates a `TestRunContext` on start
- Currently emits **synthetic** result batches every 3 seconds (stub -- not wired to real JMeter engine)
- Publishes via `InMemorySampleStreamBroker` which fans out to `StreamResults` observers

**WorkerGrpcServer** manages the Netty gRPC server:
- Binds to configurable TCP port
- JVM shutdown hook for graceful termination (30-second drain)

**WorkerNodeApplication** is a Spring Boot entry point.

### 1.5 WebSocket Transport (Alternative to gRPC)

A complete WebSocket transport implementation exists (`WebSocketWorkerTransport`) with:
- Binary framing: `[4-byte total length][4-byte message type][protobuf body]`
- 9 message types mapping 1:1 to the gRPC RPCs
- RFC 6455 compliant handshake with `jmeter-worker/v1` subprotocol negotiation
- Client-side masking per RFC 6455 section 5.1
- Automatic reconnection with exponential back-off (1s, 2s, 4s, 8s, 16s, 30s cap)
- Frame length validation with 1002 (Protocol Error) close on mismatch

### 1.6 UI Component

**DistributedConfig** React component provides:
- Worker list with Online/Busy/Offline status badges
- Checkbox selection for choosing which workers participate in a run
- Inline form to register new workers (hostname + port)
- Remove button to deregister workers
- "Start Distributed Run" button (enabled when planId is set and workers are selected)
- REST API calls: `GET /workers`, `POST /workers`, `DELETE /workers/:id`

**Tests:** 15 Vitest tests covering loading states, rendering, selection, start button logic, add/remove forms.

### 1.7 Gaps Identified

| Gap | Severity | Description |
|-----|----------|-------------|
| **No real engine wiring** | Critical | `WorkerServiceImpl.emitResultBatch()` produces synthetic data. Not connected to the actual JMeter engine adapter. |
| **No REST API backend** | High | The `web-ui` calls `GET/POST/DELETE /workers` but `modules/web-api` has no worker endpoints. |
| **No Kubernetes manifests** | High | No Helm chart, no Deployment/StatefulSet YAMLs. |
| **No Docker Compose for distributed** | Medium | `docker-compose.test.yml` only has mock servers + the main app. No worker containers. |
| **No CLI integration** | Medium | Git log shows `controller` and `worker` subcommands were committed but the CLI class was not found in the codebase. May be unbuilt or on another branch. |
| **Percentile aggregation is naive** | Medium | `ResultAggregator` passes through per-worker percentiles rather than merging histograms. Averaging p99 across workers produces incorrect results. |
| **No HDR Histogram** | Medium | Proto `SampleResultBatch` carries pre-computed percentiles, not raw histograms. Cannot merge accurately. |
| **No worker auto-discovery** | Low | Workers must be manually registered via UI. No DNS-based or Kubernetes service discovery. |
| **No mTLS** | Low | gRPC uses `usePlaintext()`. WebSocket has no TLS. Acceptable for internal networks but not for cross-cloud. |

---

## 2. Architecture Options

### Option A: gRPC (Current Implementation)

**How it works:** Controller opens gRPC channels to each worker. Workers expose `WorkerService` on a TCP port. Communication is bidirectional via server-streaming for results.

| Aspect | Assessment |
|--------|-----------|
| Latency | Excellent -- HTTP/2 multiplexing, binary protobuf |
| Streaming | Native server-streaming with backpressure |
| Code reuse | 90% already implemented |
| Proxy compatibility | Poor -- many corporate proxies block HTTP/2 / gRPC |
| Observability | Good -- gRPC interceptors, health checks via standard gRPC health protocol |
| Complexity | Low -- well-typed, generated stubs |

### Option B: REST + SSE

**How it works:** Controller sends commands via REST POST to each worker. Workers push results via Server-Sent Events (SSE) back to the controller.

| Aspect | Assessment |
|--------|-----------|
| Latency | Good -- HTTP/1.1, JSON serialization overhead |
| Streaming | SSE is text-only, unidirectional, no backpressure |
| Code reuse | Low -- would need to rewrite all RPC wrappers |
| Proxy compatibility | Excellent -- works through any HTTP proxy |
| Observability | Good -- standard HTTP logging |
| Complexity | Medium -- manual request/response correlation |

### Option C: WebSocket (Already Implemented as Fallback)

**How it works:** Controller connects to each worker via WebSocket. Binary frames carry protobuf messages. Workers push `SampleResultBatch` frames after start.

| Aspect | Assessment |
|--------|-----------|
| Latency | Excellent -- persistent connection, binary framing |
| Streaming | Full-duplex, push-based |
| Code reuse | 80% already implemented |
| Proxy compatibility | Good -- WebSocket upgrade works through most proxies |
| Observability | Medium -- custom framing requires custom tooling |
| Complexity | Medium -- manual frame parsing, reconnection logic |

---

## 3. Recommended Architecture

**Recommendation: gRPC as primary, WebSocket as fallback (Status quo, extended)**

Justification:
1. **Already 90% implemented** -- both gRPC and WebSocket transports exist with tests
2. **WorkerTransportSelector** already probes gRPC first, falls back to WebSocket
3. **gRPC is the industry standard** for inter-service communication in Kubernetes
4. **Protobuf schema** provides backward-compatible evolution via field numbering
5. **WebSocket fallback** handles the proxy/firewall edge case without code duplication (both transports use the same protobuf messages)

### What Needs to Be Built

The core distributed protocol is done. The remaining work is infrastructure and integration:

1. Wire `WorkerServiceImpl` to the real JMeter engine adapter
2. Add REST endpoints to `web-api` for worker management
3. Create Kubernetes manifests (Helm chart)
4. Create Docker Compose for local development
5. Fix percentile aggregation with HDR Histogram
6. Add Kubernetes service discovery (optional)

---

## 4. Worker Lifecycle

### 4.1 State Machine

```
                 Configure(plan)         Start(timestamp)
    IDLE ──────────────────────> CONFIGURED ────────────────> RUNNING
     ^                              |                           |
     |                              | (rejected/error)          |
     |                              v                           |
     |                           ERROR                          |
     |                                                          |
     |    Stop() ─── STOPPING ──────────────────────────────────┘
     |                   |
     └───────────────────┘  (drain complete)

     StopNow() ── RUNNING ──> IDLE  (immediate, skip STOPPING)
```

### 4.2 Registration

Currently manual via the UI (`POST /workers` with hostname + port). For Kubernetes:

**Option 1 -- Manual (current):** Operator registers each worker pod IP/hostname. Simplest but does not scale.

**Option 2 -- Kubernetes headless Service:** Controller discovers workers via DNS SRV records on a headless Service. Each worker pod registers on startup; DNS updates automatically as pods scale.

**Option 3 -- Worker self-registration:** Each worker pod sends a gRPC `Register` call to the controller on startup. Requires a new RPC in the proto or a REST endpoint.

**Recommendation:** Option 2 (headless Service) for Kubernetes; Option 1 for Docker Compose / bare metal.

### 4.3 Heartbeat and Failure Detection

Already implemented in `WorkerHealthPoller`:
- Polls `GetHealth` every 5 seconds
- 3 missed heartbeats (15 seconds) = UNAVAILABLE
- State-change events emitted to listeners
- Circuit breaker in `WorkerClient` trips after 3 consecutive RPC failures

### 4.4 Coordinated Start

Already implemented in `DistributedRunService`:
- Controller computes `start_at = now + 5 seconds`
- All workers receive the same `start_at` timestamp
- Workers begin generating load at exactly that wall-clock instant
- Requires NTP-synchronized clocks across pods (Kubernetes provides this by default)

### 4.5 Graceful Shutdown

Already implemented:
- `Stop` RPC transitions worker `RUNNING -> STOPPING` (drain in-flight samples)
- `StopNow` RPC transitions `RUNNING -> IDLE` immediately
- Controller iterates workers sequentially; skips workers with open circuit breakers
- JVM shutdown hook in `WorkerGrpcServer` ensures clean termination on SIGTERM

---

## 5. Kubernetes Deployment

### 5.1 Pod Topology

```
                        ┌─────────────────────────────┐
                        │  Ingress / LoadBalancer      │
                        │  (port 8080 web-ui + API)    │
                        └──────────┬──────────────────┘
                                   │
                        ┌──────────▼──────────────────┐
                        │  controller (StatefulSet)    │
                        │  replicas: 1                 │
                        │  ports: 8080 (HTTP), 50050   │
                        │         (gRPC, optional)     │
                        └──────────┬──────────────────┘
                                   │ gRPC :50051
                    ┌──────────────┼──────────────────┐
                    │              │                   │
             ┌──────▼─────┐ ┌─────▼──────┐ ┌─────────▼──────┐
             │  worker-0   │ │  worker-1   │ │  worker-2      │
             │  (pod)      │ │  (pod)      │ │  (pod)         │
             │  port 50051 │ │  port 50051 │ │  port 50051    │
             └─────────────┘ └─────────────┘ └────────────────┘
                    Deployment (replicas: 3, HPA)
```

### 5.2 Helm Chart Structure

```
helm/jmeter-next/
  Chart.yaml
  values.yaml
  templates/
    controller-statefulset.yaml
    controller-service.yaml
    worker-deployment.yaml
    worker-service.yaml          # headless Service for discovery
    worker-hpa.yaml              # HorizontalPodAutoscaler
    ingress.yaml
    configmap.yaml               # shared config (gRPC port, health interval)
    serviceaccount.yaml
    rbac.yaml                    # if using K8s API for discovery
```

### 5.3 Key values.yaml Parameters

```yaml
controller:
  replicas: 1                    # single controller (stateful)
  image: jmeter-next-controller:latest
  resources:
    requests: { cpu: 500m, memory: 512Mi }
    limits:   { cpu: 2000m, memory: 2Gi }
  ports:
    http: 8080
    grpc: 50050                  # optional controller gRPC

worker:
  replicas: 3                    # default worker count
  image: jmeter-next-worker:latest
  resources:
    requests: { cpu: 1000m, memory: 1Gi }
    limits:   { cpu: 4000m, memory: 4Gi }
  port: 50051                    # gRPC port
  hpa:
    enabled: true
    minReplicas: 1
    maxReplicas: 20
    targetCPU: 70

discovery:
  method: headless-service       # or "manual"
  serviceName: jmeter-workers    # DNS: worker-0.jmeter-workers.namespace.svc.cluster.local

healthCheck:
  intervalMs: 5000
  missedHeartbeats: 3
```

### 5.4 Worker Headless Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: jmeter-workers
spec:
  clusterIP: None               # headless
  selector:
    app: jmeter-next-worker
  ports:
    - name: grpc
      port: 50051
      targetPort: 50051
```

Controller discovers workers by querying DNS for `jmeter-workers.{namespace}.svc.cluster.local`, which returns A records for each pod.

---

## 6. Docker Compose (Local Development)

```yaml
version: '3.8'

services:
  controller:
    build:
      context: .
      dockerfile: Dockerfile.controller
    ports:
      - "8080:8080"       # REST API + Web UI
      - "3000:3000"       # Vite dev server (if used)
    environment:
      - WORKER_ADDRESSES=worker-1:50051,worker-2:50051,worker-3:50051
      - GRPC_PORT=50050
    depends_on:
      - worker-1
      - worker-2
      - worker-3

  worker-1:
    build:
      context: .
      dockerfile: Dockerfile.worker
    environment:
      - GRPC_PORT=50051
      - WORKER_ID=worker-1
    ports:
      - "50061:50051"     # host-mapped for debugging

  worker-2:
    build:
      context: .
      dockerfile: Dockerfile.worker
    environment:
      - GRPC_PORT=50051
      - WORKER_ID=worker-2
    ports:
      - "50062:50051"

  worker-3:
    build:
      context: .
      dockerfile: Dockerfile.worker
    environment:
      - GRPC_PORT=50051
      - WORKER_ID=worker-3
    ports:
      - "50063:50051"
```

### Dockerfiles Required

**Dockerfile.controller** -- Multi-stage build:
1. Gradle build stage: `./gradlew :modules:distributed-controller:bootJar`
2. Runtime stage: `eclipse-temurin:21-jre-jammy` base, copy bootJar, expose 8080

**Dockerfile.worker** -- Multi-stage build:
1. Gradle build stage: `./gradlew :modules:worker-node:bootJar`
2. Runtime stage: same base, copy bootJar, expose 50051

---

## 7. UI Integration

### 7.1 Current State

The `DistributedConfig` component is functional but disconnected:
- Calls `GET/POST/DELETE /workers` but no backend endpoints exist
- `onStartDistributedRun` callback passes `string[]` addresses to the parent, but the parent does not forward them to `POST /api/runs`

### 7.2 Required Backend Endpoints

| Method | Path | Body | Response | Notes |
|--------|------|------|----------|-------|
| `GET` | `/api/workers` | -- | `WorkerSummary[]` | List registered workers with current status |
| `POST` | `/api/workers` | `{ hostname, port }` | `WorkerSummary` | Register a new worker; controller probes health |
| `DELETE` | `/api/workers/:id` | -- | 204 | Deregister worker |
| `GET` | `/api/workers/:id/health` | -- | `HealthStatus` | Live health probe for a specific worker |

### 7.3 StartRunRequest Integration

The `StartRunRequest` type already includes `workerAddresses?: string[]`. When populated, the backend should:
1. Resolve worker IDs from addresses
2. Call `DistributedRunService.startDistributed()` instead of the local engine
3. Return the distributed run ID
4. SSE metrics stream should aggregate results from all workers (already handled by `ResultAggregator`)

### 7.4 UI Enhancements (Future)

- **Per-worker metrics:** Show individual worker throughput in the dashboard alongside aggregated totals
- **Worker health indicators:** Real-time status updates via SSE (poll `WorkerHealthPoller` events)
- **Auto-scale controls:** Button to request more workers (triggers HPA or manual scaling)
- **Worker logs:** Stream worker stdout/stderr to the UI for debugging

---

## 8. Metrics Aggregation

### 8.1 Current Approach (Flawed)

`ResultAggregator` passes through per-worker percentiles directly to the broker. If worker-1 reports p99=200ms and worker-2 reports p99=800ms, downstream consumers see two separate `SampleBucket` objects. They do not see a merged p99 that accounts for all samples.

### 8.2 The Percentile Merging Problem

**You cannot average percentiles.** If worker-1 has 1000 samples with p99=200ms and worker-2 has 10 samples with p99=800ms, the true p99 is approximately 200ms (dominated by worker-1's distribution), not 500ms (the average).

### 8.3 Recommended Solution: HDR Histogram

**Change the proto to transmit compressed HDR Histogram snapshots instead of pre-computed percentiles.**

```protobuf
message SampleResultBatch {
    // ... existing fields ...

    // Replace:
    //   map<string, double> percentiles = 6;
    // With:
    bytes hdr_histogram = 6;    // compressed HDR Histogram snapshot (ZLIB)
}
```

Controller-side aggregation:
1. Each worker serializes its HDR Histogram for the reporting window using `Histogram.encodeIntoCompressedByteBuffer()`
2. Controller receives N histograms and calls `targetHistogram.add(workerHistogram)` to merge
3. Controller computes percentiles from the merged histogram: `merged.getValueAtPercentile(99.0)`
4. This produces mathematically correct percentiles across all workers

**Library:** `org.hdrhistogram:HdrHistogram:2.2.2` (already commonly used in JMeter ecosystem).

### 8.4 Aggregation Fields

| Field | Aggregation Method |
|-------|-------------------|
| `sample_count` | Sum across all workers |
| `error_count` | Sum across all workers |
| `avg_response_time` | Weighted average: `sum(avg_i * count_i) / sum(count_i)` |
| `min_response_time` | Min across all workers |
| `max_response_time` | Max across all workers |
| `percentiles` | HDR Histogram merge, then extract from merged histogram |
| `samples_per_second` | Sum across all workers |
| `timestamp` | Use the earliest timestamp from the batch window |

### 8.5 Reporting Windows

Workers emit one batch per sampler label per 5-second window (configurable). Controller waits up to 2 seconds past the window boundary for stragglers before computing the aggregated bucket. Late-arriving batches are folded into the next aggregation cycle.

---

## 9. Implementation Tasks

### Phase 1: Wire to Real Engine (Critical, ~3 days)

| # | Task | Effort | Description |
|---|------|--------|-------------|
| 1.1 | Wire `WorkerServiceImpl` to engine-adapter | 2d | Replace synthetic `emitResultBatch()` with actual `EngineServiceImpl.start()` call. Subscribe to engine's `SampleStreamBroker` and forward buckets to gRPC stream. |
| 1.2 | Test with a real JMX plan | 1d | End-to-end test: controller sends plan to worker, worker runs it, results stream back. |

### Phase 2: REST API Backend (~2 days)

| # | Task | Effort | Description |
|---|------|--------|-------------|
| 2.1 | Add `WorkerController` to `web-api` | 1d | `GET/POST/DELETE /api/workers` endpoints backed by in-memory registry. |
| 2.2 | Integrate distributed start into `RunController` | 1d | When `StartRunRequest.workerAddresses` is populated, delegate to `DistributedRunService` instead of local engine. |

### Phase 3: Docker Compose (~1 day)

| # | Task | Effort | Description |
|---|------|--------|-------------|
| 3.1 | Create `Dockerfile.controller` | 0.5d | Multi-stage Gradle build + JRE runtime. |
| 3.2 | Create `Dockerfile.worker` | 0.5d | Same pattern. |
| 3.3 | Create `docker-compose.distributed.yml` | 0.5d | 1 controller + 3 workers, wired with environment variables. |

### Phase 4: Kubernetes Helm Chart (~2 days)

| # | Task | Effort | Description |
|---|------|--------|-------------|
| 4.1 | Create Helm chart skeleton | 0.5d | `Chart.yaml`, `values.yaml`, `_helpers.tpl` |
| 4.2 | Controller StatefulSet + Service | 0.5d | Ingress-exposed HTTP + optional gRPC port. |
| 4.3 | Worker Deployment + headless Service | 0.5d | Auto-scaled via HPA. |
| 4.4 | Kubernetes-based worker discovery | 0.5d | Controller queries headless Service DNS on startup and periodically. |

### Phase 5: Metrics Accuracy (~2 days)

| # | Task | Effort | Description |
|---|------|--------|-------------|
| 5.1 | Add HDR Histogram to proto | 0.5d | Replace `map<string, double> percentiles` with `bytes hdr_histogram`. |
| 5.2 | Worker-side histogram serialization | 0.5d | Accumulate HDR Histogram per sampler label per window; serialize with ZLIB. |
| 5.3 | Controller-side histogram merge | 0.5d | Merge N histograms; extract correct percentiles. |
| 5.4 | Window-based aggregation with straggler timeout | 0.5d | Wait up to 2 seconds past window boundary before emitting aggregated bucket. |

### Phase 6: Polish (~2 days)

| # | Task | Effort | Description |
|---|------|--------|-------------|
| 6.1 | Per-worker metrics in UI | 1d | Show individual worker throughput alongside aggregated totals. |
| 6.2 | mTLS for gRPC channels | 0.5d | Configure TLS certificates for production deployments. |
| 6.3 | CI integration test | 0.5d | GitHub Actions workflow: spin up controller + 2 workers in Docker Compose, run a simple plan, assert results. |

### Total Estimated Effort: ~12 days

### Dependency Graph

```
Phase 1 (engine wiring)
    |
    v
Phase 2 (REST API) ──────> Phase 6.1 (per-worker UI)
    |
    v
Phase 3 (Docker Compose)
    |
    v
Phase 4 (Kubernetes) ──> Phase 6.2 (mTLS)
                          Phase 6.3 (CI)

Phase 5 (HDR Histogram) -- independent, can run in parallel with Phase 2-4
```

---

## Appendix A: Comparison with Apache JMeter Distributed Mode

| Aspect | Apache JMeter | jMeter Next |
|--------|--------------|-------------|
| Protocol | Java RMI (`jmeter-server`) | gRPC + WebSocket fallback |
| Configuration | `remote_hosts` property in jmeter.properties | UI-based worker registration + K8s discovery |
| Start coordination | No synchronization | Coordinated start via future timestamp |
| Result aggregation | Controller merges at sample level (slow) | Stream-based with HDR Histogram merge |
| Failure handling | Silent failure, no retry | Circuit breaker + health poller + auto-reconnect |
| Scaling | Manual process management | HPA-based auto-scaling in Kubernetes |
| Transport security | RMI SSL (complex setup) | mTLS on gRPC (standard) |

## Appendix B: Comparison with StreamLoad (ws-load-test-c)

StreamLoad's distributed mode (`distributed.c`) uses a custom binary TCP protocol on port 9091:
- Workers send `HELLO` message on connect
- Controller replies with `ASSIGN` (test config + target count)
- Workers report `METRICS` every 5 seconds
- Controller sends `STOP` to end the run

jMeter Next's approach is more sophisticated:
- Protobuf-typed messages vs raw struct serialization
- Health polling + circuit breaker vs no failure detection
- WebSocket fallback vs TCP-only
- Coordinated start timestamp vs fire-and-forget
- HDR Histogram merge (proposed) vs averaging
