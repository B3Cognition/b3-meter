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

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe registry of worker nodes, coupling availability state with
 * {@link WorkerClient} instances and connection metadata.
 *
 * <p>The registry is the single source of truth for which workers are currently
 * available for routing. {@link WorkerHealthPoller} must be wired to call
 * {@link #markUnavailable(String)} and {@link #markAvailable(String, WorkerClient)}
 * as workers change state. Register a {@code WorkerRegistry} as a listener on
 * {@link WorkerHealthPoller} to receive availability change events automatically.
 *
 * <p>Recovery policy: recovery from {@link WorkerHealthPoller.WorkerAvailability#UNAVAILABLE}
 * to {@link WorkerHealthPoller.WorkerAvailability#AVAILABLE} is <em>immediate</em> — the first
 * successful heartbeat transitions the worker back to available. There is no
 * half-open state. See {@code specs/010-quality-circuit-breaker/spec.md} §Recovery Policy.
 *
 * <p>Thread-safety: all mutating methods use {@link ReentrantLock} to atomically
 * update both the available-clients map and the endpoints map.
 * {@link #availableWorkers()} returns a live unmodifiable view of the available map —
 * callers see mutations without holding a lock.
 */
public final class WorkerRegistry {

    private static final Logger LOG = Logger.getLogger(WorkerRegistry.class.getName());

    /**
     * Immutable per-worker connection metadata retained for reconnect attempts.
     */
    public record WorkerEndpoint(String host, int grpcPort, int wsPort) {}

    private final ReentrantLock lock = new ReentrantLock();

    /** Workers currently available for routing. Modified under lock. */
    private final ConcurrentHashMap<String, WorkerClient> available = new ConcurrentHashMap<>();

    /** Connection metadata for all registered workers (available or not). */
    private final ConcurrentHashMap<String, WorkerEndpoint> endpoints = new ConcurrentHashMap<>();

    /**
     * Registers a worker as available for routing and records its connection metadata.
     *
     * <p>If the worker is already registered, the existing client and metadata are replaced.
     *
     * @param client   the worker client; its {@code workerId} is the registry key
     * @param host     hostname or IP of the worker node
     * @param grpcPort gRPC port
     * @param wsPort   WebSocket port (stored for reconnect; may be 0 if unused)
     */
    public void register(WorkerClient client, String host, int grpcPort, int wsPort) {
        String id = client.getWorkerId();
        lock.lock();
        try {
            endpoints.put(id, new WorkerEndpoint(host, grpcPort, wsPort));
            available.put(id, client);
            LOG.log(Level.INFO, "Registered worker={0} ({1}:{2})",
                    new Object[]{id, host, grpcPort});
        } finally {
            lock.unlock();
        }
    }

    /**
     * Completely removes a worker from the registry (both routing and metadata).
     *
     * <p>After this call the worker will not receive routing and will not be
     * eligible for reconnect. Use {@link #markUnavailable(String)} to temporarily
     * remove from routing while retaining reconnect metadata.
     *
     * @param workerId the worker to deregister
     */
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

    /**
     * Marks a worker as unavailable, removing it from routing.
     *
     * <p>Connection metadata is retained so that a subsequent AVAILABLE event can
     * recreate the {@link WorkerClient} without re-supplying host/port. The worker
     * remains registered in {@link WorkerHealthPoller} and continues to be polled.
     *
     * <p>If the worker is not known, this method is a no-op.
     *
     * @param workerId the worker to mark unavailable
     */
    public void markUnavailable(String workerId) {
        lock.lock();
        try {
            WorkerClient removed = available.remove(workerId);
            if (removed != null) {
                LOG.log(Level.WARNING,
                        "Worker={0} marked unavailable; removed from routing", workerId);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Marks a worker as available, adding it back to routing with a fresh client.
     *
     * <p>If the worker is not registered (no endpoint metadata), this method is a
     * no-op and a warning is logged. This guard prevents ghost worker IDs from being
     * added to the routing map.
     *
     * @param workerId  the worker to re-add to routing
     * @param newClient a fresh {@link WorkerClient} for the recovered worker
     */
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
     * Returns an unmodifiable live view of the available workers.
     *
     * <p>The returned map reflects subsequent calls to {@link #markAvailable},
     * {@link #markUnavailable}, {@link #register}, and {@link #deregister} without
     * the caller needing to obtain a new reference. Callers must not attempt to
     * modify the returned map.
     *
     * @return live unmodifiable view of workerId → {@link WorkerClient} for available workers
     */
    public Map<String, WorkerClient> availableWorkers() {
        return Collections.unmodifiableMap(available);
    }

    /**
     * Returns whether the given worker is currently available for routing.
     *
     * @param workerId the worker to query
     * @return {@code true} if the worker is in the available routing map
     */
    public boolean isAvailable(String workerId) {
        return available.containsKey(workerId);
    }

    /**
     * Returns the connection metadata for the given worker, or {@code null} if not registered.
     *
     * <p>Metadata is retained even when a worker is marked unavailable, enabling reconnect
     * without re-supplying host/port.
     *
     * @param workerId the worker to query
     * @return endpoint metadata, or {@code null} if not registered
     */
    public WorkerEndpoint endpointFor(String workerId) {
        return endpoints.get(workerId);
    }
}
