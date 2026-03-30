package com.jmeternext.engine.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link TestRunResult}.
 */
class TestRunResultTest {

    private static final Instant START = Instant.parse("2026-03-25T10:00:00Z");
    private static final Instant END   = Instant.parse("2026-03-25T10:01:00Z");

    // -------------------------------------------------------------------------
    // Construction — happy path
    // -------------------------------------------------------------------------

    @Test
    void constructsWithValidFields() {
        TestRunResult result = new TestRunResult(
                "run-1", TestRunContext.TestRunStatus.STOPPED,
                START, END, 1000L, 50L, Duration.ofMinutes(1));

        assertEquals("run-1", result.runId());
        assertEquals(TestRunContext.TestRunStatus.STOPPED, result.finalStatus());
        assertEquals(START, result.startedAt());
        assertEquals(END, result.endedAt());
        assertEquals(1000L, result.totalSamples());
        assertEquals(50L, result.errorCount());
        assertEquals(Duration.ofMinutes(1), result.elapsed());
    }

    // -------------------------------------------------------------------------
    // Null guards
    // -------------------------------------------------------------------------

    @Test
    void throwsOnNullRunId() {
        assertThrows(NullPointerException.class, () ->
                new TestRunResult(null, TestRunContext.TestRunStatus.STOPPED,
                        START, END, 100L, 0L, Duration.ofSeconds(60)));
    }

    @Test
    void throwsOnNullFinalStatus() {
        assertThrows(NullPointerException.class, () ->
                new TestRunResult("run-1", null,
                        START, END, 100L, 0L, Duration.ofSeconds(60)));
    }

    @Test
    void throwsOnNullStartedAt() {
        assertThrows(NullPointerException.class, () ->
                new TestRunResult("run-1", TestRunContext.TestRunStatus.STOPPED,
                        null, END, 100L, 0L, Duration.ofSeconds(60)));
    }

    @Test
    void throwsOnNullEndedAt() {
        assertThrows(NullPointerException.class, () ->
                new TestRunResult("run-1", TestRunContext.TestRunStatus.STOPPED,
                        START, null, 100L, 0L, Duration.ofSeconds(60)));
    }

    @Test
    void throwsOnNullElapsed() {
        assertThrows(NullPointerException.class, () ->
                new TestRunResult("run-1", TestRunContext.TestRunStatus.STOPPED,
                        START, END, 100L, 0L, null));
    }

    // -------------------------------------------------------------------------
    // Constraint guards
    // -------------------------------------------------------------------------

    @Test
    void throwsOnNegativeTotalSamples() {
        assertThrows(IllegalArgumentException.class, () ->
                new TestRunResult("run-1", TestRunContext.TestRunStatus.ERROR,
                        START, END, -1L, 0L, Duration.ofSeconds(1)));
    }

    @Test
    void throwsOnNegativeErrorCount() {
        assertThrows(IllegalArgumentException.class, () ->
                new TestRunResult("run-1", TestRunContext.TestRunStatus.ERROR,
                        START, END, 100L, -1L, Duration.ofSeconds(1)));
    }

    @Test
    void throwsWhenErrorCountExceedsTotalSamples() {
        assertThrows(IllegalArgumentException.class, () ->
                new TestRunResult("run-1", TestRunContext.TestRunStatus.ERROR,
                        START, END, 10L, 11L, Duration.ofSeconds(1)));
    }

    @Test
    void throwsOnNegativeElapsed() {
        assertThrows(IllegalArgumentException.class, () ->
                new TestRunResult("run-1", TestRunContext.TestRunStatus.STOPPED,
                        START, END, 100L, 0L, Duration.ofSeconds(-1)));
    }

    // -------------------------------------------------------------------------
    // errorPercent
    // -------------------------------------------------------------------------

    @Test
    void errorPercentIsZeroWhenNoSamples() {
        TestRunResult result = new TestRunResult(
                "run-1", TestRunContext.TestRunStatus.STOPPED,
                START, END, 0L, 0L, Duration.ofSeconds(0));

        assertEquals(0.0, result.errorPercent(), 1e-9);
    }

    @Test
    void errorPercentComputedCorrectly() {
        TestRunResult result = new TestRunResult(
                "run-1", TestRunContext.TestRunStatus.STOPPED,
                START, END, 200L, 50L, Duration.ofSeconds(10));

        assertEquals(25.0, result.errorPercent(), 1e-9);
    }

    @Test
    void errorPercentIsHundredWhenAllErrored() {
        TestRunResult result = new TestRunResult(
                "run-1", TestRunContext.TestRunStatus.ERROR,
                START, END, 10L, 10L, Duration.ofSeconds(1));

        assertEquals(100.0, result.errorPercent(), 1e-9);
    }

    // -------------------------------------------------------------------------
    // Zero samples with zero errors is valid (run started but nothing executed)
    // -------------------------------------------------------------------------

    @Test
    void zeroSamplesZeroErrorsIsValid() {
        assertDoesNotThrow(() ->
                new TestRunResult("run-1", TestRunContext.TestRunStatus.STOPPED,
                        START, END, 0L, 0L, Duration.ZERO));
    }
}
