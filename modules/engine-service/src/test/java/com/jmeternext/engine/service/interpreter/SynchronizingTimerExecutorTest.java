package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SynchronizingTimerExecutor}.
 */
class SynchronizingTimerExecutorTest {

    @AfterEach
    void tearDown() {
        SynchronizingTimerExecutor.clearBarriers();
    }

    @Test
    void execute_twoThreadsSynchronize() throws Exception {
        PlanNode node = PlanNode.builder("SynchronizingTimer", "sync-2")
                .property("groupSize", 2)
                .property("timeoutInMs", "5000")
                .build();

        AtomicInteger arrivedCount = new AtomicInteger(0);
        CountDownLatch allDone = new CountDownLatch(2);

        // Thread 1
        Thread t1 = Thread.ofVirtual().start(() -> {
            SynchronizingTimerExecutor.execute(node);
            arrivedCount.incrementAndGet();
            allDone.countDown();
        });

        // Thread 2
        Thread t2 = Thread.ofVirtual().start(() -> {
            SynchronizingTimerExecutor.execute(node);
            arrivedCount.incrementAndGet();
            allDone.countDown();
        });

        assertTrue(allDone.await(5, TimeUnit.SECONDS),
                "both threads should complete within timeout");
        assertEquals(2, arrivedCount.get());
    }

    @Test
    void execute_groupSizeOne_immediatePass() {
        PlanNode node = PlanNode.builder("SynchronizingTimer", "sync-1")
                .property("groupSize", 1)
                .build();

        long start = System.currentTimeMillis();
        SynchronizingTimerExecutor.execute(node);
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 200, "groupSize=1 should pass immediately, elapsed: " + elapsed);
    }

    @Test
    void execute_groupSizeZero_defaultsToOne() {
        PlanNode node = PlanNode.builder("SynchronizingTimer", "sync-0")
                .property("groupSize", 0)
                .build();

        // groupSize 0 defaults to 1, so should pass immediately
        long start = System.currentTimeMillis();
        SynchronizingTimerExecutor.execute(node);
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 200, "groupSize=0 should default to 1 and pass immediately");
    }

    @Test
    void getOrCreateBarrier_returnsCorrectSize() {
        CyclicBarrier barrier = SynchronizingTimerExecutor.getOrCreateBarrier("test-key", 5);
        assertEquals(5, barrier.getParties());
    }

    @Test
    void execute_nullNode_throws() {
        assertThrows(NullPointerException.class,
                () -> SynchronizingTimerExecutor.execute(null));
    }
}
