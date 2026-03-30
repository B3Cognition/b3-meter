/*
 * Copyright 2024-2026 b3meter Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.b3meter.distributed.controller;

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import com.b3meter.worker.proto.ConfigureAck;
import com.b3meter.worker.proto.StartAck;
import com.b3meter.worker.proto.StartMessage;
import com.b3meter.worker.proto.StopAck;
import com.b3meter.worker.proto.TestPlanMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * High-level service that coordinates a distributed load-test run across multiple
 * gRPC worker nodes.
 *
 * <p>Lifecycle of a distributed run:
 * <ol>
 *   <li>{@link #startDistributed(String, List, byte[], int, long)} — configure each
 *       worker with the test plan, then issue a synchronised start using a 5-second
 *       future timestamp so all workers begin loading at exactly the same wall-clock
 *       instant. Result streams from every worker are routed through
 *       {@link ResultAggregator} into a shared broker.</li>
 *   <li>{@link #stopDistributed(String)} — gracefully drain in-flight samples on
 *       every worker.</li>
 *   <li>{@link #stopDistributedNow(String)} — immediately abort all workers.</li>
 * </ol>
 *
 * <p>Thread-safety: all public methods are thread-safe.
 */
public final class DistributedRunService {

    private static final Logger LOG = Logger.getLogger(DistributedRunService.class.getName());

    /**
     * How far in the future (milliseconds) the coordinated start timestamp is set.
     * Gives all workers enough time to receive the Start RPC before load begins.
     */
    static final long COORDINATED_START_DELAY_MS = 5_000L;

    private final Map<String, WorkerClient> workerRegistry;
    private final ResultAggregator aggregator;

    /** Active run metadata keyed by runId. */
    private final ConcurrentHashMap<String, RunRecord> activeRuns = new ConcurrentHashMap<>();

    /**
     * Creates a service with the given worker registry and result aggregator.
     *
     * @param workerRegistry map of workerId → client; must not be {@code null}
     * @param aggregator     the aggregator that merges worker result streams; must not be {@code null}
     */
    public DistributedRunService(Map<String, WorkerClient> workerRegistry,
                                  ResultAggregator aggregator) {
        this.workerRegistry = Objects.requireNonNull(workerRegistry, "workerRegistry");
        this.aggregator = Objects.requireNonNull(aggregator, "aggregator");
    }

    // -------------------------------------------------------------------------
    // Start
    // -------------------------------------------------------------------------

    /**
     * Configures all requested workers and starts a coordinated distributed run.
     *
     * <p>The virtual users are distributed evenly across the workers; any remainder is
     * assigned to the first worker.
     *
     * @param planId          logical identifier of the test plan (used for logging)
     * @param workerIds       IDs of the workers to involve; must all be registered
     * @param planContent     JMX plan bytes sent to each worker
     * @param totalVirtualUsers total virtual users to distribute
     * @param durationSeconds  run duration (0 = until plan completes)
     * @return the generated run ID for this distributed run
     * @throws IllegalArgumentException if {@code workerIds} is empty or a worker is not found
     * @throws IllegalStateException    if any worker rejects the Configure or Start RPC
     */
    public String startDistributed(
            String planId,
            List<String> workerIds,
            byte[] planContent,
            int totalVirtualUsers,
            long durationSeconds) {

        if (workerIds == null || workerIds.isEmpty()) {
            throw new IllegalArgumentException("workerIds must not be empty");
        }

        String runId = UUID.randomUUID().toString();
        LOG.log(Level.INFO,
                "Starting distributed run runId={0}, planId={1}, workers={2}, VUs={3}",
                new Object[]{runId, planId, workerIds, totalVirtualUsers});

        List<WorkerClient> clients = resolveWorkers(workerIds);
        int workerCount = clients.size();
        int baseVUs = totalVirtualUsers / workerCount;
        int remainder = totalVirtualUsers % workerCount;

        // --- Phase 1: Configure ---
        List<String> configuredWorkers = new ArrayList<>();
        for (int i = 0; i < clients.size(); i++) {
            WorkerClient client = clients.get(i);
            int vus = baseVUs + (i == 0 ? remainder : 0);

            TestPlanMessage plan = TestPlanMessage.newBuilder()
                    .setRunId(runId)
                    .setPlanContent(ByteString.copyFrom(planContent))
                    .setVirtualUsers(Math.max(1, vus))
                    .setDurationSeconds(durationSeconds)
                    .build();

            ConfigureAck ack = client.configure(plan);
            if (!ack.getAccepted()) {
                // Roll back already-configured workers
                rollbackStopNow(configuredWorkers, runId);
                throw new IllegalStateException(
                        "Worker " + client.getWorkerId() + " rejected Configure: " + ack.getMessage());
            }
            configuredWorkers.add(client.getWorkerId());
            LOG.log(Level.INFO,
                    "Worker={0} configured for runId={1} with {2} VUs",
                    new Object[]{client.getWorkerId(), runId, vus});
        }

        // --- Phase 2: Coordinated Start (5 s in the future) ---
        Instant startAt = Instant.now().plusMillis(COORDINATED_START_DELAY_MS);
        Timestamp protoStartAt = Timestamp.newBuilder()
                .setSeconds(startAt.getEpochSecond())
                .setNanos(startAt.getNano())
                .build();
        StartMessage startMsg = StartMessage.newBuilder()
                .setRunId(runId)
                .setStartAt(protoStartAt)
                .build();

        for (WorkerClient client : clients) {
            StartAck ack = client.start(startMsg);
            if (!ack.getAccepted()) {
                rollbackStopNow(clients.stream().map(WorkerClient::getWorkerId).toList(), runId);
                throw new IllegalStateException(
                        "Worker " + client.getWorkerId() + " rejected Start: " + ack.getMessage());
            }
            LOG.log(Level.INFO,
                    "Worker={0} started for runId={1}", new Object[]{client.getWorkerId(), runId});
        }

        // --- Phase 3: Open result streams ---
        for (WorkerClient client : clients) {
            aggregator.subscribeWorker(runId, client);
        }

        activeRuns.put(runId, new RunRecord(runId, List.copyOf(workerIds)));
        LOG.log(Level.INFO, "Distributed run runId={0} started on {1} workers",
                new Object[]{runId, workerCount});
        return runId;
    }

    // -------------------------------------------------------------------------
    // Stop (graceful)
    // -------------------------------------------------------------------------

    /**
     * Gracefully stops all workers participating in the given run.
     *
     * <p>Each worker is sent a {@code Stop} RPC in sequence. If a worker's circuit
     * is open, it is skipped (the run on that worker is presumed already failed).
     *
     * @param runId the run to stop
     * @throws IllegalArgumentException if the run is not known
     */
    public void stopDistributed(String runId) {
        RunRecord record = requireRun(runId);
        LOG.log(Level.INFO, "Graceful stop requested for runId={0}", runId);

        for (String workerId : record.workerIds()) {
            WorkerClient client = workerRegistry.get(workerId);
            if (client == null || client.isCircuitOpen()) {
                LOG.log(Level.WARNING,
                        "Skipping stop for worker={0} (not found or circuit open)", workerId);
                continue;
            }
            StopAck ack = client.stop(runId);
            LOG.log(Level.INFO,
                    "Stop ack from worker={0}: accepted={1}, msg={2}",
                    new Object[]{workerId, ack.getAccepted(), ack.getMessage()});
        }

        activeRuns.remove(runId);
    }

    // -------------------------------------------------------------------------
    // StopNow (immediate)
    // -------------------------------------------------------------------------

    /**
     * Immediately aborts all workers participating in the given run.
     *
     * @param runId the run to abort
     * @throws IllegalArgumentException if the run is not known
     */
    public void stopDistributedNow(String runId) {
        RunRecord record = requireRun(runId);
        LOG.log(Level.INFO, "Immediate stop requested for runId={0}", runId);

        for (String workerId : record.workerIds()) {
            WorkerClient client = workerRegistry.get(workerId);
            if (client == null) {
                LOG.log(Level.WARNING, "Worker={0} not found for stopNow", workerId);
                continue;
            }
            StopAck ack = client.stopNow(runId);
            LOG.log(Level.INFO,
                    "StopNow ack from worker={0}: accepted={1}",
                    new Object[]{workerId, ack.getAccepted()});
        }

        activeRuns.remove(runId);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the result aggregator used by this service.
     *
     * @return the {@link ResultAggregator}; never {@code null}
     */
    public ResultAggregator getAggregator() {
        return aggregator;
    }

    /**
     * Returns an unmodifiable view of the currently active run IDs.
     *
     * @return set of active run IDs
     */
    public java.util.Set<String> activeRunIds() {
        return Collections.unmodifiableSet(activeRuns.keySet());
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private List<WorkerClient> resolveWorkers(List<String> workerIds) {
        List<WorkerClient> clients = new ArrayList<>(workerIds.size());
        for (String id : workerIds) {
            WorkerClient client = workerRegistry.get(id);
            if (client == null) {
                throw new IllegalArgumentException("Worker not found in registry: " + id);
            }
            clients.add(client);
        }
        return clients;
    }

    private void rollbackStopNow(List<String> workerIds, String runId) {
        for (String workerId : workerIds) {
            WorkerClient client = workerRegistry.get(workerId);
            if (client != null) {
                try {
                    client.stopNow(runId);
                } catch (Exception ex) {
                    LOG.log(Level.WARNING,
                            "Rollback stopNow failed for worker={0}: {1}",
                            new Object[]{workerId, ex.getMessage()});
                }
            }
        }
    }

    private RunRecord requireRun(String runId) {
        RunRecord record = activeRuns.get(runId);
        if (record == null) {
            throw new IllegalArgumentException("No active distributed run with ID: " + runId);
        }
        return record;
    }

    // -------------------------------------------------------------------------
    // Internal record
    // -------------------------------------------------------------------------

    private record RunRecord(String runId, List<String> workerIds) {}
}
