# Specification: Circuit Breaker Recovery — WorkerRegistry Wiring

**Domain**: 010-quality-circuit-breaker
**Created**: 2026-03-29
**Status**: Draft
**Dependencies**: 004-re-distributed-controller

---

## Overview

`WorkerHealthPoller` correctly detects worker unavailability and recovery, emitting `UNAVAILABLE`/`AVAILABLE` events to registered listeners. However, no listener is wired between `WorkerHealthPoller` and `DistributedRunService` — the two components that together form the circuit breaker. When a worker becomes unavailable, `DistributedRunService` continues routing RPCs to its stale `WorkerClient`, causing cascading failures. This spec defines: (1) a `WorkerRegistry` class that couples availability state with the `WorkerClient` map, (2) wiring in `DistributedControllerApplication` to connect `WorkerHealthPoller` events to `WorkerRegistry`, and (3) documentation of the deliberate no-half-open-state policy.

---

## Problem Statement

### Current State

**`WorkerHealthPoller`** (`WorkerHealthPoller.java`):
- Tracks `missCounters: ConcurrentHashMap<String,AtomicInteger>` and `lastAvailability: ConcurrentHashMap<String,WorkerAvailability>`.
- Emits `UNAVAILABLE` after `MAX_MISSED_HEARTBEATS = 3` consecutive missed polls.
- Emits `AVAILABLE` immediately on first successful heartbeat after being unavailable (`recordHeartbeat()` at line 238 calls `transitionIfChanged(workerId, AVAILABLE)`).
- Exposes `addListener(BiConsumer<String, WorkerAvailability>)` at line 175.

**`DistributedRunService`** (`DistributedRunService.java`):
- Holds `private final Map<String, WorkerClient> workerRegistry` (line 50) — a plain `ConcurrentHashMap` injected at construction.
- Uses `workerRegistry` to look up `WorkerClient` instances for distribute-run calls.
- Has no connection to `WorkerHealthPoller`.

**`DistributedControllerApplication`** (`DistributedControllerApplication.java`):
- Is a Spring Boot entry point with zero bean wiring. Lines 1–19: just `@SpringBootApplication` + `main()`.
- No `@Bean` methods, no configuration, no `@Autowired` wiring of `WorkerHealthPoller` to `DistributedRunService`.

**`WorkerClient`** (`WorkerClient.java`):
- Has an internal circuit breaker (`consecutiveFailures`, `circuitOpen` at lines 54–57) that tracks RPC failures.
- `isCircuitOpen()` returns `true` after 3 consecutive RPC failures (line 263).
- `resetCircuit()` resets the breaker (line 271).
- The `WorkerHealthPoller` circuit and the `WorkerClient` circuit are parallel, unconnected mechanisms.

### What Is Wrong

1. **Missing listener registration**: `WorkerHealthPoller` emits UNAVAILABLE/AVAILABLE events but nobody listens. When a worker dies, `DistributedRunService` continues sending RPCs to a dead `WorkerClient`. The `WorkerClient` internal circuit breaker will eventually trip, but it does so per-RPC rather than per health poll.
   - Evidence: `WorkerHealthPoller.java` line 175 — `addListener()` exists but is never called from `DistributedControllerApplication.java`.
   - Evidence: `DistributedControllerApplication.java` lines 1–19 — zero bean wiring.

2. **No shared registry**: `WorkerHealthPoller.workers` (line 67) and `DistributedRunService.workerRegistry` (line 50) are separate maps. Both track workers, but neither updates the other when availability changes.

3. **No reconnect path**: When a worker recovers (UNAVAILABLE→AVAILABLE), `WorkerHealthPoller` emits AVAILABLE but there is no code to create a new `WorkerClient` with a fresh gRPC channel. The old `WorkerClient` may have its internal circuit tripped and channel in bad state.

4. **No half-open state (policy, not a bug)**: Recovery is immediate on first successful heartbeat. This is a deliberate policy choice (simpler, adequate for the load testing use case) but is undocumented.

### Impact

- A dead worker continues receiving RPCs until `WorkerClient.CIRCUIT_TRIP_THRESHOLD = 3` RPC failures accumulate per run (not per health poll cycle). During this time, runs may fail or block.
- A recovered worker is never reintegrated into active routing — once removed, it stays removed until the process restarts.
- Source evidence: `DistributedControllerApplication.java` lines 1–19; `WorkerHealthPoller.java` line 175; `DistributedRunService.java` line 50.

---

## User Stories

### US-010.1 — Dead Worker Removed from Active Routing (P1)

As a distributed test operator, I need a worker that fails 3 consecutive health polls to be automatically removed from active routing so that new distributed runs do not attempt to use a dead worker.

**Source evidence**: `WorkerHealthPoller.java` lines 241–248 — `recordMiss()` fires `transitionIfChanged(workerId, UNAVAILABLE)` at threshold. `DistributedRunService.java` line 50 — `workerRegistry` is unaffected.

**Acceptance Scenarios**:
- Given worker W1 is registered and health polling is active, when W1 misses 3 consecutive polls, then W1 is removed from the routing registry within 1 poll interval of the threshold being reached.
- Given W1 is removed from routing, when a new distributed run starts requesting W1 by ID, then `DistributedRunService.startDistributed()` throws or skips W1 rather than sending RPCs to a dead client.
- Given W1 is removed from routing, then `WorkerHealthPoller` continues polling W1 to detect recovery.

### US-010.2 — Recovered Worker Reintegrated into Routing (P1)

As a distributed test operator, I need a worker that recovers from failure to be automatically reconnected and added back to active routing so that the controller can use all healthy workers.

**Source evidence**: `WorkerHealthPoller.java` line 238 — `transitionIfChanged(workerId, AVAILABLE)` fires on recovery. No reconnect path exists.

**Acceptance Scenarios**:
- Given worker W1 was marked UNAVAILABLE, when W1 responds to a health poll with a non-UNSPECIFIED state, then W1 is re-added to the routing registry with a new `WorkerClient`.
- Given W1 is reintegrated, when a new distributed run starts, then W1 is included in run distribution.
- Given W1 has a tripped internal circuit breaker, when W1 is reintegrated, then a new `WorkerClient` (fresh channel) is created — the old tripped client is discarded.

### US-010.3 — Thread-Safe Registry Operations (P1)

As a developer, I need the registry and event handling to be thread-safe per the codebase's virtual-threads concurrency model so that concurrent poller callbacks and run-start calls do not produce race conditions.

**Source evidence**: `specs/000-re-overview/constitution.md` §Principle II — "Virtual threads — use `ReentrantLock` (NOT `synchronized`) in new concurrent code."

**Acceptance Scenarios**:
- Given `WorkerRegistry` is accessed concurrently from the poller thread and the run-start thread, then no `ConcurrentModificationException` is thrown.
- Given a worker is transitioning UNAVAILABLE→AVAILABLE while a run is starting, then the run either sees the old unavailable state or the new available state — never a partially-updated state.

### US-010.4 — No Half-Open State Policy Documented (P2)

As a contributor reading the distributed controller code, I need the recovery policy (immediate recovery on first successful heartbeat, no half-open intermediate state) to be documented in Javadoc and in this spec so that I do not implement half-open state thinking it is missing.

**Source evidence**: `WorkerHealthPoller.java` lines 234–239 — `recordHeartbeat()` immediately transitions to AVAILABLE on the first success. No half-open guard exists. No Javadoc explains this is intentional.

**Acceptance Scenarios**:
- Given `WorkerHealthPoller.java`, when a reviewer reads `recordHeartbeat()`, then the Javadoc clearly states that recovery is immediate (no half-open state) and references this spec as the policy decision.
- Given this spec, when a contributor reads §Recovery Policy, then they understand exactly why half-open state is not implemented.

### US-010.5 — Reconnect Failure Handled Gracefully (P2)

As a distributed test operator, I need the reconnect attempt (when AVAILABLE fires) to handle `TransportUnavailableException` gracefully so that a false-positive health poll does not crash the controller.

**Source evidence**: `WorkerTransportSelector.java` lines 95–99 — `TransportUnavailableException` is thrown when neither transport is reachable. This can happen if the health poll succeeds (clearing the miss counter) but the full transport selection fails.

**Acceptance Scenarios**:
- Given W1 emits AVAILABLE but `WorkerTransportSelector.select()` throws `TransportUnavailableException`, then the reconnect attempt logs a warning and leaves W1 out of routing rather than crashing the controller.
- Given the reconnect fails, then subsequent polls continue (the poller is not stopped).

---

## Functional Requirements

### FR-010.001 — WorkerRegistry Class

A new class `WorkerRegistry` must be created in `modules/distributed-controller/src/main/java/com/b3meter/distributed/controller/WorkerRegistry.java` with the following contract:

```java
public final class WorkerRegistry {
    // Register a worker (both for polling and for routing)
    public void register(WorkerClient client, String host, int grpcPort, int wsPort);
    // Deregister a worker entirely
    public void deregister(String workerId);
    // Mark a worker unavailable (removes from routing, keeps in polling)
    public void markUnavailable(String workerId);
    // Replace the WorkerClient for a recovered worker (adds back to routing)
    public void markAvailable(String workerId, WorkerClient newClient);
    // Returns a snapshot of available WorkerClient instances for routing
    public Map<String, WorkerClient> availableWorkers();
    // Returns whether a worker is currently available for routing
    public boolean isAvailable(String workerId);
}
```

`WorkerRegistry` must use `ReentrantLock` (not `synchronized`) for all state mutations that modify both the available-routing map and the availability flag atomically.

**Source evidence**: `constitution.md` §Principle II; `DistributedRunService.java` line 50.

### FR-010.002 — WorkerHealthPoller Listener Registration

`WorkerHealthPoller.addListener()` must be called during application startup with a listener that:
- On `UNAVAILABLE`: calls `WorkerRegistry.markUnavailable(workerId)`
- On `AVAILABLE`: calls `WorkerTransportSelector.select()` to create a new `WorkerClient`, then calls `WorkerRegistry.markAvailable(workerId, newClient)`. If `TransportUnavailableException` is thrown, logs at WARNING level and leaves the worker unavailable.

**Source evidence**: `WorkerHealthPoller.java` line 175 — `addListener()` is never called.

### FR-010.003 — DistributedRunService Uses WorkerRegistry

`DistributedRunService` must accept `WorkerRegistry` (instead of or in addition to `Map<String, WorkerClient>`) as its worker source. When resolving workers for a run, it must consult `WorkerRegistry.availableWorkers()` to get only currently-available clients.

Alternative: `WorkerRegistry.availableWorkers()` returns an unmodifiable view of a `ConcurrentHashMap`, which `DistributedRunService` can accept as `Map<String, WorkerClient>` (no constructor change needed if `availableWorkers()` returns the live map). The preferred approach is to keep `DistributedRunService` accepting `Map<String, WorkerClient>` and pass `WorkerRegistry.availableWorkers()` at construction — this avoids changing the `DistributedRunService` API.

**Source evidence**: `DistributedRunService.java` line 62 — constructor accepts `Map<String, WorkerClient>`.

### FR-010.004 — Spring Configuration Class

A Spring `@Configuration` class must be created to wire `WorkerHealthPoller`, `WorkerRegistry`, `WorkerTransportSelector`, and `DistributedRunService` as Spring beans. `DistributedControllerApplication` must remain a pure entry point.

**Source evidence**: `DistributedControllerApplication.java` lines 1–19 — currently has zero bean definitions.

### FR-010.005 — Recovery Policy: No Half-Open State

The recovery policy is: **the first successful health poll after UNAVAILABLE transitions the worker immediately to AVAILABLE**. This is the documented and intended behaviour. No half-open state is implemented. The policy must be documented in `WorkerHealthPoller.recordHeartbeat()` Javadoc.

**Rationale**: For load testing scenarios, a worker that responds successfully to a health probe is ready to receive work. The health probe IS the probe — there is no need for a separate warm-up window. If the worker fails again, the miss counter mechanism re-trips it within 15 seconds (3 polls × 5s).

### FR-010.006 — WorkerHealthPoller Continues Polling Unavailable Workers

After a worker is marked UNAVAILABLE, `WorkerHealthPoller` must continue polling it (to detect recovery). `WorkerRegistry.markUnavailable()` removes the worker from the routing map but must not call `WorkerHealthPoller.removeWorker()`.

**Source evidence**: `WorkerHealthPoller.java` lines 164–168 — `removeWorker()` removes from polling set. Unavailability must not trigger this.

### FR-010.007 — Connection Metadata Stored in WorkerRegistry

`WorkerRegistry` must store the `host`, `grpcPort`, and `wsPort` for each registered worker so that reconnect attempts (FR-010.002) can call `WorkerTransportSelector.select(workerId, host, grpcPort, wsPort)` without requiring the caller to re-supply connection parameters.

---

## Success Criteria

### SC-010.001 — UNAVAILABLE Removes from Routing

Test: register a worker, send 3 mock health failures, assert `WorkerRegistry.isAvailable(workerId)` returns `false` and `WorkerRegistry.availableWorkers()` does not contain the worker.

### SC-010.002 — AVAILABLE Re-Adds to Routing

Test: start from UNAVAILABLE state, emit a successful health poll, assert `WorkerRegistry.isAvailable(workerId)` returns `true` and `WorkerRegistry.availableWorkers()` contains the worker with a new `WorkerClient`.

### SC-010.003 — DistributedRunService Skips Unavailable Workers

Test: start a distributed run with 2 workers, mark one UNAVAILABLE, assert the run proceeds on the 1 available worker and does not attempt RPC to the unavailable worker.

### SC-010.004 — Reconnect Failure Does Not Crash Controller

Test: emit AVAILABLE for a worker but configure `WorkerTransportSelector` to throw `TransportUnavailableException`. Assert: no exception propagates out of the listener; the worker remains marked unavailable; polling continues.

### SC-010.005 — Thread Safety Under Concurrent Access

Test: simulate concurrent calls to `markUnavailable()` and `availableWorkers()` from 10 threads; assert no `ConcurrentModificationException` and final state is consistent.

### SC-010.006 — ReentrantLock Used (Not synchronized)

Static analysis / code review: `WorkerRegistry.java` contains `ReentrantLock` and zero `synchronized` keywords.

---

## Non-Functional Requirements

### NFR-010.001 — Virtual Threads Compliance

All new concurrent code in `WorkerRegistry` must use `ReentrantLock` from `java.util.concurrent.locks`. No `synchronized` blocks or methods.

**Source evidence**: `constitution.md` §Principle II.

### NFR-010.002 — Listener Execution Time

The `UNAVAILABLE` listener callback (`markUnavailable`) must complete in under 5ms (it only removes from a map — no I/O). The `AVAILABLE` listener callback invokes `WorkerTransportSelector.select()` which may block for up to `GRPC_PROBE_TIMEOUT_SECONDS = 5` seconds — this is acceptable since it runs on the poller thread, not the request thread.

### NFR-010.003 — No New Infrastructure Required

`WorkerRegistry` and the configuration class must work with the existing in-memory state. No database, no message broker.

**Source evidence**: `constitution.md` §Principle V.

### NFR-010.004 — Logging

All state transitions logged in `WorkerRegistry` must use `java.util.logging.Logger` (consistent with the rest of the distributed-controller module). No SLF4J or Logback imports.

---

## Recovery Policy: No Half-Open State

This section documents the deliberate policy decision.

**Decision**: Recovery from UNAVAILABLE to AVAILABLE is immediate on the first successful health poll. There is no intermediate half-open state where a limited number of test requests are allowed through before full recovery.

**Rationale**:
1. The health probe (`GetHealth` gRPC) is functionally equivalent to a canary request. A worker that responds to it is ready to accept work.
2. Load tests are time-bounded. The 15-second unavailability detection window (3 polls × 5s) plus immediate recovery minimises disruption to active runs.
3. If recovery is a false positive, the miss counter re-trips the worker within 3 additional polls (15 seconds). The cost of a false recovery is bounded.
4. Half-open state introduces a `currentProbeCount` counter that must be atomically managed with the `WorkerAvailability` state. For a system with virtual threads and no shared-memory synchronisation on the hot path, this added complexity is not justified.

**Future consideration**: If requirements change to require a minimum-N-successful-probes policy before full recovery, add a `recoveryConfirmationCount` field to `WorkerHealthPoller` and document this spec as superseded.

---

## Out of Scope

- Adding half-open state to `WorkerHealthPoller`.
- Changing the 5-second poll interval or `MAX_MISSED_HEARTBEATS = 3` threshold.
- Dynamic worker discovery (DNS-based or Kubernetes service-discovery) — `DnsWorkerDiscovery.java` is a separate concern.
- Metrics / Prometheus exposure of worker availability state.
- Persistent worker registry (database-backed).
- Changing the `WorkerClient` internal circuit breaker (`CIRCUIT_TRIP_THRESHOLD`).
- Changing the gRPC deadline (`DEADLINE_SECONDS = 30`).
- Any changes to `engine-service`, `web-api`, or frontend modules.
