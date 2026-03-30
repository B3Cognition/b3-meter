# Specification: Distributed Controller

**Domain**: 004-re-distributed-controller
**Created**: 2026-03-29
**Status**: Draft (Reverse-Engineered)
**Dependencies**: 001-re-worker-proto, 002-re-engine-service

## Overview

The distributed-controller module coordinates load generation across multiple remote worker nodes. It handles worker discovery (DNS-based), transport selection (gRPC or WebSocket), coordinated test start with a synchronized 5-second future timestamp, result aggregation using HDR histogram merging for accurate cross-worker percentiles, and circuit-breaking for unhealthy workers.

**Source Files Analyzed**:
- `modules/distributed-controller/src/main/java/com/b3meter/distributed/controller/DistributedRunService.java`
- `modules/distributed-controller/src/main/java/com/b3meter/distributed/controller/ResultAggregator.java`
- `modules/distributed-controller/src/main/java/com/b3meter/distributed/controller/WorkerClient.java`
- `modules/distributed-controller/src/main/java/com/b3meter/distributed/controller/WorkerHealthPoller.java`
- `modules/distributed-controller/src/main/java/com/b3meter/distributed/controller/WorkerTransportSelector.java`
- `modules/distributed-controller/src/main/java/com/b3meter/distributed/controller/DnsWorkerDiscovery.java`
- `modules/distributed-controller/src/main/java/com/b3meter/distributed/controller/GrpcWorkerTransport.java`
- `modules/distributed-controller/src/main/java/com/b3meter/distributed/controller/WebSocketWorkerTransport.java`

## Complexity Estimation

| Metric | Value | Implication |
|--------|-------|-------------|
| **Files** | ~12 main + 5 test | Medium |
| **Lines of Code** | ~4,000 est. | Focused module |
| **Git Commits** | In hotspots (distributed spec) | Active development |
| **Contributors** | 1 | Specialist knowledge |
| **Hotspot Score** | High | Distributed mode spec in top hotspots |

**Estimated Complexity**: High
**Rationale**: Concurrent multi-worker coordination with failure handling, circuit-breaking, HDR histogram merging, and transport abstraction layer.

## User Scenarios & Testing

### US-004.1 — Start Distributed Run Across Multiple Workers (Priority: P1)

As a load engineer, I need to distribute virtual users across N worker nodes and have all workers start simultaneously so that aggregate throughput scales linearly with worker count.

**Source Evidence**:
- File: `DistributedRunService.java:87` — `startDistributed(planId, workerIds, planContent, totalVirtualUsers, durationSeconds)`
- File: `DistributedRunService.java:104-106` — VU distribution: `baseVUs = total/count`, `remainder` assigned to first worker
- File: `DistributedRunService.java:135` — Coordinated start: `Instant.now().plusMillis(5000)`

**Acceptance Scenarios**:
1. **Given** 100 total VUs and 3 workers, **When** `startDistributed` is called, **Then** workers receive 34, 33, 33 VUs respectively (remainder to first)
2. **Given** all workers configured successfully, **When** Start RPCs are sent, **Then** all workers use the same `start_at` timestamp (5 seconds in the future)
3. **Given** a worker rejects Configure, **When** rollback occurs, **Then** all previously-configured workers receive `StopNow` before exception is thrown
4. **Given** `workerIds` is empty, **When** `startDistributed` is called, **Then** throws `IllegalArgumentException`

### US-004.2 — Aggregate Results Across Workers (Priority: P1)

As a metrics consumer, I need results from all workers merged into a single timeline with accurate cross-worker percentiles so that I can report the true p99 of the entire system.

**Source Evidence**:
- File: `ResultAggregator.java` — merges worker result streams
- File: `worker.proto:147-151` — `hdr_histogram` bytes enable accurate merge

**Acceptance Scenarios**:
1. **Given** 3 workers each streaming `SampleResultBatch` for the same sampler label, **When** aggregated, **Then** the merged `sample_count` equals the sum of all worker counts
2. **Given** HDR histograms are present in batches, **When** merging, **Then** the merged p99 uses histogram merge (not arithmetic average of p99 values)
3. **Given** one worker has higher latency than others, **When** aggregated, **Then** the p99 reflects the worst-case worker's contribution to the distribution

### US-004.3 — Worker Health Polling and Circuit Breaking (Priority: P1)

As a distributed system operator, I need unhealthy workers to be automatically circuit-broken so that a failed worker does not delay stop operations or block result collection.

**Source Evidence**:
- File: `WorkerHealthPoller.java` — `POLL_INTERVAL_MS=5000`, `MAX_MISSED_HEARTBEATS=3`
- File: `WorkerClient.java:186` — `isCircuitOpen()` check
- Test: `WorkerHealthPollerTest.java`

**Acceptance Scenarios**:
1. **Given** a worker fails to respond to 3 consecutive health checks, **When** the circuit breaker opens, **Then** subsequent RPCs to that worker are skipped
2. **Given** a circuit-broken worker, **When** `stopDistributed` is called, **Then** the failing worker is skipped with a WARN log
3. **Given** a circuit-broken worker recovers (health probe succeeds), **When** circuit is tested, **Then** the circuit transitions to half-open and eventually closed

### US-004.4 — DNS-Based Worker Discovery (Priority: P2)

As a Kubernetes operator, I need workers to be automatically discovered via DNS so that scaling the worker deployment automatically adds capacity to the pool.

**Source Evidence**:
- File: `DnsWorkerDiscovery.java` — DNS-based service discovery
- File: `DistributionMode.java` — enum for discovery modes

**Acceptance Scenarios**:
1. **Given** a Kubernetes headless service `jmeter-worker`, **When** DNS is queried, **Then** all worker pod IPs are discovered and registered
2. **Given** new workers are added to DNS while a test is running, **When** the next discovery cycle runs (interval is caller-configured via `startPeriodicRefresh(Duration, Consumer)`), **Then** new workers become available for subsequent runs
3. **Given** a worker is removed from DNS, **When** discovery runs, **Then** the worker is deregistered from the pool

### US-004.5 — Transport Protocol Selection (Priority: P2)

As an infrastructure operator, I need to select between gRPC and WebSocket transport for worker communication so that the system can operate in environments where gRPC is blocked by proxies.

**Source Evidence**:
- File: `WorkerTransportSelector.java` — selects transport implementation
- File: `GrpcWorkerTransport.java` — gRPC transport
- File: `WebSocketWorkerTransport.java` — WebSocket fallback transport
- File: `WorkerTransport.java` — common interface
- Test: `WorkerTransportSelectorTest.java`

**Acceptance Scenarios**:
1. **Given** no firewall restrictions, **When** transport is selected, **Then** gRPC transport is used (lower overhead)
2. **Given** gRPC port is blocked, **When** configured to use WebSocket, **Then** WebSocket transport is used with equivalent functionality
3. **Given** gRPC transport fails on initial connection, **When** fallback is enabled, **Then** automatically retries with WebSocket transport

### US-004.6 — Graceful Distributed Stop (Priority: P1)

As a test operator, I need to gracefully stop all workers so that in-flight samples complete and results are not dropped at the moment of stop.

**Source Evidence**:
- File: `DistributedRunService.java:180-198` — `stopDistributed`
- File: `DistributedRunService.java:209-227` — `stopDistributedNow`

**Acceptance Scenarios**:
1. **Given** all workers are healthy, **When** `stopDistributed` is called, **Then** `Stop` RPC is sent to each worker in sequence
2. **Given** one worker circuit is open, **When** `stopDistributed` is called, **Then** the open-circuit worker is skipped with WARN log, others are stopped normally
3. **Given** a run is forcefully aborted, **When** `stopDistributedNow` is called, **Then** `StopNow` RPC is sent to all workers regardless of circuit state

## Requirements

### Functional Requirements

**Run Coordination**
- **FR-004.001**: `startDistributed` MUST coordinate the start_at timestamp so all workers begin load simultaneously (within 100ms clock skew tolerance)
- **FR-004.002**: VU distribution MUST use integer division with remainder assigned to the first worker
- **FR-004.003**: If any worker rejects Configure, ALL previously configured workers MUST be rolled back via `StopNow` before throwing
- **FR-004.004**: `COORDINATED_START_DELAY_MS` MUST be 5000ms (configurable in future but hardcoded now)

**Result Aggregation**
- **FR-004.005**: Aggregator MUST merge HDR histograms when present (not average p-values)
- **FR-004.006**: Aggregator MUST handle worker disconnections gracefully (partial results published on disconnect)

**Health and Circuit Breaking**
- **FR-004.007**: `WorkerHealthPoller` MUST poll each worker at 5000ms intervals (default: `POLL_INTERVAL_MS = 5000` — configurable via constructor)
- **FR-004.008**: Circuit breaker MUST open after 3 consecutive health failures (default: `MAX_MISSED_HEARTBEATS = 3` — 15s total before declaring unavailable)
- **FR-004.009**: Open circuits MUST be skipped silently during `stopDistributed` (log WARN, not throw)

**Discovery**
- **FR-004.010**: `DnsWorkerDiscovery` MUST resolve SRV or A records for worker hostnames
- **FR-004.011**: Discovery MUST be re-run periodically to pick up newly registered workers

**Transport**
- **FR-004.012**: Both `GrpcWorkerTransport` and `WebSocketWorkerTransport` MUST implement `WorkerTransport`
- **FR-004.013**: `WorkerTransportSelector` MUST select transport based on configuration, not capability probing

## Key Entities

### WorkerClient

| Attribute | Type | Description |
|-----------|------|-------------|
| workerId | String | Unique worker identifier |
| transport | WorkerTransport | gRPC or WebSocket transport |
| circuitOpen | boolean | Whether this worker is circuit-broken |

**Methods**: `configure()`, `start()`, `stop()`, `stopNow()`, `streamResults()`, `getHealth()`, `isCircuitOpen()`

### DistributionMode

Enum values: `LOCAL` (single node), `DISTRIBUTED` (multi-worker)

## Edge Cases

- **Empty workerIds**: `IllegalArgumentException` thrown immediately — Source: `DistributedRunService.java:94`
- **Worker not in registry**: `IllegalArgumentException` — Source: `DistributedRunService.java:259`
- **Circuit-open worker during stop**: Skipped with WARN — Source: `DistributedRunService.java:186`
- **Result stream closed by server**: Stream closure triggers cleanup in aggregator

## Success Criteria

- **SC-004.001**: All workers start within 100ms of the `start_at` timestamp
- **SC-004.002**: `ResultAggregatorTest` passes — merged p99 is within 1% of actual p99 from merged histogram
- **SC-004.003**: Circuit breaker opens after configured failure threshold
- **SC-004.004**: `DistributedRunServiceTest` passes for all rollback scenarios
- **SC-004.005**: `WorkerTransportSelectorTest` passes for both gRPC and WebSocket paths
