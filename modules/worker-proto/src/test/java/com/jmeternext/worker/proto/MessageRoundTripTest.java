package com.jmeternext.worker.proto;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip serialization tests for every message type defined in worker.proto.
 *
 * <p>Each test:
 * <ol>
 *   <li>Builds a message with representative field values.</li>
 *   <li>Serialises it to bytes via {@code toByteArray()}.</li>
 *   <li>Deserialises it back via the generated {@code parseFrom()} method.</li>
 *   <li>Asserts equality between the original and the parsed instance.</li>
 * </ol>
 */
class MessageRoundTripTest {

    // ------------------------------------------------------------------
    // Configure phase
    // ------------------------------------------------------------------

    @Test
    void testPlanMessage_roundTrip() throws InvalidProtocolBufferException {
        TestPlanMessage original = TestPlanMessage.newBuilder()
                .setRunId("run-abc-001")
                .setPlanContent(com.google.protobuf.ByteString.copyFromUtf8("<jmeterTestPlan/>"))
                .setVirtualUsers(50)
                .setDurationSeconds(300L)
                .build();

        byte[] bytes = original.toByteArray();
        TestPlanMessage parsed = TestPlanMessage.parseFrom(bytes);

        assertEquals(original, parsed);
        assertEquals("run-abc-001", parsed.getRunId());
        assertEquals(50, parsed.getVirtualUsers());
        assertEquals(300L, parsed.getDurationSeconds());
    }

    @Test
    void configureAck_accepted_roundTrip() throws InvalidProtocolBufferException {
        ConfigureAck original = ConfigureAck.newBuilder()
                .setAccepted(true)
                .setMessage("Plan validated successfully")
                .build();

        ConfigureAck parsed = ConfigureAck.parseFrom(original.toByteArray());

        assertEquals(original, parsed);
        assertTrue(parsed.getAccepted());
    }

    @Test
    void configureAck_rejected_roundTrip() throws InvalidProtocolBufferException {
        ConfigureAck original = ConfigureAck.newBuilder()
                .setAccepted(false)
                .setMessage("Invalid JMX: missing ThreadGroup")
                .build();

        ConfigureAck parsed = ConfigureAck.parseFrom(original.toByteArray());

        assertEquals(original, parsed);
        assertFalse(parsed.getAccepted());
        assertEquals("Invalid JMX: missing ThreadGroup", parsed.getMessage());
    }

    // ------------------------------------------------------------------
    // Start phase
    // ------------------------------------------------------------------

    @Test
    void startMessage_withStartAt_roundTrip() throws InvalidProtocolBufferException {
        Timestamp startAt = Timestamp.newBuilder()
                .setSeconds(1_740_000_000L)
                .setNanos(500_000_000)
                .build();

        StartMessage original = StartMessage.newBuilder()
                .setRunId("run-abc-001")
                .setStartAt(startAt)
                .build();

        StartMessage parsed = StartMessage.parseFrom(original.toByteArray());

        assertEquals(original, parsed);
        assertEquals("run-abc-001", parsed.getRunId());
        assertEquals(startAt, parsed.getStartAt());
    }

    @Test
    void startMessage_withoutStartAt_roundTrip() throws InvalidProtocolBufferException {
        StartMessage original = StartMessage.newBuilder()
                .setRunId("run-immediate")
                .build();

        StartMessage parsed = StartMessage.parseFrom(original.toByteArray());

        assertEquals(original, parsed);
        assertFalse(parsed.hasStartAt(), "start_at should be absent when not set");
    }

    @Test
    void startAck_roundTrip() throws InvalidProtocolBufferException {
        StartAck original = StartAck.newBuilder()
                .setAccepted(true)
                .setMessage("Run started")
                .build();

        StartAck parsed = StartAck.parseFrom(original.toByteArray());

        assertEquals(original, parsed);
        assertTrue(parsed.getAccepted());
    }

    // ------------------------------------------------------------------
    // Stop phase
    // ------------------------------------------------------------------

    @Test
    void stopMessage_roundTrip() throws InvalidProtocolBufferException {
        StopMessage original = StopMessage.newBuilder()
                .setRunId("run-abc-001")
                .build();

        StopMessage parsed = StopMessage.parseFrom(original.toByteArray());

        assertEquals(original, parsed);
        assertEquals("run-abc-001", parsed.getRunId());
    }

    @Test
    void stopAck_roundTrip() throws InvalidProtocolBufferException {
        StopAck original = StopAck.newBuilder()
                .setAccepted(true)
                .setMessage("Stop acknowledged")
                .build();

        StopAck parsed = StopAck.parseFrom(original.toByteArray());

        assertEquals(original, parsed);
        assertTrue(parsed.getAccepted());
    }

    // ------------------------------------------------------------------
    // Streaming results
    // ------------------------------------------------------------------

    @Test
    void sampleResultBatch_withPercentiles_roundTrip() throws InvalidProtocolBufferException {
        Timestamp ts = Timestamp.newBuilder().setSeconds(1_740_000_100L).build();

        SampleResultBatch original = SampleResultBatch.newBuilder()
                .setTimestamp(ts)
                .setSamplerLabel("HTTP Request – login")
                .setSampleCount(200L)
                .setErrorCount(3L)
                .setAvgResponseTime(145.7)
                .putPercentiles("p50", 120.0)
                .putPercentiles("p90", 200.0)
                .putPercentiles("p95", 280.0)
                .putPercentiles("p99", 450.0)
                .build();

        SampleResultBatch parsed = SampleResultBatch.parseFrom(original.toByteArray());

        assertEquals(original, parsed);
        assertEquals("HTTP Request – login", parsed.getSamplerLabel());
        assertEquals(200L, parsed.getSampleCount());
        assertEquals(3L, parsed.getErrorCount());
        assertEquals(145.7, parsed.getAvgResponseTime(), 0.001);
        assertEquals(4, parsed.getPercentilesCount());
        assertEquals(120.0, parsed.getPercentilesOrThrow("p50"), 0.001);
        assertEquals(200.0, parsed.getPercentilesOrThrow("p90"), 0.001);
        assertEquals(280.0, parsed.getPercentilesOrThrow("p95"), 0.001);
        assertEquals(450.0, parsed.getPercentilesOrThrow("p99"), 0.001);
    }

    @Test
    void sampleResultBatch_empty_roundTrip() throws InvalidProtocolBufferException {
        SampleResultBatch original = SampleResultBatch.newBuilder()
                .setSamplerLabel("idle-sampler")
                .setSampleCount(0L)
                .setErrorCount(0L)
                .setAvgResponseTime(0.0)
                .build();

        SampleResultBatch parsed = SampleResultBatch.parseFrom(original.toByteArray());

        assertEquals(original, parsed);
        assertEquals(0, parsed.getPercentilesCount());
    }

    // ------------------------------------------------------------------
    // Health
    // ------------------------------------------------------------------

    @Test
    void healthRequest_empty_roundTrip() throws InvalidProtocolBufferException {
        HealthRequest original = HealthRequest.newBuilder().build();
        HealthRequest parsed = HealthRequest.parseFrom(original.toByteArray());
        assertEquals(original, parsed);
    }

    @Test
    void healthRequest_withRunId_roundTrip() throws InvalidProtocolBufferException {
        HealthRequest original = HealthRequest.newBuilder()
                .setRunId("run-abc-001")
                .build();

        HealthRequest parsed = HealthRequest.parseFrom(original.toByteArray());

        assertEquals(original, parsed);
        assertEquals("run-abc-001", parsed.getRunId());
    }

    @Test
    void healthStatus_idle_roundTrip() throws InvalidProtocolBufferException {
        HealthStatus original = HealthStatus.newBuilder()
                .setState(WorkerState.WORKER_STATE_IDLE)
                .setMessage("Worker is ready")
                .setActiveRunId("")
                .setTimestampMs(1_740_000_000_000L)
                .build();

        HealthStatus parsed = HealthStatus.parseFrom(original.toByteArray());

        assertEquals(original, parsed);
        assertEquals(WorkerState.WORKER_STATE_IDLE, parsed.getState());
    }

    @Test
    void healthStatus_running_roundTrip() throws InvalidProtocolBufferException {
        HealthStatus original = HealthStatus.newBuilder()
                .setState(WorkerState.WORKER_STATE_RUNNING)
                .setMessage("Executing test")
                .setActiveRunId("run-abc-001")
                .setTimestampMs(1_740_000_050_000L)
                .build();

        HealthStatus parsed = HealthStatus.parseFrom(original.toByteArray());

        assertEquals(original, parsed);
        assertEquals(WorkerState.WORKER_STATE_RUNNING, parsed.getState());
        assertEquals("run-abc-001", parsed.getActiveRunId());
    }

    @Test
    void healthStatus_error_roundTrip() throws InvalidProtocolBufferException {
        HealthStatus original = HealthStatus.newBuilder()
                .setState(WorkerState.WORKER_STATE_ERROR)
                .setMessage("OutOfMemoryError during test execution")
                .setTimestampMs(1_740_000_999_000L)
                .build();

        HealthStatus parsed = HealthStatus.parseFrom(original.toByteArray());

        assertEquals(original, parsed);
        assertEquals(WorkerState.WORKER_STATE_ERROR, parsed.getState());
    }

    // ------------------------------------------------------------------
    // WorkerState enum exhaustive check
    // ------------------------------------------------------------------

    @Test
    void workerState_allValues_haveDistinctNumbers() {
        WorkerState[] values = WorkerState.values();
        // Exclude the UNRECOGNIZED sentinel that protobuf appends
        long distinct = java.util.Arrays.stream(values)
                .filter(s -> s != WorkerState.UNRECOGNIZED)
                .mapToInt(WorkerState::getNumber)
                .distinct()
                .count();
        // UNSPECIFIED(0), IDLE(1), CONFIGURED(2), RUNNING(3), STOPPING(4), ERROR(5)
        assertEquals(6, distinct, "Expected 6 distinct WorkerState values");
    }
}
