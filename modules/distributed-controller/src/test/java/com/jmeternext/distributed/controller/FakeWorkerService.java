package com.jmeternext.distributed.controller;

import com.google.protobuf.Timestamp;
import com.jmeternext.worker.proto.ConfigureAck;
import com.jmeternext.worker.proto.HealthRequest;
import com.jmeternext.worker.proto.HealthStatus;
import com.jmeternext.worker.proto.SampleResultBatch;
import com.jmeternext.worker.proto.StartAck;
import com.jmeternext.worker.proto.StartMessage;
import com.jmeternext.worker.proto.StopAck;
import com.jmeternext.worker.proto.StopMessage;
import com.jmeternext.worker.proto.TestPlanMessage;
import com.jmeternext.worker.proto.WorkerServiceGrpc;
import com.jmeternext.worker.proto.WorkerState;
import io.grpc.stub.StreamObserver;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Self-contained in-process fake {@link WorkerServiceGrpc.WorkerServiceImplBase} used
 * only in the distributed-controller test suite.
 *
 * <p>This avoids a runtime test dependency on the {@code worker-node} module. The fake
 * emits synthetic {@link SampleResultBatch} messages every 3 seconds while RUNNING,
 * mirrors the real worker state machine, and responds correctly to all RPCs.
 */
class FakeWorkerService extends WorkerServiceGrpc.WorkerServiceImplBase {

    enum State { IDLE, CONFIGURED, RUNNING, STOPPING }

    /** Samples per synthetic batch (matches WorkerServiceImpl). */
    static final long SAMPLES_PER_BATCH = 10L;
    static final long EMIT_INTERVAL_MS = 3_000L;

    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);
    private volatile String activeRunId;
    private volatile StreamObserver<SampleResultBatch> resultObserver;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "fake-worker-emitter");
                t.setDaemon(true);
                return t;
            });
    private volatile ScheduledFuture<?> emitTask;

    @Override
    public void configure(TestPlanMessage request, StreamObserver<ConfigureAck> responseObserver) {
        if (state.get() != State.IDLE) {
            responseObserver.onNext(ConfigureAck.newBuilder()
                    .setAccepted(false).setMessage("not idle").build());
            responseObserver.onCompleted();
            return;
        }
        activeRunId = request.getRunId();
        state.set(State.CONFIGURED);
        responseObserver.onNext(ConfigureAck.newBuilder()
                .setAccepted(true).setMessage("ok").build());
        responseObserver.onCompleted();
    }

    @Override
    public void start(StartMessage request, StreamObserver<StartAck> responseObserver) {
        if (state.get() != State.CONFIGURED) {
            responseObserver.onNext(StartAck.newBuilder()
                    .setAccepted(false).setMessage("not configured").build());
            responseObserver.onCompleted();
            return;
        }
        state.set(State.RUNNING);
        final String runId = request.getRunId();
        emitTask = scheduler.scheduleAtFixedRate(
                () -> emitBatch(runId), EMIT_INTERVAL_MS, EMIT_INTERVAL_MS, TimeUnit.MILLISECONDS);
        responseObserver.onNext(StartAck.newBuilder()
                .setAccepted(true).setMessage("started").build());
        responseObserver.onCompleted();
    }

    @Override
    public void stop(StopMessage request, StreamObserver<StopAck> responseObserver) {
        cancelEmit();
        state.set(State.STOPPING);
        scheduler.schedule(() -> finishRun(request.getRunId()),
                EMIT_INTERVAL_MS, TimeUnit.MILLISECONDS);
        responseObserver.onNext(StopAck.newBuilder().setAccepted(true).build());
        responseObserver.onCompleted();
    }

    @Override
    public void stopNow(StopMessage request, StreamObserver<StopAck> responseObserver) {
        cancelEmit();
        finishRun(request.getRunId());
        responseObserver.onNext(StopAck.newBuilder().setAccepted(true).build());
        responseObserver.onCompleted();
    }

    @Override
    public void streamResults(StopMessage request,
                               StreamObserver<SampleResultBatch> responseObserver) {
        this.resultObserver = responseObserver;
    }

    @Override
    public void getHealth(HealthRequest request, StreamObserver<HealthStatus> responseObserver) {
        State s = state.get();
        WorkerState proto = switch (s) {
            case IDLE      -> WorkerState.WORKER_STATE_IDLE;
            case CONFIGURED -> WorkerState.WORKER_STATE_CONFIGURED;
            case RUNNING   -> WorkerState.WORKER_STATE_RUNNING;
            case STOPPING  -> WorkerState.WORKER_STATE_STOPPING;
        };
        responseObserver.onNext(HealthStatus.newBuilder()
                .setState(proto)
                .setMessage(s.name().toLowerCase())
                .setActiveRunId(activeRunId != null ? activeRunId : "")
                .setTimestampMs(System.currentTimeMillis())
                .build());
        responseObserver.onCompleted();
    }

    State currentState() { return state.get(); }

    void shutdown() { scheduler.shutdownNow(); }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void emitBatch(String runId) {
        if (state.get() != State.RUNNING) return;
        StreamObserver<SampleResultBatch> obs = resultObserver;
        if (obs == null) return;
        Instant now = Instant.now();
        SampleResultBatch batch = SampleResultBatch.newBuilder()
                .setTimestamp(Timestamp.newBuilder()
                        .setSeconds(now.getEpochSecond())
                        .setNanos(now.getNano())
                        .build())
                .setSamplerLabel("HTTP Request")
                .setSampleCount(SAMPLES_PER_BATCH)
                .setErrorCount(0L)
                .setAvgResponseTime(125.0)
                .putPercentiles("p90", 220.0)
                .putPercentiles("p95", 270.0)
                .putPercentiles("p99", 295.0)
                .build();
        try {
            obs.onNext(batch);
        } catch (Exception ignored) {}
    }

    private void cancelEmit() {
        ScheduledFuture<?> task = emitTask;
        if (task != null) {
            task.cancel(false);
            emitTask = null;
        }
    }

    private void finishRun(String runId) {
        state.set(State.IDLE);
        activeRunId = null;
        StreamObserver<SampleResultBatch> obs = resultObserver;
        resultObserver = null;
        if (obs != null) {
            try { obs.onCompleted(); } catch (Exception ignored) {}
        }
    }
}
