package com.jmeternext.distributed.controller;

import com.jmeternext.engine.service.SampleBucketConsumer;
import com.jmeternext.worker.proto.ConfigureAck;
import com.jmeternext.worker.proto.HealthStatus;
import com.jmeternext.worker.proto.StartAck;
import com.jmeternext.worker.proto.StartMessage;
import com.jmeternext.worker.proto.StopAck;
import com.jmeternext.worker.proto.TestPlanMessage;

/**
 * Transport-agnostic interface for communicating with a single remote worker node.
 *
 * <p>Concrete implementations exist for both gRPC ({@link GrpcWorkerTransport}) and
 * WebSocket ({@link WebSocketWorkerTransport}).  The {@link WorkerTransportSelector}
 * probes the worker at connection time and returns whichever transport is reachable.
 *
 * <p>All methods are synchronous (blocking) except {@link #streamResults}, which
 * registers an asynchronous callback.  Implementations must be thread-safe.
 */
public interface WorkerTransport {

    /**
     * Sends the test plan to the worker node and returns its acknowledgement.
     *
     * @param plan the plan to configure on the worker
     * @return the worker's acknowledgement; {@link ConfigureAck#getAccepted()} is
     *         {@code false} on rejection
     */
    ConfigureAck configure(TestPlanMessage plan);

    /**
     * Instructs the worker to start the previously configured run.
     *
     * @param msg the start message (includes optional coordinated start timestamp)
     * @return the worker's acknowledgement
     */
    StartAck start(StartMessage msg);

    /**
     * Gracefully stops the active run on the worker.
     *
     * <p>The worker is allowed to drain in-flight samples before transitioning to IDLE.
     */
    StopAck stop();

    /**
     * Opens an asynchronous result stream from the worker.
     *
     * <p>The {@code consumer} callback is invoked for each received
     * {@link com.jmeternext.worker.proto.SampleResultBatch}. The callback may be invoked
     * from a background thread; implementations must be thread-safe.
     *
     * @param runId    the run whose results should be streamed
     * @param consumer callback invoked for each received sample batch
     */
    void streamResults(String runId, SampleBucketConsumer consumer);

    /**
     * Probes the worker's current liveness and run state.
     *
     * @return the current health status; on transport failure returns a status with
     *         {@code WORKER_STATE_UNSPECIFIED}
     */
    HealthStatus getHealth();

    /**
     * Closes the underlying transport connection and releases any resources.
     *
     * <p>After this call the transport must not be used again.
     */
    void close();
}
