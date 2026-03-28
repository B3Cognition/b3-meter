package com.jmeternext.engine.service.output;

import com.jmeternext.engine.service.SampleBucket;
import com.jmeternext.engine.service.SampleBucketConsumer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Collects {@link SampleBucket} events and fans them out to registered
 * {@link MetricsOutput} backends.
 *
 * <p>Implements {@link SampleBucketConsumer} so it can be subscribed to a
 * {@link com.jmeternext.engine.service.SampleStreamBroker}. Incoming buckets
 * are collected into a 1-second batch; a background scheduler flushes the
 * batch to all outputs once per second.
 *
 * <p>Error isolation: if one output throws during {@code writeSamples}, the
 * exception is logged and the remaining outputs still receive the batch.
 *
 * <p>Uses {@link ReentrantLock} instead of {@code synchronized} to avoid
 * virtual-thread pinning (JEP-491).
 */
public final class MetricsOutputManager implements SampleBucketConsumer, AutoCloseable {

    private static final Logger LOG = Logger.getLogger(MetricsOutputManager.class.getName());

    private final List<MetricsOutput> outputs = new CopyOnWriteArrayList<>();
    private final ReentrantLock bufferLock = new ReentrantLock();
    private List<SampleBucket> buffer = new ArrayList<>();
    private volatile ScheduledExecutorService scheduler;
    private volatile boolean started = false;

    /**
     * Registers a metrics output backend.
     *
     * <p>Must be called before {@link #start}. Adding an output after start
     * is allowed but the output's {@link MetricsOutput#start} will not be
     * called automatically — the caller is responsible for starting it.
     *
     * @param output the output to register; must not be {@code null}
     * @throws NullPointerException if {@code output} is {@code null}
     */
    public void addOutput(MetricsOutput output) {
        Objects.requireNonNull(output, "output must not be null");
        outputs.add(output);
        LOG.fine(() -> "Registered metrics output: " + output.name());
    }

    /**
     * Starts all registered outputs and begins the 1-second flush cycle.
     *
     * @param runId  the test-run identifier; must not be {@code null}
     * @param config shared configuration map; must not be {@code null}
     */
    public void start(String runId, Map<String, String> config) {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(config, "config must not be null");

        for (MetricsOutput output : outputs) {
            try {
                output.start(runId, config);
                LOG.info(() -> "Started metrics output: " + output.name());
            } catch (RuntimeException ex) {
                LOG.log(Level.SEVERE, "Failed to start metrics output: " + output.name(), ex);
            }
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "metrics-flush");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::flush, 1, 1, TimeUnit.SECONDS);
        started = true;
        LOG.info(() -> "MetricsOutputManager started with " + outputs.size() + " output(s) for run " + runId);
    }

    /**
     * Receives a single bucket from the broker.
     *
     * <p>Adds the bucket to the internal buffer. The buffer is drained by the
     * scheduled flush task every second.
     *
     * @param bucket the completed bucket; never {@code null}
     */
    @Override
    public void onBucket(SampleBucket bucket) {
        Objects.requireNonNull(bucket, "bucket must not be null");
        bufferLock.lock();
        try {
            buffer.add(bucket);
        } finally {
            bufferLock.unlock();
        }
    }

    /**
     * Drains the buffer and writes the batch to all outputs.
     */
    private void flush() {
        List<SampleBucket> batch;
        bufferLock.lock();
        try {
            if (buffer.isEmpty()) {
                return;
            }
            batch = buffer;
            buffer = new ArrayList<>();
        } finally {
            bufferLock.unlock();
        }

        List<SampleBucket> immutableBatch = List.copyOf(batch);
        for (MetricsOutput output : outputs) {
            try {
                output.writeSamples(immutableBatch);
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING,
                        "Metrics output '" + output.name() + "' failed to write samples", ex);
            }
        }
    }

    /**
     * Stops the flush scheduler, performs a final flush, and stops all outputs.
     *
     * <p>Safe to call multiple times; subsequent calls are no-ops.
     */
    public void stop() {
        if (!started) {
            return;
        }
        started = false;

        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException ex) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Final flush to ensure no data is lost
        flush();

        for (MetricsOutput output : outputs) {
            try {
                output.stop();
                LOG.info(() -> "Stopped metrics output: " + output.name());
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "Failed to stop metrics output: " + output.name(), ex);
            }
        }
    }

    /**
     * Returns an unmodifiable view of the registered outputs.
     *
     * @return the list of registered outputs
     */
    public List<MetricsOutput> getOutputs() {
        return List.copyOf(outputs);
    }

    /**
     * Delegates to {@link #stop()}.
     */
    @Override
    public void close() {
        stop();
    }
}
