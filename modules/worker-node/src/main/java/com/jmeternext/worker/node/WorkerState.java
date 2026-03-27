package com.jmeternext.worker.node;

/**
 * Finite-state machine for the worker-node lifecycle.
 *
 * <p>Legal transitions:
 * <pre>
 *   IDLE → CONFIGURED   (on Configure RPC)
 *   CONFIGURED → RUNNING (on Start RPC)
 *   RUNNING → STOPPING   (on Stop RPC)
 *   STOPPING → IDLE      (on run completion)
 *   RUNNING → IDLE       (on StopNow RPC — immediate)
 *   any → ERROR          (on fatal engine failure)
 * </pre>
 */
public enum WorkerState {

    /**
     * Worker is alive and not executing a test.
     * This is the initial state after startup.
     */
    IDLE,

    /**
     * Worker has accepted and validated a test plan and is waiting for Start.
     */
    CONFIGURED,

    /**
     * Worker is actively executing a test run.
     */
    RUNNING,

    /**
     * Worker is draining in-flight samples after a graceful Stop request.
     * Transitions to IDLE once all threads have finished.
     */
    STOPPING,

    /**
     * Worker encountered a fatal error and cannot accept new work.
     */
    ERROR;

    /**
     * Returns the proto-equivalent {@link com.jmeternext.worker.proto.WorkerState} for
     * this enum value.
     *
     * @return the corresponding proto WorkerState; never {@code null}
     */
    public com.jmeternext.worker.proto.WorkerState toProto() {
        return switch (this) {
            case IDLE      -> com.jmeternext.worker.proto.WorkerState.WORKER_STATE_IDLE;
            case CONFIGURED -> com.jmeternext.worker.proto.WorkerState.WORKER_STATE_CONFIGURED;
            case RUNNING   -> com.jmeternext.worker.proto.WorkerState.WORKER_STATE_RUNNING;
            case STOPPING  -> com.jmeternext.worker.proto.WorkerState.WORKER_STATE_STOPPING;
            case ERROR     -> com.jmeternext.worker.proto.WorkerState.WORKER_STATE_ERROR;
        };
    }
}
