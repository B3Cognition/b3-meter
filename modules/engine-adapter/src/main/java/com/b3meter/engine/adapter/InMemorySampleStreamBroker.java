package com.jmeternext.engine.adapter;

import com.jmeternext.engine.service.SampleBucket;
import com.jmeternext.engine.service.SampleBucketConsumer;
import com.jmeternext.engine.service.SampleStreamBroker;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * In-memory implementation of {@link SampleStreamBroker} backed by a
 * {@link CopyOnWriteArrayList} of consumers per run identifier.
 *
 * <p>This implementation is appropriate for single-node deployments where the engine
 * and web-api layers share the same JVM. For distributed deployments a broker backed
 * by a message bus (e.g. Redis Streams, Kafka) would replace this.
 *
 * <p>Thread-safety:
 * <ul>
 *   <li>The outer map is a {@link ConcurrentHashMap} — concurrent {@link #subscribe},
 *       {@link #unsubscribe}, and {@link #publish} calls from different threads are safe.</li>
 *   <li>Each consumer list is a {@link CopyOnWriteArrayList} — mutations (subscribe/
 *       unsubscribe) are thread-safe and iteration during {@link #publish} is always over
 *       a stable snapshot.</li>
 * </ul>
 *
 * <p>Fan-out contract: if a consumer throws a {@link RuntimeException} during delivery,
 * the exception is logged at WARNING level and delivery continues to the remaining
 * consumers. One failing consumer does not disrupt others.
 */
public final class InMemorySampleStreamBroker implements SampleStreamBroker {

    private static final Logger LOG = Logger.getLogger(InMemorySampleStreamBroker.class.getName());

    /** Map from runId to the list of subscribed consumers for that run. */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SampleBucketConsumer>> consumersByRun =
            new ConcurrentHashMap<>();

    /**
     * Creates a new, empty broker with no subscriptions.
     */
    public InMemorySampleStreamBroker() {
        // no initialisation needed
    }

    @Override
    public void subscribe(String runId, SampleBucketConsumer consumer) {
        Objects.requireNonNull(runId,    "runId must not be null");
        Objects.requireNonNull(consumer, "consumer must not be null");

        consumersByRun
                .computeIfAbsent(runId, ignored -> new CopyOnWriteArrayList<>())
                .addIfAbsent(consumer);
    }

    @Override
    public void unsubscribe(String runId, SampleBucketConsumer consumer) {
        Objects.requireNonNull(runId,    "runId must not be null");
        Objects.requireNonNull(consumer, "consumer must not be null");

        List<SampleBucketConsumer> consumers = consumersByRun.get(runId);
        if (consumers != null) {
            consumers.remove(consumer);
        }
    }

    @Override
    public void publish(String runId, SampleBucket bucket) {
        Objects.requireNonNull(runId,  "runId must not be null");
        Objects.requireNonNull(bucket, "bucket must not be null");

        List<SampleBucketConsumer> consumers = consumersByRun.get(runId);
        if (consumers == null || consumers.isEmpty()) {
            return;
        }

        // Iterate over a snapshot — CopyOnWriteArrayList.iterator() is snapshot-based
        for (SampleBucketConsumer consumer : consumers) {
            try {
                consumer.onBucket(bucket);
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING,
                        "SampleBucketConsumer threw an exception for runId={0}; delivery continues",
                        new Object[]{runId});
                LOG.log(Level.WARNING, "Consumer exception detail", ex);
            }
        }
    }

    /**
     * Returns the number of consumers currently subscribed for {@code runId}.
     *
     * <p>Intended for testing and monitoring; not part of the {@link SampleStreamBroker} contract.
     *
     * @param runId the run to query; must not be {@code null}
     * @return number of subscribed consumers; 0 if no consumers or run not known
     */
    public int subscriberCount(String runId) {
        Objects.requireNonNull(runId, "runId must not be null");
        List<SampleBucketConsumer> consumers = consumersByRun.get(runId);
        return consumers == null ? 0 : consumers.size();
    }
}
