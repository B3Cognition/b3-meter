# Specification: Worker Protocol (gRPC Wire Format)

**Domain**: 001-re-worker-proto
**Created**: 2026-03-29
**Status**: Draft (Reverse-Engineered)
**Dependencies**: None

## Overview

The worker-proto module defines the gRPC wire protocol between the distributed controller and worker nodes. It is the foundational contract that allows the controller to configure, start, stop, and stream results from remote worker nodes over gRPC using Protocol Buffers v3.

**Source Files Analyzed**:
- `modules/worker-proto/src/main/proto/worker.proto`
- `modules/worker-proto/build.gradle.kts`

## Complexity Estimation

| Metric | Value | Implication |
|--------|-------|-------------|
| **Files** | 1 proto + build file | Small; single contract file |
| **Lines of Code** | ~200 | Compact, well-structured |
| **Git Commits (6 mo)** | 1 (in hotspots) | Core protocol established, stable |
| **Contributors** | 1 | Single author |
| **Hotspot Score** | Medium | In git hotspot list |

**Estimated Complexity**: Low
**Rationale**: Single proto file defining 6 RPC methods and ~8 message types. Protocol is well-defined with clear lifecycle.

## User Scenarios & Testing

### US-001.1 — Configure Worker with Test Plan (Priority: P1)

As a distributed controller, I need to send a test plan to a worker node for validation before starting so that the worker can confirm it can execute the plan.

**Source Evidence**:
- File: `worker.proto:23` — `rpc Configure(TestPlanMessage) returns (ConfigureAck)`
- File: `worker.proto:46-67` — `TestPlanMessage` with `run_id`, `plan_content`, `virtual_users`, `duration_seconds`

**Acceptance Scenarios**:
1. **Given** a valid JMX plan bytes and positive `virtual_users`, **When** controller sends `Configure`, **Then** worker returns `ConfigureAck.accepted=true`
2. **Given** an invalid JMX plan, **When** controller sends `Configure`, **Then** worker returns `ConfigureAck.accepted=false` with `message` describing the error
3. **Given** `plan_content` is empty, **When** controller sends `Configure`, **Then** worker rejects with `accepted=false`

### US-001.2 — Coordinated Start Across Workers (Priority: P1)

As a distributed controller, I need to start all workers at a synchronized future timestamp so that load generation begins simultaneously across the cluster.

**Source Evidence**:
- File: `worker.proto:73-93` — `StartMessage` with optional `start_at` Timestamp
- Source: `DistributedRunService.java:135` — `startAt = Instant.now().plusMillis(5000)`

**Acceptance Scenarios**:
1. **Given** a `StartMessage` with `start_at` 5 seconds in the future, **When** sent to all workers, **Then** all workers begin executing at the specified wall-clock time (±100ms tolerance)
2. **Given** `start_at` is absent or in the past, **When** Start RPC is received, **Then** worker starts immediately

### US-001.3 — Graceful Stop (Priority: P1)

As a distributed controller, I need to request a graceful stop that completes in-flight samples so that partial results are not lost.

**Source Evidence**:
- File: `worker.proto:28-30` — `rpc Stop(StopMessage) returns (StopAck)`
- File: `worker.proto:99-112` — `StopMessage` with `run_id`

**Acceptance Scenarios**:
1. **Given** a running test, **When** `Stop` RPC is received, **Then** worker transitions to `WORKER_STATE_STOPPING`, completes in-flight samples, then transitions to `WORKER_STATE_IDLE`
2. **Given** an unknown `run_id`, **When** `Stop` is called, **Then** `StopAck.accepted=false`

### US-001.4 — Immediate Abort (Priority: P1)

As a distributed controller, I need to forcibly abort all VUs without waiting for in-flight requests to complete so that I can immediately stop runaway tests.

**Source Evidence**:
- File: `worker.proto:33` — `rpc StopNow(StopMessage) returns (StopAck)`

**Acceptance Scenarios**:
1. **Given** a running test, **When** `StopNow` is called, **Then** all virtual user threads are interrupted immediately and `StopAck.accepted=true`
2. **Given** worker is already idle, **When** `StopNow` is called, **Then** `StopAck.accepted=false` with appropriate message

### US-001.5 — Stream Aggregated Results (Priority: P1)

As a distributed controller, I need to receive periodic batches of aggregated metrics from each worker so that I can merge them into accurate global percentiles.

**Source Evidence**:
- File: `worker.proto:35` — `rpc StreamResults(StopMessage) returns (stream SampleResultBatch)`
- File: `worker.proto:122-152` — `SampleResultBatch` with timestamp, sampler_label, sample_count, error_count, avg_response_time, percentiles map, hdr_histogram bytes

**Acceptance Scenarios**:
1. **Given** a running test, **When** `StreamResults` is opened with the `run_id`, **Then** worker emits `SampleResultBatch` every reporting interval (default 5s)
2. **Given** `hdr_histogram` is present in a batch, **When** controller processes the batch, **Then** controller uses HdrHistogram merge for accurate cross-worker percentiles rather than the percentiles map
3. **Given** the test completes, **When** the worker finishes, **Then** the stream is closed by the server

### US-001.6 — Worker Health Probe (Priority: P2)

As a distributed controller, I need to poll worker health to detect failures and circuit-break unhealthy workers so that a failed worker does not stall the entire run.

**Source Evidence**:
- File: `worker.proto:38` — `rpc GetHealth(HealthRequest) returns (HealthStatus)`
- File: `worker.proto:165-177` — `HealthStatus` with `state`, `message`, `active_run_id`, `timestamp_ms`
- Source: `WorkerHealthPoller.java` — periodic health polling

**Acceptance Scenarios**:
1. **Given** a healthy idle worker, **When** `GetHealth` is called, **Then** `HealthStatus.state = WORKER_STATE_IDLE`
2. **Given** a worker executing a test, **When** `GetHealth` is called, **Then** `HealthStatus.state = WORKER_STATE_RUNNING` and `active_run_id` is populated
3. **Given** a worker that crashed, **When** `GetHealth` times out, **Then** controller circuit-breaks that worker

## Requirements

### Functional Requirements

**Worker Lifecycle RPC Set**
- **FR-001.001**: Protocol MUST define `Configure`, `Start`, `Stop`, `StopNow`, `StreamResults`, `GetHealth` RPCs as the complete worker lifecycle interface
- **FR-001.002**: `TestPlanMessage` MUST include `run_id` (UUID), `plan_content` (UTF-8 JMX bytes), `virtual_users` (int32), `duration_seconds` (int64 — 0 means run until plan completes)
- **FR-001.003**: `StartMessage` MUST support optional `start_at` Timestamp for coordinated multi-worker launch
- **FR-001.004**: `SampleResultBatch` MUST include both `percentiles` map (backward compat) and `hdr_histogram` bytes (preferred for accurate merge)

**Worker State Machine**
- **FR-001.005**: Worker MUST expose the following states: `UNSPECIFIED`, `IDLE`, `CONFIGURED`, `RUNNING`, `STOPPING`, `ERROR`
- **FR-001.006**: Valid state transitions: `IDLE → CONFIGURED` (after Configure), `CONFIGURED → RUNNING` (after Start), `RUNNING → STOPPING` (after Stop), `STOPPING → IDLE` (after drain), `ANY → ERROR` (on fatal error)

## Key Entities

### TestPlanMessage

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| run_id | string | UUID for this run | Non-empty UUID |
| plan_content | bytes | JMX test plan UTF-8 XML | Non-empty |
| virtual_users | int32 | VUs for this worker | >= 1 |
| duration_seconds | int64 | Max run duration | 0 = until complete |

### SampleResultBatch

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| timestamp | Timestamp | Start of reporting window | Non-null |
| sampler_label | string | JMeter sampler label | Non-empty |
| sample_count | int64 | Samples in this window | >= 0 |
| error_count | int64 | Error samples | >= 0, <= sample_count |
| avg_response_time | double | Mean response time (ms) | >= 0 |
| percentiles | map<string, double> | Legacy percentile map | Keys: p50, p90, p95, p99 |
| hdr_histogram | bytes | Sparse RLE HdrHistogram | May be empty (fallback to percentiles) |

## Edge Cases

- **Empty `hdr_histogram`**: Controller falls back to `percentiles` map — Source: `worker.proto:149-151`
- **Worker rejects Configure**: Controller rolls back all already-configured workers via `StopNow` — Source: `DistributedRunService.java:124`
- **`start_at` in the past**: Worker starts immediately rather than waiting — Source: `worker.proto:80-82`

## Success Criteria

- **SC-001.001**: All 6 RPC methods are fully defined with request/response message types
- **SC-001.002**: `SampleResultBatch` supports HdrHistogram for accurate cross-worker percentile merging
- **SC-001.003**: Worker state machine covers all 6 lifecycle states with valid transitions
- **SC-001.004**: Protocol is backward-compatible (percentiles map retained alongside hdr_histogram)
