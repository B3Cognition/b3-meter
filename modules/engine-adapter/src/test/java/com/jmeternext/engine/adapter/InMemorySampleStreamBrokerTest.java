package com.jmeternext.engine.adapter;

import com.jmeternext.engine.service.SampleBucket;
import com.jmeternext.engine.service.SampleBucketConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link InMemorySampleStreamBroker}.
 */
class InMemorySampleStreamBrokerTest {

    private static final Instant TS      = Instant.parse("2026-03-25T10:00:00Z");
    private static final String  RUN_ID  = "run-1";

    private InMemorySampleStreamBroker broker;

    @BeforeEach
    void setUp() {
        broker = new InMemorySampleStreamBroker();
    }

    // -------------------------------------------------------------------------
    // subscribe — basic delivery
    // -------------------------------------------------------------------------

    @Test
    void subscribedConsumerReceivesBucket() {
        List<SampleBucket> received = new ArrayList<>();
        broker.subscribe(RUN_ID, received::add);

        SampleBucket bucket = validBucket("HTTP GET");
        broker.publish(RUN_ID, bucket);

        assertEquals(1, received.size());
        assertSame(bucket, received.get(0));
    }

    @Test
    void multipleSubscribersAllReceiveBucket() {
        List<SampleBucket> sink1 = new ArrayList<>();
        List<SampleBucket> sink2 = new ArrayList<>();
        broker.subscribe(RUN_ID, sink1::add);
        broker.subscribe(RUN_ID, sink2::add);

        broker.publish(RUN_ID, validBucket("HTTP GET"));

        assertEquals(1, sink1.size());
        assertEquals(1, sink2.size());
    }

    // -------------------------------------------------------------------------
    // subscribe — idempotency
    // -------------------------------------------------------------------------

    @Test
    void subscribingSameConsumerTwiceDeliversBucketOnce() {
        List<SampleBucket> received = new ArrayList<>();
        SampleBucketConsumer consumer = received::add;

        broker.subscribe(RUN_ID, consumer);
        broker.subscribe(RUN_ID, consumer); // duplicate — must be ignored

        broker.publish(RUN_ID, validBucket("HTTP GET"));

        assertEquals(1, received.size(), "consumer should only receive the bucket once");
    }

    // -------------------------------------------------------------------------
    // unsubscribe
    // -------------------------------------------------------------------------

    @Test
    void unsubscribedConsumerNoLongerReceivesBuckets() {
        List<SampleBucket> received = new ArrayList<>();
        SampleBucketConsumer consumer = received::add;

        broker.subscribe(RUN_ID, consumer);
        broker.unsubscribe(RUN_ID, consumer);
        broker.publish(RUN_ID, validBucket("HTTP GET"));

        assertTrue(received.isEmpty(), "unsubscribed consumer must not receive buckets");
    }

    @Test
    void unsubscribingNonExistentConsumerIsNoOp() {
        assertDoesNotThrow(() -> broker.unsubscribe(RUN_ID, bucket -> {}));
    }

    @Test
    void unsubscribingFromUnknownRunIdIsNoOp() {
        assertDoesNotThrow(() -> broker.unsubscribe("unknown-run", bucket -> {}));
    }

    // -------------------------------------------------------------------------
    // publish — no consumers
    // -------------------------------------------------------------------------

    @Test
    void publishWithNoSubscribersIsNoOp() {
        assertDoesNotThrow(() -> broker.publish(RUN_ID, validBucket("HTTP GET")));
    }

    @Test
    void publishToUnknownRunIdIsNoOp() {
        List<SampleBucket> received = new ArrayList<>();
        broker.subscribe(RUN_ID, received::add);

        broker.publish("other-run", validBucket("HTTP GET"));

        assertTrue(received.isEmpty(), "consumer for run-1 must not receive bucket published to other-run");
    }

    // -------------------------------------------------------------------------
    // publish — consumer isolation: failing consumer does not disrupt others
    // -------------------------------------------------------------------------

    @Test
    void failingConsumerDoesNotPreventDeliveryToOtherConsumers() {
        AtomicInteger goodConsumerCallCount = new AtomicInteger(0);

        SampleBucketConsumer badConsumer = bucket -> {
            throw new RuntimeException("simulated consumer failure");
        };
        SampleBucketConsumer goodConsumer = bucket -> goodConsumerCallCount.incrementAndGet();

        broker.subscribe(RUN_ID, badConsumer);
        broker.subscribe(RUN_ID, goodConsumer);

        assertDoesNotThrow(() -> broker.publish(RUN_ID, validBucket("HTTP GET")));
        assertEquals(1, goodConsumerCallCount.get(), "good consumer must still receive the bucket");
    }

    // -------------------------------------------------------------------------
    // publish — multiple buckets in sequence
    // -------------------------------------------------------------------------

    @Test
    void publishingMultipleBucketsDeliversAllInOrder() {
        List<SampleBucket> received = new ArrayList<>();
        broker.subscribe(RUN_ID, received::add);

        SampleBucket b1 = validBucket("HTTP GET");
        SampleBucket b2 = validBucket("HTTP POST");
        SampleBucket b3 = validBucket("DB Query");

        broker.publish(RUN_ID, b1);
        broker.publish(RUN_ID, b2);
        broker.publish(RUN_ID, b3);

        assertEquals(3, received.size());
        assertSame(b1, received.get(0));
        assertSame(b2, received.get(1));
        assertSame(b3, received.get(2));
    }

    // -------------------------------------------------------------------------
    // Multiple runs are isolated
    // -------------------------------------------------------------------------

    @Test
    void consumersForDifferentRunsAreIsolated() {
        List<SampleBucket> run1Received = new ArrayList<>();
        List<SampleBucket> run2Received = new ArrayList<>();

        broker.subscribe("run-1", run1Received::add);
        broker.subscribe("run-2", run2Received::add);

        broker.publish("run-1", validBucket("HTTP GET"));

        assertEquals(1, run1Received.size());
        assertTrue(run2Received.isEmpty(), "run-2 consumer must not receive buckets published to run-1");
    }

    // -------------------------------------------------------------------------
    // subscriberCount (package-friendly monitoring method)
    // -------------------------------------------------------------------------

    @Test
    void subscriberCountReturnsZeroForUnknownRun() {
        assertEquals(0, broker.subscriberCount("no-such-run"));
    }

    @Test
    void subscriberCountReflectsCurrentSubscriptions() {
        SampleBucketConsumer c1 = bucket -> {};
        SampleBucketConsumer c2 = bucket -> {};

        broker.subscribe(RUN_ID, c1);
        assertEquals(1, broker.subscriberCount(RUN_ID));

        broker.subscribe(RUN_ID, c2);
        assertEquals(2, broker.subscriberCount(RUN_ID));

        broker.unsubscribe(RUN_ID, c1);
        assertEquals(1, broker.subscriberCount(RUN_ID));
    }

    // -------------------------------------------------------------------------
    // Null guards
    // -------------------------------------------------------------------------

    @Test
    void subscribeThrowsOnNullRunId() {
        assertThrows(NullPointerException.class, () -> broker.subscribe(null, bucket -> {}));
    }

    @Test
    void subscribeThrowsOnNullConsumer() {
        assertThrows(NullPointerException.class, () -> broker.subscribe(RUN_ID, null));
    }

    @Test
    void unsubscribeThrowsOnNullRunId() {
        assertThrows(NullPointerException.class, () -> broker.unsubscribe(null, bucket -> {}));
    }

    @Test
    void unsubscribeThrowsOnNullConsumer() {
        assertThrows(NullPointerException.class, () -> broker.unsubscribe(RUN_ID, null));
    }

    @Test
    void publishThrowsOnNullRunId() {
        assertThrows(NullPointerException.class, () -> broker.publish(null, validBucket("GET")));
    }

    @Test
    void publishThrowsOnNullBucket() {
        assertThrows(NullPointerException.class, () -> broker.publish(RUN_ID, null));
    }

    // -------------------------------------------------------------------------
    // Concurrency: subscribe + publish from multiple threads
    // -------------------------------------------------------------------------

    @Test
    void concurrentPublishesDeliverAllBucketsToAllSubscribers() throws Exception {
        int consumerCount = 5;
        int publishCount  = 20;

        List<List<SampleBucket>> sinks = new ArrayList<>();
        for (int i = 0; i < consumerCount; i++) {
            List<SampleBucket> sink = Collections.synchronizedList(new ArrayList<>());
            sinks.add(sink);
            broker.subscribe(RUN_ID, sink::add);
        }

        CyclicBarrier barrier = new CyclicBarrier(publishCount);
        ExecutorService pool = Executors.newFixedThreadPool(publishCount);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < publishCount; i++) {
            futures.add(pool.submit(() -> {
                try {
                    barrier.await();
                    broker.publish(RUN_ID, validBucket("HTTP GET"));
                } catch (Exception e) {
                    fail("unexpected exception: " + e.getMessage());
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        // Each consumer should have received all published buckets
        for (int i = 0; i < consumerCount; i++) {
            assertEquals(publishCount, sinks.get(i).size(),
                    "consumer " + i + " should have received all " + publishCount + " buckets");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static SampleBucket validBucket(String label) {
        return new SampleBucket(TS, label, 10L, 0L, 100.0, 50.0, 200.0, 150.0, 180.0, 195.0, 10.0);
    }
}
