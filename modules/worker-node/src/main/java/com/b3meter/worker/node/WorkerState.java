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
package com.b3meter.worker.node;

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
     * Returns the proto-equivalent {@link com.b3meter.worker.proto.WorkerState} for
     * this enum value.
     *
     * @return the corresponding proto WorkerState; never {@code null}
     */
    public com.b3meter.worker.proto.WorkerState toProto() {
        return switch (this) {
            case IDLE      -> com.b3meter.worker.proto.WorkerState.WORKER_STATE_IDLE;
            case CONFIGURED -> com.b3meter.worker.proto.WorkerState.WORKER_STATE_CONFIGURED;
            case RUNNING   -> com.b3meter.worker.proto.WorkerState.WORKER_STATE_RUNNING;
            case STOPPING  -> com.b3meter.worker.proto.WorkerState.WORKER_STATE_STOPPING;
            case ERROR     -> com.b3meter.worker.proto.WorkerState.WORKER_STATE_ERROR;
        };
    }
}
