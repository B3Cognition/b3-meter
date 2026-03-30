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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Spring bean wiring for the distributed controller subsystem.
 *
 * <p>This class is the critical glue that was previously missing: it registers
 * a {@link WorkerHealthPoller} listener that calls {@link WorkerRegistry#markUnavailable}
 * and {@link WorkerRegistry#markAvailable} on state transitions, closing the circuit
 * breaker loop between the poller and routing.
 *
 * <p>Listener behaviour:
 * <ul>
 *   <li>UNAVAILABLE events: removes worker from routing immediately via
 *       {@link WorkerRegistry#markUnavailable(String)}.</li>
 *   <li>AVAILABLE events: creates a fresh {@link WorkerClient} using stored endpoint
 *       metadata and calls {@link WorkerRegistry#markAvailable(String, WorkerClient)}.
 *       If the endpoint is unknown or client creation fails, logs at WARNING level and
 *       leaves the worker unavailable until the next successful poll.</li>
 * </ul>
 *
 * <p>Recovery policy: no half-open state. The first successful health poll after
 * UNAVAILABLE transitions the worker immediately to AVAILABLE.
 * See {@code specs/010-quality-circuit-breaker/spec.md} §Recovery Policy.
 *
 * <p>{@link DistributedControllerApplication} remains a pure entry point; Spring
 * component-scan picks up this {@code @Configuration} class automatically.
 */
@Configuration
public class DistributedControllerConfiguration {

    private static final Logger LOG =
            Logger.getLogger(DistributedControllerConfiguration.class.getName());

    /**
     * Creates the shared {@link WorkerRegistry}.
     *
     * @return a new empty registry
     */
    @Bean
    public WorkerRegistry workerRegistry() {
        return new WorkerRegistry();
    }

    /**
     * Creates and starts the {@link WorkerHealthPoller}, wiring the registry listener.
     *
     * <p>The listener registered here is the only caller of
     * {@link WorkerHealthPoller#addListener} — this was the missing wiring.
     * On UNAVAILABLE, routing is immediately withdrawn. On AVAILABLE, a new
     * {@link WorkerClient} is created directly (the health poller has already confirmed
     * the worker responds to gRPC, so {@code WorkerTransportSelector} is not needed).
     *
     * @param registry the worker registry that receives availability notifications
     * @return a started poller
     */
    @Bean
    public WorkerHealthPoller workerHealthPoller(WorkerRegistry registry) {
        WorkerHealthPoller poller = new WorkerHealthPoller();
        poller.addListener((workerId, availability) -> {
            switch (availability) {
                case UNAVAILABLE -> registry.markUnavailable(workerId);
                case AVAILABLE   -> reconnect(workerId, registry);
            }
        });
        poller.start();
        return poller;
    }

    /**
     * Creates the {@link ResultAggregator}.
     *
     * @return a new aggregator
     */
    @Bean
    public ResultAggregator resultAggregator() {
        return new ResultAggregator();
    }

    /**
     * Creates the {@link DistributedRunService}, passing a live unmodifiable view
     * of the available workers from the registry.
     *
     * <p>Because {@link WorkerRegistry#availableWorkers()} returns a live view of the
     * underlying {@code ConcurrentHashMap}, {@link DistributedRunService} always sees
     * the current available-worker state without requiring any constructor change.
     *
     * @param registry   the registry providing the live worker map
     * @param aggregator the result aggregator
     * @return a configured distributed run service
     */
    @Bean
    public DistributedRunService distributedRunService(
            WorkerRegistry registry,
            ResultAggregator aggregator) {
        return new DistributedRunService(registry.availableWorkers(), aggregator);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Handles an AVAILABLE transition by creating a fresh {@link WorkerClient} and
     * re-adding the worker to routing.
     *
     * <p>If the endpoint is not found (worker was never registered) or client creation
     * throws unexpectedly, a warning is logged and the worker remains unavailable.
     * This handles the US-010.5 requirement: false-positive health polls do not crash
     * the controller.
     *
     * @param workerId the worker that became available
     * @param registry the registry to update
     */
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
}
