# Specification: Engine Service (Core Execution Layer)

**Domain**: 002-re-engine-service
**Created**: 2026-03-29
**Status**: Draft (Reverse-Engineered)
**Dependencies**: 001-re-worker-proto

## Overview

The engine-service module is the pure-Java core of jMeter Next. It defines all execution abstractions — virtual user executors (thread-based and arrival-rate), SLA monitoring, metrics bucketing, test run lifecycle, and the UI bridge contract. This module has zero framework dependencies (no Spring, no JMeter internals) by architectural decree ("Constitution Principle I: Framework-Free").

**Source Files Analyzed**:
- `modules/engine-service/src/main/java/com/b3meter/engine/service/EngineService.java`
- `modules/engine-service/src/main/java/com/b3meter/engine/service/ArrivalRateExecutor.java`
- `modules/engine-service/src/main/java/com/b3meter/engine/service/RampingArrivalRateExecutor.java`
- `modules/engine-service/src/main/java/com/b3meter/engine/service/SlaEvaluator.java`
- `modules/engine-service/src/main/java/com/b3meter/engine/service/TestRunContext.java`
- `modules/engine-service/src/main/java/com/b3meter/engine/service/SampleBucket.java`
- `modules/engine-service/src/main/java/com/b3meter/engine/service/HdrHistogramAccumulator.java`
- `modules/engine-service/src/main/java/com/b3meter/engine/service/interpreter/` (50+ sampler executor classes)

## Complexity Estimation

| Metric | Value | Implication |
|--------|-------|-------------|
| **Files** | ~40 main + ~30 test | Medium-Large |
| **Lines of Code** | ~15,000 est. | Significant core logic |
| **Git Commits (6 mo)** | Included in major uplift commit | Active development |
| **Contributors** | 1 | Specialist knowledge concentrated |
| **Hotspot Score** | High | WorkerServiceImpl is in hotspots |

**Estimated Complexity**: High
**Rationale**: Contains concurrent execution logic (virtual threads, arrival-rate scheduling), 50+ sampler executor implementations, HdrHistogram integration, and SLA monitoring — all with zero framework safety nets.

## User Scenarios & Testing

### US-002.1 — Start a Test Run (Priority: P1)

As a web API layer, I need to start a test run from a saved plan and receive a handle to track it so that I can report run status to the user.

**Source Evidence**:
- File: `EngineService.java:33` — `TestRunHandle startRun(String planId, Map<String, Object> treeData, Properties overrides)`
- File: `EngineService.java:27-29` — Returns CompletableFuture that resolves on completion

**Acceptance Scenarios**:
1. **Given** a valid `planId` and `treeData`, **When** `startRun` is called, **Then** returns a `TestRunHandle` with non-null `runId` and a pending `CompletableFuture`
2. **Given** a run with the same derived `runId` is already active, **When** `startRun` is called again, **Then** throws `IllegalStateException`
3. **Given** `planId` is null or blank, **When** `startRun` is called, **Then** throws `IllegalArgumentException`

### US-002.2 — Constant Arrival-Rate Execution (Priority: P1)

As a load engineer, I need to generate requests at a fixed target rate (iterations/second) independent of VU response time so that I can test server capacity at a known load, not at client-determined throughput.

**Source Evidence**:
- File: `ArrivalRateExecutor.java:54` — `public final class ArrivalRateExecutor implements AutoCloseable`
- File: `ArrivalRateExecutor.java:62-65` — Config: `targetRate`, `maxVUs`, `preAllocatedVUs`, `duration`
- File: `ArrivalRateExecutor.java:49` — "Inspired by k6's constant-arrival-rate executor"

**Acceptance Scenarios**:
1. **Given** `targetRate=100 iter/s`, `maxVUs=20`, `preAllocatedVUs=5`, **When** started, **Then** dispatches exactly 100 iterations per second (±5%)
2. **Given** all VUs are busy and `allocatedVUs < maxVUs`, **When** next tick fires, **Then** pool is expanded by 1 VU (up to `maxVUs`)
3. **Given** all VUs are busy and `allocatedVUs == maxVUs`, **When** next tick fires, **Then** iteration is dropped and `droppedIterations` counter increments
4. **Given** `targetRate <= 0`, **When** constructor is called, **Then** throws `IllegalArgumentException`
5. **Given** `maxVUs < preAllocatedVUs`, **When** constructor is called, **Then** throws `IllegalArgumentException`

### US-002.3 — Ramping Arrival-Rate Execution (Priority: P1)

As a load engineer, I need to ramp arrival rate from a start rate to an end rate over a duration so that I can simulate gradual load increase (load shape testing).

**Source Evidence**:
- File: `RampingArrivalRateExecutor.java` — sister class to `ArrivalRateExecutor`
- File: `ArrivalRateExecutor.java:52` — `@see RampingArrivalRateExecutor`

**Acceptance Scenarios**:
1. **Given** `startRate=10`, `endRate=100`, `duration=60s`, **When** running, **Then** rate linearly interpolates from 10 to 100 iter/s over 60 seconds
2. **Given** rate ramps up, **When** VU pool is exhausted, **Then** pool expands dynamically (same algorithm as constant arrival-rate)

### US-002.4 — Real-Time SLA Monitoring (Priority: P1)

As a load engineer, I need continuous SLA checks against every metrics bucket so that I receive immediate notification when p95/p99/avg/error-rate thresholds are breached.

**Source Evidence**:
- File: `SlaEvaluator.java:20` — `public class SlaEvaluator implements SampleBucketConsumer`
- File: `SlaEvaluator.java:33` — Constructor: `(maxP95Ms, maxP99Ms, maxAvgMs, maxErrorPercent)`
- File: `SlaEvaluator.java:41-72` — `onBucket(SampleBucket)` checks all thresholds

**Acceptance Scenarios**:
1. **Given** `maxP95Ms=500`, **When** a bucket arrives with `p95=600ms`, **Then** `SlaViolation` is recorded with `reason="p95=600ms > 500ms"`
2. **Given** `maxErrorPercent=1.0`, **When** a bucket arrives with `errorPercent=2.5%`, **Then** `SlaViolation` is recorded
3. **Given** `maxP95Ms=0` (disabled), **When** any bucket arrives, **Then** p95 check is skipped (zero = threshold disabled)
4. **Given** multiple thresholds violated in one bucket, **When** `onBucket` is called, **Then** all violations are combined into one `SlaViolation.reason` string

### US-002.5 — Graceful Stop (Priority: P1)

As a test operator, I need to stop a run gracefully so that current VU iterations complete before the engine shuts down.

**Source Evidence**:
- File: `EngineService.java:45` — `void stopRun(String runId)` — graceful (lets iterations finish)
- File: `EngineService.java:57` — `void stopRunNow(String runId)` — immediate (interrupts threads)

**Acceptance Scenarios**:
1. **Given** a running test, **When** `stopRun` is called, **Then** VU threads complete their current iteration before shutting down
2. **Given** a running test, **When** `stopRunNow` is called, **Then** VU threads are interrupted immediately via `Thread.interrupt()`
3. **Given** `runId` is null, **When** either stop is called, **Then** throws `IllegalArgumentException`

### US-002.6 — HDR Histogram Metrics Accumulation (Priority: P1)

As a metrics pipeline, I need to accumulate response time data into an HDR histogram so that accurate percentiles (especially p95, p99) can be computed without coordinated omission bias.

**Source Evidence**:
- File: `HdrHistogramAccumulator.java` (in hotspot list)
- File: `SampleBucket.java` — `percentile95()`, `percentile99()` methods
- File: `worker.proto:147-151` — `hdr_histogram` bytes field in `SampleResultBatch`

**Acceptance Scenarios**:
1. **Given** a series of samples with response times 1ms–5000ms, **When** `percentile99()` is called, **Then** returns the 99th percentile value accurate to the configured precision (±1%)
2. **Given** two HDR histograms from different workers, **When** merged, **Then** the merged percentiles accurately represent the combined distribution

### US-002.7 — Coordinated Omission Detection (Priority: P2)

As an accuracy-conscious engineer, I need the engine to detect and correct for coordinated omission (VU queue backlog masking high latency) so that load test results accurately reflect server performance under saturation.

**Source Evidence**:
- File: `CoordinatedOmissionDetector.java` — dedicated detector class

**Acceptance Scenarios**:
1. **Given** VUs backing up (queue depth > 0), **When** omission is detected, **Then** log warning and adjust reported latency
2. **Given** arrival-rate executor with backpressure, **When** `droppedIterations > 0`, **Then** coordinated omission is flagged

## Requirements

### Functional Requirements

**Test Run Lifecycle**
- **FR-002.001**: `EngineService` MUST be thread-safe — all methods callable from multiple threads concurrently
- **FR-002.002**: `startRun` MUST return a `TestRunHandle` containing run ID and a `CompletableFuture<TestRunResult>` that resolves on completion
- **FR-002.003**: `stopRun` MUST perform graceful drain; `stopRunNow` MUST interrupt immediately
- **FR-002.004**: `getRunStatus` MUST return current `TestRunStatus` or `null` if run not found
- **FR-002.005**: `activeRuns()` MUST return a snapshot — changes after the call are not reflected

**Executor Types**
- **FR-002.006**: `VirtualUserExecutor` MUST spawn N virtual threads (Java 21 Virtual Threads / JEP-444) each looping the iteration task
- **FR-002.007**: `ArrivalRateExecutor` MUST dispatch iterations at `targetRate` ± 5% using a nanosecond-precision ticker
- **FR-002.008**: `RampingArrivalRateExecutor` MUST interpolate rate linearly from `startRate` to `endRate` over `duration`
- **FR-002.009**: All executors MUST use `ReentrantLock` (not `synchronized`) to avoid VT pinning per JEP-491

**SLA Monitoring**
- **FR-002.010**: `SlaEvaluator` MUST subscribe to `SampleStreamBroker` and evaluate every bucket
- **FR-002.011**: A threshold of 0 MUST disable that check (not "must be zero")
- **FR-002.012**: `SlaStatus.verdict` MUST be "PASS" or "VIOLATED" — no intermediate states

**Framework Independence (Constitution Principle I)**
- **FR-002.013**: engine-service module MUST NOT import Spring, JMeter, or any framework classes
- **FR-002.014**: All classes in engine-service MUST use only JDK standard library dependencies

## Key Entities

### TestRunContext

| Attribute | Type | Description | Constraints |
|-----------|------|-------------|-------------|
| runId | String | UUID of the run | Non-null, non-blank |
| status | TestRunStatus | Current lifecycle state | Enum: PENDING, RUNNING, STOPPING, STOPPED, ERROR |
| startedAt | Instant | When run began | Non-null after start |
| planId | String | Source test plan ID | Non-null |

### SampleBucket

| Attribute | Type | Description | Constraints |
|-----------|------|-------------|-------------|
| samplerLabel | String | JMeter sampler label | Non-null |
| sampleCount | long | Samples in window | >= 0 |
| errorCount | long | Error samples | >= 0, <= sampleCount |
| avgResponseTime | double | Mean RT (ms) | >= 0 |
| percentile95 | double | p95 RT (ms) | >= 0 |
| percentile99 | double | p99 RT (ms) | >= 0 |
| errorPercent | double | errorCount/sampleCount * 100 | 0.0–100.0 |

### ExecutorType

Values: `VIRTUAL_USERS`, `CONSTANT_ARRIVAL_RATE`, `RAMPING_ARRIVAL_RATE`

## Edge Cases

- **`droppedIterations` at max capacity**: Logged at WARN level every 1st occurrence and every 100th — Source: `ArrivalRateExecutor.java:306-311`
- **VU pool shutdown grace period**: 30s await then force shutdown — Source: `ArrivalRateExecutor.java:237-243`
- **`synchronized` vs `ReentrantLock`**: Engine must use ReentrantLock to avoid VT pinning — Source: `ArrivalRateExecutor.java:48`

## Success Criteria

- **SC-002.001**: `ArrivalRateExecutor` dispatches at configured rate ± 5% under normal load
- **SC-002.002**: `SlaEvaluator` reports violations within one reporting interval (default 5s)
- **SC-002.003**: engine-service module compiles with zero Spring/JMeter imports
- **SC-002.004**: All executor classes are thread-safe under concurrent start/stop calls
- **SC-002.005**: `HdrHistogramAccumulator` produces accurate p99 within ±1% compared to exact sort
