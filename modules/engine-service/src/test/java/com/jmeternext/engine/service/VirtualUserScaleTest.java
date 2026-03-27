package com.jmeternext.engine.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance test for {@link VirtualUserExecutor} at scale.
 *
 * <p>Verifies that 5,000 virtual users complete within 5 seconds and that
 * the JVM platform thread count stays below 200 during execution.
 *
 * <p>Tagged {@code @Tag("performance")} so it can be run selectively via
 * {@code ./gradlew test -Pinclude.tags=performance}.
 */
@Tag("performance")
class VirtualUserScaleTest {

    /** Number of virtual users to launch concurrently. */
    private static final int VIRTUAL_USER_COUNT = 5_000;

    /** Maximum wall-clock time for all users to complete. */
    private static final long MAX_WALL_CLOCK_SECONDS = 5;

    /**
     * Maximum platform thread count allowed during the test.
     *
     * <p>Virtual threads share a small pool of carrier (platform) threads.
     * For 5,000 virtual users sleeping simultaneously, the JVM should need
     * far fewer than 200 platform threads.
     */
    private static final int MAX_PLATFORM_THREADS = 200;

    /** Simulated work duration per virtual user. */
    private static final long USER_SLEEP_MS = 1_000;

    // -------------------------------------------------------------------------
    // Minimal UIBridge stub
    // -------------------------------------------------------------------------

    private static final UIBridge NO_OP = new UIBridge() {
        @Override public void onTestStarted(TestRunContext c) {}
        @Override public void onSample(TestRunContext c, double s, double e) {}
        @Override public void onTestEnded(TestRunContext c) {}
        @Override public void onThreadStarted(TestRunContext c, String t, int n) {}
        @Override public void onThreadFinished(TestRunContext c, String t, int n) {}
        @Override public void reportError(String m, String t) {}
        @Override public void reportInfo(String m, String t) {}
        @Override public void refreshUI() {}
        @Override public String promptPassword(String m) { return null; }
        @Override public void onSampleReceived(TestRunContext c, String l, long e, boolean s) {}
    };

    private TestRunContext context;
    private VirtualUserExecutor executor;

    @BeforeEach
    void setUp() {
        context = TestRunContext.builder()
                .runId("scale-test-run")
                .planPath("/plans/scale.jmx")
                .virtualUsers(VIRTUAL_USER_COUNT)
                .uiBridge(NO_OP)
                .build();
        executor = new VirtualUserExecutor(context);
    }

    @AfterEach
    void tearDown() {
        executor.close();
    }

    // -------------------------------------------------------------------------
    // Scale test: 5,000 VUs sleeping 1 second complete in < 5 seconds
    // -------------------------------------------------------------------------

    @Test
    void fiveThousandVirtualUsersCompleteWithinFiveSeconds() throws Exception {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        CountDownLatch allDone = new CountDownLatch(VIRTUAL_USER_COUNT);
        AtomicInteger peakPlatformThreads = new AtomicInteger(0);

        long startNanos = System.nanoTime();

        for (int i = 0; i < VIRTUAL_USER_COUNT; i++) {
            executor.submitVirtualUser(() -> {
                // Sample peak platform thread count from within the virtual threads
                int currentPlatform = threadBean.getThreadCount();
                peakPlatformThreads.updateAndGet(current -> Math.max(current, currentPlatform));

                try {
                    Thread.sleep(USER_SLEEP_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    allDone.countDown();
                }
            });
        }

        boolean allCompleted = allDone.await(MAX_WALL_CLOCK_SECONDS + 2, TimeUnit.SECONDS);
        long elapsedSeconds = (System.nanoTime() - startNanos) / 1_000_000_000L;

        assertAll(
                () -> assertTrue(allCompleted,
                        VIRTUAL_USER_COUNT + " users must all complete (no timeout)"),
                () -> assertTrue(elapsedSeconds <= MAX_WALL_CLOCK_SECONDS,
                        "All users must complete within " + MAX_WALL_CLOCK_SECONDS
                                + "s — actual: " + elapsedSeconds + "s"),
                () -> assertTrue(peakPlatformThreads.get() < MAX_PLATFORM_THREADS,
                        "Peak platform thread count must be < " + MAX_PLATFORM_THREADS
                                + " — actual: " + peakPlatformThreads.get())
        );
    }

    // -------------------------------------------------------------------------
    // No OutOfMemoryError — implicit: if the test above passes without OOM the
    // criterion is met. This test validates the same scenario with explicit OOM
    // guard by catching Error and failing with a clear message.
    // -------------------------------------------------------------------------

    @Test
    void fiveThousandVirtualUsersDoNotCauseOutOfMemoryError() {
        CountDownLatch allDone = new CountDownLatch(VIRTUAL_USER_COUNT);

        assertDoesNotThrow(() -> {
            for (int i = 0; i < VIRTUAL_USER_COUNT; i++) {
                executor.submitVirtualUser(() -> {
                    try {
                        Thread.sleep(USER_SLEEP_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        allDone.countDown();
                    }
                });
            }
            allDone.await(MAX_WALL_CLOCK_SECONDS + 2, TimeUnit.SECONDS);
        }, "Submitting 5,000 virtual users must not throw any exception including OutOfMemoryError");
    }
}
