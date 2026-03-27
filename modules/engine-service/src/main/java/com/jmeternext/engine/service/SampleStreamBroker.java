package com.jmeternext.engine.service;

/**
 * Fan-out broker for 1-second sample aggregation buckets.
 *
 * <p>The broker decouples the engine (which produces raw samples) from UI/reporting
 * consumers (SSE publisher, SLA evaluator, metrics collector). Each consumer registers
 * a {@link SampleBucketConsumer} callback for a specific run; the broker delivers each
 * published bucket to all registered consumers for that run.
 *
 * <p>This interface deliberately avoids reactive types (Reactor Flux, RxJava Observable)
 * to keep the engine-service module framework-free. Reactive wrappers can be built on top
 * of this callback interface in the web-api layer.
 *
 * <p>All methods on this interface must be thread-safe.
 */
public interface SampleStreamBroker {

    /**
     * Registers a consumer to receive buckets for the given run.
     *
     * <p>Registering the same {@code consumer} instance twice for the same {@code runId}
     * is a no-op (idempotent). The consumer will receive each published bucket exactly once
     * per unique registration.
     *
     * @param runId    the run identifier to subscribe to; must not be {@code null}
     * @param consumer the callback to invoke; must not be {@code null}
     * @throws NullPointerException if {@code runId} or {@code consumer} is {@code null}
     */
    void subscribe(String runId, SampleBucketConsumer consumer);

    /**
     * Removes a previously registered consumer for the given run.
     *
     * <p>If the consumer was not registered, this method is a no-op.
     *
     * @param runId    the run identifier to unsubscribe from; must not be {@code null}
     * @param consumer the callback to remove; must not be {@code null}
     * @throws NullPointerException if {@code runId} or {@code consumer} is {@code null}
     */
    void unsubscribe(String runId, SampleBucketConsumer consumer);

    /**
     * Delivers {@code bucket} to all consumers currently subscribed to {@code runId}.
     *
     * <p>Delivery is synchronous: all consumer callbacks are invoked before this method
     * returns. If a consumer throws a {@link RuntimeException}, the broker must log the
     * exception and continue delivering to the remaining consumers — one failing consumer
     * must not prevent others from receiving the bucket.
     *
     * @param runId  the run identifier whose consumers should receive the bucket; must not be {@code null}
     * @param bucket the completed aggregation bucket; must not be {@code null}
     * @throws NullPointerException if {@code runId} or {@code bucket} is {@code null}
     */
    void publish(String runId, SampleBucket bucket);
}
