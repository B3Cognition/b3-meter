# Implementation Plan: Circuit Breaker Recovery — WorkerRegistry Wiring

**Domain**: 010-quality-circuit-breaker
**Created**: 2026-03-29
**Status**: Draft

---

## Approach

The implementation has three phases:

**Phase 1 — WorkerRegistry**: Build the shared registry class that replaces the raw `Map<String, WorkerClient>` in `DistributedRunService`. This is a pure Java class with no Spring dependency — independently testable.

**Phase 2 — DistributedController Configuration**: Add a Spring `@Configuration` class that creates and wires all beans: `WorkerHealthPoller`, `WorkerRegistry`, `WorkerTransportSelector`, `ResultAggregator`, and `DistributedRunService`. Register the health poller listener that calls `WorkerRegistry.markUnavailable()` / `markAvailable()`.

**Phase 3 — Documentation**: Add Javadoc to `WorkerHealthPoller.recordHeartbeat()` documenting the no-half-open-state policy. Update `WorkerHealthPoller` class-level Javadoc to reference `WorkerRegistry`.

The key constraint from the constitution (Principle II) is that `WorkerRegistry` uses `ReentrantLock`, not `synchronized`.

The `DistributedRunService` constructor currently accepts `Map<String, WorkerClient>`. The plan keeps this constructor unchanged and passes `WorkerRegistry.availableWorkers()` — which returns a live unmodifiable view of the available workers map — at construction time. This means `DistributedRunService` always sees the current available-worker state without any code changes to it.

---

## File Changes

### Files to Create

#### `modules/distributed-controller/src/main/java/com/b3meter/distributed/controller/WorkerRegistry.java`

**Purpose**: Thread-safe registry coupling worker availability state with `WorkerClient` instances and connection metadata for reconnect.

**Key implementation details**:

```java
public final class WorkerRegistry {

    private static final Logger LOG = Logger.getLogger(WorkerRegistry.class.getName());

    /**
     * Immutable per-worker connection metadata retained for reconnect.
     */
    private record WorkerEndpoint(String host, int grpcPort, int wsPort) {}

    private final ReentrantLock lock = new ReentrantLock();

    /** Workers currently available for routing. Modified under lock. */
    private final Map<String, WorkerClient> available = new ConcurrentHashMap<>();

    /** Connection metadata for all registered workers (available or not). */
    private final Map<String, WorkerEndpoint> endpoints = new ConcurrentHashMap<>();

    public void register(WorkerClient client, String host, int grpcPort, int wsPort) {
        lock.lock();
        try {
            String id = client.getWorkerId();
            endpoints.put(id, new WorkerEndpoint(host, grpcPort, wsPort));
            available.put(id, client);
            LOG.log(Level.INFO, "Registered worker={0} ({1}:{2})", new Object[]{id, host, grpcPort});
        } finally {
            lock.unlock();
        }
    }

    public void deregister(String workerId) {
        lock.lock();
        try {
            available.remove(workerId);
            endpoints.remove(workerId);
            LOG.log(Level.INFO, "Deregistered worker={0}", workerId);
        } finally {
            lock.unlock();
        }
    }

    public void markUnavailable(String workerId) {
        lock.lock();
        try {
            WorkerClient removed = available.remove(workerId);
            if (removed != null) {
                LOG.log(Level.WARNING, "Worker={0} marked unavailable; removed from routing", workerId);
            }
        } finally {
            lock.unlock();
        }
    }

    public void markAvailable(String workerId, WorkerClient newClient) {
        lock.lock();
        try {
            if (endpoints.containsKey(workerId)) {
                available.put(workerId, newClient);
                LOG.log(Level.INFO, "Worker={0} recovered; re-added to routing", workerId);
            } else {
                LOG.log(Level.WARNING,
                    "markAvailable called for unknown worker={0}; ignoring", workerId);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns an unmodifiable snapshot of available workers for routing.
     * The returned map reflects the state at the time of the call.
     */
    public Map<String, WorkerClient> availableWorkers() {
        lock.lock();
        try {
            return Collections.unmodifiableMap(new HashMap<>(available));
        } finally {
            lock.unlock();
        }
    }

    public boolean isAvailable(String workerId) {
        return available.containsKey(workerId);
    }

    public WorkerEndpoint endpointFor(String workerId) {
        return endpoints.get(workerId);
    }
}
```

**Note on DistributedRunService compatibility**: `availableWorkers()` returns a snapshot. For the `DistributedRunService` constructor to always see the current state, the constructor should accept a `Supplier<Map<String,WorkerClient>>` rather than a pre-snapshotted map — OR `WorkerRegistry` should be passed directly and `DistributedRunService` calls `registry.availableWorkers()` at run-start time. The plan uses the `Supplier` approach to avoid changing the `DistributedRunService` signature: pass `registry::availableWorkers` and call it at the start of `startDistributed()`.

**Alternative if Supplier is too invasive**: Keep `DistributedRunService` constructor as `Map<String, WorkerClient>` and pass a `ConcurrentHashMap` that `WorkerRegistry` updates in-place (instead of returning snapshots). This is simpler but means the `available` map in `WorkerRegistry` is a `ConcurrentHashMap` shared directly with `DistributedRunService`.

**Decision**: Use the shared-map approach. `WorkerRegistry.available` is a `ConcurrentHashMap`. `availableWorkers()` returns an unmodifiable view of the live map (not a snapshot). Pass this view to `DistributedRunService`. This way reads in `DistributedRunService` always see current state with no constructor change.

```java
public Map<String, WorkerClient> availableWorkers() {
    return Collections.unmodifiableMap(available);
}
```

Mutations (markUnavailable, markAvailable, register, deregister) are protected by `ReentrantLock` to ensure atomicity of `available` + `endpoints` updates together.

#### `modules/distributed-controller/src/main/java/com/b3meter/distributed/controller/DistributedControllerConfiguration.java`

**Purpose**: Spring `@Configuration` class that declares all distributed-controller beans and registers the health poller listener.

```java
@Configuration
public class DistributedControllerConfiguration {

    @Bean
    public WorkerRegistry workerRegistry() {
        return new WorkerRegistry();
    }

    @Bean
    public WorkerTransportSelector workerTransportSelector() {
        return new WorkerTransportSelector();
    }

    @Bean
    public WorkerHealthPoller workerHealthPoller(
            WorkerRegistry registry,
            WorkerTransportSelector selector) {
        WorkerHealthPoller poller = new WorkerHealthPoller();
        poller.addListener((workerId, availability) -> {
            if (availability == WorkerHealthPoller.WorkerAvailability.UNAVAILABLE) {
                registry.markUnavailable(workerId);
            } else if (availability == WorkerHealthPoller.WorkerAvailability.AVAILABLE) {
                WorkerRegistry.WorkerEndpoint endpoint = registry.endpointFor(workerId);
                if (endpoint == null) {
                    LOG.log(Level.WARNING,
                        "AVAILABLE event for unregistered worker={0}; ignoring", workerId);
                    return;
                }
                try {
                    WorkerTransport transport = selector.select(
                        workerId, endpoint.host(), endpoint.grpcPort(), endpoint.wsPort());
                    // GrpcWorkerTransport wraps a WorkerClient — extract it
                    // If transport is GrpcWorkerTransport, get its client
                    // For WebSocketWorkerTransport, create a WorkerClient differently
                    // See detailed note in implementation
                    WorkerClient newClient = clientFromTransport(workerId, transport, endpoint);
                    registry.markAvailable(workerId, newClient);
                } catch (WorkerTransportSelector.TransportUnavailableException ex) {
                    LOG.log(Level.WARNING,
                        "Reconnect failed for worker={0}: {1}",
                        new Object[]{workerId, ex.getMessage()});
                    // Worker stays unavailable; poller continues
                }
            }
        });
        poller.start();
        return poller;
    }

    @Bean
    public ResultAggregator resultAggregator() {
        return new ResultAggregator();
    }

    @Bean
    public DistributedRunService distributedRunService(
            WorkerRegistry registry,
            ResultAggregator aggregator) {
        return new DistributedRunService(registry.availableWorkers(), aggregator);
    }
}
```

**Note on `clientFromTransport()`**: `WorkerTransportSelector.select()` returns a `WorkerTransport` (interface). For reconnect purposes, we need a `WorkerClient`. `GrpcWorkerTransport` wraps a `WorkerClient` (see `WorkerTransportSelector.java` lines 81–82: `new WorkerClient(workerId, host, grpcPort)` → `new GrpcWorkerTransport(client)`). The simplest approach is to create a new `WorkerClient` directly using the endpoint metadata rather than going through `WorkerTransportSelector`. Add a package-private `createClient()` helper in the configuration, or expose the `WorkerClient` from `GrpcWorkerTransport`.

**Simpler alternative**: Since `WorkerHealthPoller` already proved the worker is responding to gRPC health probes (that's how it detected AVAILABLE), skip `WorkerTransportSelector` entirely and create `new WorkerClient(workerId, endpoint.host(), endpoint.grpcPort())` directly on the AVAILABLE event. If the gRPC channel creation fails, catch the exception.

**Decision**: Create `WorkerClient` directly on AVAILABLE. The health poller already confirmed gRPC is responding. `WorkerTransportSelector` is for initial connection when we don't know which transport to use.

#### `modules/distributed-controller/src/test/java/com/b3meter/distributed/controller/WorkerRegistryTest.java`

JUnit 5 tests for `WorkerRegistry`:
- `register_then_isAvailable_returnsTrue()`
- `markUnavailable_removesFromRouting_keepingEndpoint()`
- `markAvailable_reAddsToRouting()`
- `deregister_removesCompletelyFromAllMaps()`
- `availableWorkers_reflectsCurrentState()`
- `markUnavailable_unknownWorker_isNoOp()`
- `concurrent_markUnavailable_and_availableWorkers_noException()` — 10-thread stress test

#### `modules/distributed-controller/src/test/java/com/b3meter/distributed/controller/WorkerHealthPollerRegistryIntegrationTest.java`

Integration test verifying the poller → registry listener wiring:
- `pollerUnavailableEvent_removesWorkerFromRegistry()`
- `pollerAvailableEvent_reAddsWorkerToRegistry()`
- `pollerAvailableEvent_reconnectFails_workerRemainsUnavailable()` — mock transport selector throws

---

### Files to Modify

#### `modules/distributed-controller/src/main/java/com/b3meter/distributed/controller/WorkerHealthPoller.java`

**Change 1** — `recordHeartbeat()` Javadoc (lines 234–239):

Add Javadoc:
```java
/**
 * Records a successful heartbeat for the given worker.
 *
 * <p><strong>Recovery policy</strong>: Recovery from {@link WorkerAvailability#UNAVAILABLE}
 * to {@link WorkerAvailability#AVAILABLE} is immediate — the first successful heartbeat
 * after the worker was declared unavailable triggers the AVAILABLE transition.
 * There is no half-open intermediate state. This is a deliberate design choice:
 * the health probe itself is the canary, and a worker responding to it is considered
 * ready to accept work. If the worker fails again, the miss counter re-trips it
 * within {@value #MAX_MISSED_HEARTBEATS} polls ({@value #POLL_INTERVAL_MS}ms each).
 * See spec: {@code specs/010-quality-circuit-breaker/spec.md} §Recovery Policy.
 *
 * @param workerId the worker that responded successfully
 */
private void recordHeartbeat(String workerId) { ... }
```

**Change 2** — Class-level Javadoc update: add a reference to `WorkerRegistry` as the recommended listener implementation.

---

### Files to Leave Unchanged

| File | Reason |
|------|--------|
| `DistributedControllerApplication.java` | Remains a pure entry point; Spring scans `@Configuration` classes automatically |
| `DistributedRunService.java` | Constructor unchanged; accepts `Map<String,WorkerClient>`; receives live view from `WorkerRegistry` |
| `WorkerClient.java` | Internal circuit breaker unchanged; new clients are created fresh on recovery |
| `WorkerTransportSelector.java` | Not called during recovery (see Decision above); unchanged |
| `GrpcWorkerTransport.java`, `WebSocketWorkerTransport.java` | Unchanged |
| All other modules | This is distributed-controller only |

---

## Dependencies / Prerequisites

1. Confirm `DistributedRunService` constructor signature: `Map<String, WorkerClient> workerRegistry` — confirmed at `DistributedRunService.java` line 62.
2. Confirm `WorkerClient` has a public constructor `WorkerClient(String workerId, String host, int port)` — confirmed at `WorkerClient.java` line 66.
3. Confirm `WorkerHealthPoller.addListener()` is public — confirmed at line 175.
4. Confirm `WorkerHealthPoller.WorkerAvailability` enum is public — confirmed at lines 50–55.
5. Run `./gradlew :distributed-controller:test` to confirm baseline is green before starting.

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| `DistributedRunService` caches the `Map` reference at construction and does not see later mutations to `WorkerRegistry.available` | Medium | Dead workers stay in routing | Pass `Collections.unmodifiableMap(available)` which is a live view of the `ConcurrentHashMap`; mutations to the underlying map are visible through the view |
| Spring Boot component scan picks up `DistributedControllerConfiguration` only if it is in a sub-package of `com.b3meter.distributed.controller` | Low | Beans not created | The configuration class is in the same package — `@SpringBootApplication` scans it automatically |
| `WorkerClient` creation on AVAILABLE event blocks the poller thread for up to gRPC connection timeout | Medium | Poller delays processing other workers | The poller processes workers sequentially in `pollAll()` — this is a pre-existing characteristic; reconnect is infrequent |
| `ReentrantLock` in `WorkerRegistry.availableWorkers()` creates contention when many threads call it concurrently | Low | Minor performance impact | Lock is held for a `new HashMap<>(available)` copy operation — O(n) but n is small (≤ 100 workers typical) |
| `DistributedControllerApplication` and `DistributedControllerConfiguration` being in the same module could cause duplicate bean definitions if tests already create beans manually | Low | Spring context startup failure in tests | Annotate integration test with `@SpringBootTest` and ensure test configuration is isolated |

---

## Rollback

1. Delete `WorkerRegistry.java` and `DistributedControllerConfiguration.java`.
2. Revert `WorkerHealthPoller.java` (Javadoc only — functional change is zero).
3. Run `./gradlew :distributed-controller:test` to confirm baseline restored.

No `DistributedRunService` changes are made, so no rollback is needed there. Spring will fall back to not wiring the poller listener (current state) if the configuration class is removed.

---

## Testing Strategy

### Unit Tests — WorkerRegistryTest

All tests use a directly instantiated `WorkerRegistry` and mock `WorkerClient` instances:
- Basic CRUD: `register`, `deregister`, `markUnavailable`, `markAvailable`.
- Boundary: `markUnavailable` for unknown worker is a no-op.
- Boundary: `markAvailable` for unregistered worker is a no-op (no endpoint).
- Concurrency: 10 threads alternating `markUnavailable` and `availableWorkers` — assert no exception.

### Integration Tests — WorkerHealthPollerRegistryIntegrationTest

Use the existing `WorkerHealthPollerTest` test infrastructure (fake gRPC server pattern) to:
1. Register a worker in `WorkerRegistry` and in `WorkerHealthPoller`.
2. Register the listener.
3. Fail the worker (kill the fake server) → 3 polls → assert `registry.isAvailable() == false`.
4. Restore the worker (new fake server) → 1 poll → assert `registry.isAvailable() == true`.

For the "reconnect fails" case: configure `WorkerClient` constructor call in the listener to throw (use a test subclass or spy). Assert `WorkerRegistry.isAvailable()` remains false after the failed reconnect.

### Regression

`./gradlew :distributed-controller:test` — all pre-existing tests pass including `poll_workerRecovery()`.

### Manual Smoke Test

In a Docker Compose environment:
1. Start controller + 2 workers.
2. Kill worker 2.
3. Wait 20 seconds (4 poll intervals).
4. Start a distributed run — observe it targets only worker 1.
5. Restart worker 2.
6. Wait 10 seconds.
7. Start another distributed run — observe it targets both workers.
