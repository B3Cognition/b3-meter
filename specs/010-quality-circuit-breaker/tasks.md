# Tasks: Circuit Breaker Recovery — WorkerRegistry Wiring

**Domain**: 010-quality-circuit-breaker
**Created**: 2026-03-29
**Status**: Complete — all 7 tasks done; BUILD_DONE — PASS (38 tests, all passing)

---

## Task List

### T-010-001 — Verify Baseline and Read Existing Test Infrastructure

**Type**: test
**Files affected**: none (read-only)
**Complexity**: S
**Dependencies**: none

**Description**: Confirm the baseline distributed-controller test suite is green and understand the existing `WorkerHealthPollerTest` test infrastructure before making changes.

**Steps**:
1. Run `./gradlew :distributed-controller:test` — must exit with code 0.
2. Read `modules/distributed-controller/src/test/java/com/b3meter/distributed/controller/WorkerHealthPollerTest.java` to understand the fake gRPC server setup used in existing tests.
3. Confirm `WorkerClient(String workerId, String host, int port)` constructor exists at `WorkerClient.java` line 66.
4. Confirm `WorkerHealthPoller.addListener()` is public (line 175).
5. Confirm `WorkerAvailability` enum is public (lines 50–55).

**Acceptance Criteria**:
- `./gradlew :distributed-controller:test` exits with code 0.
- The `poll_workerRecovery()` test passes (confirms the poller itself is correct).

---

### T-010-002 — Create WorkerRegistry

**Type**: create
**Files affected**:
- `modules/distributed-controller/src/main/java/com/b3meter/distributed/controller/WorkerRegistry.java` (create)
**Complexity**: M
**Dependencies**: T-010-001

**Description**: Implement `WorkerRegistry` with `ReentrantLock`-based thread safety. The `available` map is a `ConcurrentHashMap` that is mutated under lock for atomicity with `endpoints`. `availableWorkers()` returns an unmodifiable live view.

**Implementation**:

```java
package com.b3meter.distributed.controller;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe registry of worker nodes, coupling availability state
 * with {@link WorkerClient} instances and connection metadata.
 *
 * <p>The registry is the single source of truth for which workers are
 * currently available for routing. {@link WorkerHealthPoller} must be
 * wired to call {@link #markUnavailable(String)} and
 * {@link #markAvailable(String, WorkerClient)} as workers change state.
 *
 * <p>Thread-safety: all mutating methods use {@link ReentrantLock} to
 * atomically update both the available-clients map and the endpoints map.
 * {@link #availableWorkers()} returns a live unmodifiable view —
 * callers see mutations without holding a lock.
 */
public final class WorkerRegistry {

    private static final Logger LOG = Logger.getLogger(WorkerRegistry.class.getName());

    /**
     * Immutable per-worker connection metadata retained for reconnect attempts.
     */
    record WorkerEndpoint(String host, int grpcPort, int wsPort) {}

    private final ReentrantLock lock = new ReentrantLock();

    /** Workers currently available for routing. */
    private final ConcurrentHashMap<String, WorkerClient> available = new ConcurrentHashMap<>();

    /** Connection metadata for all registered workers (available or not). */
    private final ConcurrentHashMap<String, WorkerEndpoint> endpoints = new ConcurrentHashMap<>();

    public void register(WorkerClient client, String host, int grpcPort, int wsPort) { ... }
    public void deregister(String workerId) { ... }
    public void markUnavailable(String workerId) { ... }
    public void markAvailable(String workerId, WorkerClient newClient) { ... }
    public Map<String, WorkerClient> availableWorkers() {
        return Collections.unmodifiableMap(available);
    }
    public boolean isAvailable(String workerId) { return available.containsKey(workerId); }
    public WorkerEndpoint endpointFor(String workerId) { return endpoints.get(workerId); }
}
```

All mutating methods acquire `lock` before touching both `available` and `endpoints`. `markUnavailable` removes from `available` only (not from `endpoints` — endpoint is needed for reconnect). `markAvailable` adds to `available` only if `endpoints` contains the worker (guards against ghost IDs). `deregister` removes from both.

Run `./gradlew :distributed-controller:compileJava`.

**Acceptance Criteria**:
- `./gradlew :distributed-controller:compileJava` exits with code 0.
- File contains `ReentrantLock` and zero `synchronized` keywords.
- `availableWorkers()` returns a live unmodifiable view (not a snapshot copy).

---

### T-010-003 — Test WorkerRegistry

**Type**: test
**Files affected**:
- `modules/distributed-controller/src/test/java/com/b3meter/distributed/controller/WorkerRegistryTest.java` (create)
**Complexity**: M
**Dependencies**: T-010-002

**Description**: Write JUnit 5 unit tests for `WorkerRegistry` covering all state transitions and concurrent access.

**Tests to implement**:

```java
class WorkerRegistryTest {

    private WorkerRegistry registry;

    @BeforeEach
    void setUp() { registry = new WorkerRegistry(); }

    @Test
    void register_workerIsAvailable() {
        WorkerClient client = mockClient("w1");
        registry.register(client, "localhost", 50051, 8080);
        assertThat(registry.isAvailable("w1")).isTrue();
        assertThat(registry.availableWorkers()).containsKey("w1");
    }

    @Test
    void markUnavailable_removesFromRouting() {
        registry.register(mockClient("w1"), "localhost", 50051, 8080);
        registry.markUnavailable("w1");
        assertThat(registry.isAvailable("w1")).isFalse();
        assertThat(registry.availableWorkers()).doesNotContainKey("w1");
    }

    @Test
    void markUnavailable_retainsEndpointForReconnect() {
        registry.register(mockClient("w1"), "localhost", 50051, 8080);
        registry.markUnavailable("w1");
        assertThat(registry.endpointFor("w1")).isNotNull();
        assertThat(registry.endpointFor("w1").host()).isEqualTo("localhost");
    }

    @Test
    void markAvailable_reAddsToRouting() {
        registry.register(mockClient("w1"), "localhost", 50051, 8080);
        registry.markUnavailable("w1");
        WorkerClient newClient = mockClient("w1");
        registry.markAvailable("w1", newClient);
        assertThat(registry.isAvailable("w1")).isTrue();
        assertThat(registry.availableWorkers()).containsKey("w1");
    }

    @Test
    void markAvailable_unknownWorker_isNoOp() {
        WorkerClient client = mockClient("ghost");
        registry.markAvailable("ghost", client);
        assertThat(registry.isAvailable("ghost")).isFalse();
    }

    @Test
    void deregister_removesCompletely() {
        registry.register(mockClient("w1"), "localhost", 50051, 8080);
        registry.deregister("w1");
        assertThat(registry.isAvailable("w1")).isFalse();
        assertThat(registry.endpointFor("w1")).isNull();
    }

    @Test
    void markUnavailable_unknownWorker_isNoOp() {
        assertThatCode(() -> registry.markUnavailable("nonexistent")).doesNotThrowAnyException();
    }

    @Test
    void availableWorkers_isLiveView_reflectsSubsequentMutation() {
        Map<String, WorkerClient> view = registry.availableWorkers();
        registry.register(mockClient("w1"), "localhost", 50051, 8080);
        assertThat(view).containsKey("w1"); // live view
    }

    @Test
    void concurrent_markUnavailable_and_availableWorkers_noException()
            throws InterruptedException {
        registry.register(mockClient("w1"), "localhost", 50051, 8080);
        CountDownLatch latch = new CountDownLatch(10);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            threads.add(Thread.ofVirtual().start(() -> {
                registry.markUnavailable("w1");
                latch.countDown();
            }));
            threads.add(Thread.ofVirtual().start(() -> {
                registry.availableWorkers();
                latch.countDown();
            }));
        }
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    }

    private WorkerClient mockClient(String workerId) {
        // Use Mockito or a test double; WorkerClient has a (String, ManagedChannel) constructor
        // In-process gRPC channel is simplest for tests
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 1)
            .usePlaintext().build();
        return new WorkerClient(workerId, channel);
    }
}
```

**Acceptance Criteria**:
- All tests pass: `./gradlew :distributed-controller:test --tests "*.WorkerRegistryTest"`.
- The concurrent test uses `Thread.ofVirtual()` (Java 21 virtual threads) consistent with the project's virtual-threads adoption.

---

### T-010-004 — Create DistributedControllerConfiguration

**Type**: create
**Files affected**:
- `modules/distributed-controller/src/main/java/com/b3meter/distributed/controller/DistributedControllerConfiguration.java` (create)
**Complexity**: M
**Dependencies**: T-010-002

**Description**: Create the Spring `@Configuration` class that wires all distributed-controller beans and registers the health poller listener.

**Implementation**:

```java
package com.b3meter.distributed.controller;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Spring bean wiring for the distributed controller subsystem.
 *
 * <p>Connects {@link WorkerHealthPoller} to {@link WorkerRegistry} via a listener:
 * <ul>
 *   <li>UNAVAILABLE events: removes worker from routing immediately.</li>
 *   <li>AVAILABLE events: creates a new {@link WorkerClient} and re-adds to routing.
 *       {@link WorkerTransportSelector.TransportUnavailableException} is caught and
 *       logged; the worker remains unavailable until the next successful poll.</li>
 * </ul>
 *
 * <p>Recovery policy: no half-open state. See
 * {@code specs/010-quality-circuit-breaker/spec.md} §Recovery Policy.
 */
@Configuration
public class DistributedControllerConfiguration {

    private static final Logger LOG =
        Logger.getLogger(DistributedControllerConfiguration.class.getName());

    @Bean
    public WorkerRegistry workerRegistry() {
        return new WorkerRegistry();
    }

    @Bean
    public WorkerHealthPoller workerHealthPoller(WorkerRegistry registry) {
        WorkerHealthPoller poller = new WorkerHealthPoller();
        poller.addListener((workerId, availability) -> {
            switch (availability) {
                case UNAVAILABLE -> registry.markUnavailable(workerId);
                case AVAILABLE -> reconnect(workerId, registry);
            }
        });
        poller.start();
        return poller;
    }

    private void reconnect(String workerId, WorkerRegistry registry) {
        WorkerRegistry.WorkerEndpoint ep = registry.endpointFor(workerId);
        if (ep == null) {
            LOG.log(Level.WARNING,
                "AVAILABLE event for unregistered worker={0}; ignoring", workerId);
            return;
        }
        try {
            WorkerClient newClient = new WorkerClient(workerId, ep.host(), ep.grpcPort());
            registry.markAvailable(workerId, newClient);
            LOG.log(Level.INFO, "Worker={0} reconnected at {1}:{2}",
                new Object[]{workerId, ep.host(), ep.grpcPort()});
        } catch (Exception ex) {
            LOG.log(Level.WARNING,
                "Reconnect failed for worker={0}: {1}; worker remains unavailable",
                new Object[]{workerId, ex.getMessage()});
        }
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

**Notes**:
- `WorkerClient` constructor at `WorkerClient.java` line 66 takes `(String workerId, String host, int port)` — used directly for reconnect, skipping `WorkerTransportSelector` since the health poller already confirmed gRPC is responding.
- `poller.start()` is called here so the bean is active immediately after context startup.
- No `@PreDestroy` needed — `WorkerHealthPoller` is `AutoCloseable`; Spring will call `close()` on `AutoCloseable` beans at context shutdown if registered as `@Bean`.

Run `./gradlew :distributed-controller:compileJava`.

**Acceptance Criteria**:
- `./gradlew :distributed-controller:compileJava` exits with code 0.
- No `@Autowired` fields — constructor injection only (Spring best practice).
- `addListener()` is called exactly once with a lambda covering both UNAVAILABLE and AVAILABLE.

---

### T-010-005 — Document No-Half-Open-State Policy in WorkerHealthPoller

**Type**: modify
**Files affected**:
- `modules/distributed-controller/src/main/java/com/b3meter/distributed/controller/WorkerHealthPoller.java` (modify)
**Complexity**: S
**Dependencies**: T-010-004

**Description**: Add Javadoc to `recordHeartbeat()` documenting the immediate recovery policy and add a reference to `WorkerRegistry` in the class-level Javadoc.

**Changes to WorkerHealthPoller.java**:

1. Add to the class-level Javadoc (after the existing paragraph about thread-safety):
   ```
    * <p>Recovery policy: recovery from {@link WorkerAvailability#UNAVAILABLE} to
    * {@link WorkerAvailability#AVAILABLE} is <em>immediate</em> — the first
    * successful heartbeat transitions the worker back to available. There is no
    * half-open state. Callers should register a {@code WorkerRegistry} as a listener
    * to handle routing updates on state change.
    * See {@code specs/010-quality-circuit-breaker/spec.md} §Recovery Policy.
   ```

2. Add Javadoc above `recordHeartbeat()` private method:
   ```java
   /**
    * Records a successful heartbeat for the given worker, resetting the miss counter
    * and transitioning to {@link WorkerAvailability#AVAILABLE} if the worker was
    * previously unavailable.
    *
    * <p><strong>Recovery policy</strong>: recovery is immediate. The first successful
    * heartbeat after UNAVAILABLE transitions the worker to AVAILABLE with no
    * intermediate half-open state. If the worker fails again, the miss counter
    * re-trips it within {@value #MAX_MISSED_HEARTBEATS} polls
    * ({@value #POLL_INTERVAL_MS}ms each = {@code MAX_MISSED_HEARTBEATS * POLL_INTERVAL_MS}ms).
    *
    * @param workerId the worker that responded successfully
    */
   ```

Run `./gradlew :distributed-controller:compileJava`.

**Acceptance Criteria**:
- `./gradlew :distributed-controller:compileJava` exits with code 0.
- Zero functional changes to `WorkerHealthPoller` (Javadoc-only modification).
- The words "half-open" and "immediate" appear in the new Javadoc.

---

### T-010-006 — Write Poller-Registry Integration Tests

**Type**: test
**Files affected**:
- `modules/distributed-controller/src/test/java/com/b3meter/distributed/controller/WorkerHealthPollerRegistryIntegrationTest.java` (create)
**Complexity**: M
**Dependencies**: T-010-002, T-010-004

**Description**: Write integration tests that verify the end-to-end behaviour: poller emits events → listener updates registry → registry reflects correct state. Reuse the in-process gRPC fake server pattern from `WorkerHealthPollerTest`.

**Tests to implement**:

```java
class WorkerHealthPollerRegistryIntegrationTest {

    private WorkerHealthPoller poller;
    private WorkerRegistry registry;
    // fake gRPC server (reuse InProcessServerBuilder pattern from WorkerHealthPollerTest)

    @BeforeEach
    void setUp() throws Exception {
        registry = new WorkerRegistry();
        poller = new WorkerHealthPoller(Executors.newSingleThreadScheduledExecutor());
        // Wire the listener (same logic as DistributedControllerConfiguration)
        poller.addListener((workerId, availability) -> {
            if (availability == WorkerHealthPoller.WorkerAvailability.UNAVAILABLE) {
                registry.markUnavailable(workerId);
            } else {
                WorkerRegistry.WorkerEndpoint ep = registry.endpointFor(workerId);
                if (ep != null) {
                    registry.markAvailable(workerId, new WorkerClient(workerId, ep.host(), ep.grpcPort()));
                }
            }
        });
    }

    @Test
    void unavailableEvent_removesWorkerFromRegistry() {
        // Setup: register worker in both poller and registry
        // Trigger 3 consecutive misses
        // Assert: registry.isAvailable() == false
    }

    @Test
    void availableEvent_reAddsWorkerToRegistry() {
        // Setup: start from UNAVAILABLE state (3 misses already recorded)
        // Restore fake server
        // Trigger 1 successful poll
        // Assert: registry.isAvailable() == true
    }

    @Test
    void availableEvent_reconnectFails_workerRemainsUnavailable() {
        // Setup: mark UNAVAILABLE, override registry to return null endpoint for workerId
        // Trigger AVAILABLE event (by direct poller.recordHeartbeat if package-private,
        // or by emitting the event through a custom listener registered before the registry listener)
        // Assert: registry.isAvailable() == false
        // Assert: no exception propagated
    }

    @AfterEach
    void tearDown() throws Exception {
        poller.close();
    }
}
```

**Note**: `recordHeartbeat()` is private in `WorkerHealthPoller`. To trigger the AVAILABLE event in the third test without starting a real gRPC server, use the same fake-server pattern as `WorkerHealthPollerTest` — start a fake server, register the worker with a client pointing to it, call `poller.pollAll()`. The test double server can be configured to return healthy or unhealthy responses.

**Acceptance Criteria**:
- All 3 tests pass: `./gradlew :distributed-controller:test --tests "*.WorkerHealthPollerRegistryIntegrationTest"`.
- Tests complete in under 30 seconds (use manual poll calls, not real 5s intervals).

---

### T-010-007 — Full Test Suite Validation

**Type**: test
**Files affected**: none
**Complexity**: S
**Dependencies**: T-010-003, T-010-005, T-010-006

**Description**: Run the complete distributed-controller test suite and confirm all pre-existing tests and all new tests pass.

**Steps**:
1. Run `./gradlew :distributed-controller:test`.
2. Confirm the test output includes:
   - `WorkerRegistryTest` — 9 tests, all pass.
   - `WorkerHealthPollerRegistryIntegrationTest` — 3 tests, all pass.
   - All pre-existing `WorkerHealthPollerTest` tests — all pass.
3. Run `./gradlew :distributed-controller:compileJava --warning-mode all` — zero warnings in new files.
4. Confirm `WorkerRegistry.java` contains `ReentrantLock` and zero `synchronized` keywords.
5. Commit with message: `feat(distributed-controller): add WorkerRegistry and wire health poller listener for circuit breaker recovery`.

**Acceptance Criteria**:
- `./gradlew :distributed-controller:test` exits with code 0.
- All SC-010.001 through SC-010.006 pass.
- `WorkerRegistry.java` uses `ReentrantLock` exclusively (no `synchronized`).
- `DistributedControllerConfiguration.java` exists with `@Configuration` annotation and all 4 `@Bean` methods.
- `WorkerHealthPoller.java` Javadoc includes the recovery policy documentation.
