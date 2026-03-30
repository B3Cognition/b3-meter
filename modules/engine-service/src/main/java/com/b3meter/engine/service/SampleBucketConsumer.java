package com.jmeternext.engine.service;

/**
 * Callback invoked whenever a completed {@link SampleBucket} is available.
 *
 * <p>Implementations are registered with a {@link SampleStreamBroker} for a specific
 * run identifier. The broker calls {@link #onBucket} once per aggregated 1-second
 * window for each sampler label active during that window.
 *
 * <p>Implementations must be thread-safe — the broker may invoke {@link #onBucket}
 * from any thread.
 *
 * <p>Implementations must not throw checked exceptions; any runtime exception thrown
 * by {@link #onBucket} is propagated to the caller (the broker implementation should
 * log and continue to avoid disrupting other subscribers).
 */
@FunctionalInterface
public interface SampleBucketConsumer {

    /**
     * Called when a 1-second aggregation bucket is ready.
     *
     * @param bucket the completed bucket; never {@code null}
     */
    void onBucket(SampleBucket bucket);
}
