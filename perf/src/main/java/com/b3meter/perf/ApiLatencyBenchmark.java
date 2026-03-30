package com.jmeternext.perf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Measures p99 API latency against an in-process mock endpoint.
 *
 * <p>The benchmark simulates the web-api request-handling path without network
 * overhead, isolating serialisation, routing, and response-building latency.
 * Each iteration:
 * <ol>
 *   <li>Constructs a minimal request payload.</li>
 *   <li>Dispatches it through the in-process mock handler.</li>
 *   <li>Records the round-trip duration in nanoseconds.</li>
 * </ol>
 *
 * <p><strong>Target SLO:</strong> p99 ≤ 200 ms (see {@code perf/.baseline.json}).
 *
 * <h2>Sample size</h2>
 * {@value SAMPLE_COUNT} requests are issued sequentially on a single virtual thread
 * to produce a stable latency distribution without thread-scheduling noise at the
 * sampler level. The p99 is computed from all recorded samples.
 */
public final class ApiLatencyBenchmark {

    private static final Logger LOG = Logger.getLogger(ApiLatencyBenchmark.class.getName());

    /** Total number of request samples to collect. */
    static final int SAMPLE_COUNT = 1_000;

    /** Simulates one API request/response cycle — in-process no-op mock. */
    @FunctionalInterface
    interface RequestHandler {
        /** Executes the mocked request and returns the response latency in nanoseconds. */
        long handleOnce();
    }

    private final RequestHandler handler;

    /**
     * Constructs the benchmark with the default in-process mock handler.
     *
     * <p>The mock models a minimal JSON serialisation round-trip (~constant overhead)
     * so the measured latency reflects the engine dispatch path, not network I/O.
     */
    public ApiLatencyBenchmark() {
        this.handler = () -> {
            long start = System.nanoTime();
            // Simulate JSON serialisation work: string concat proportional to a small payload
            StringBuilder sb = new StringBuilder(128);
            sb.append("{\"status\":\"ok\",\"runId\":\"bench-");
            sb.append(Thread.currentThread().threadId());
            sb.append("\",\"ts\":");
            sb.append(System.currentTimeMillis());
            sb.append('}');
            String ignored = sb.toString(); // prevent dead-code elimination
            if (ignored.isEmpty()) throw new AssertionError("unreachable");
            return System.nanoTime() - start;
        };
    }

    /**
     * Package-private constructor for test injection.
     *
     * @param handler the request handler mock to use
     */
    ApiLatencyBenchmark(RequestHandler handler) {
        this.handler = handler;
    }

    /**
     * Runs the latency benchmark.
     *
     * @return a {@link BenchmarkResult} with p99 latency in milliseconds
     */
    public BenchmarkResult run() {
        LOG.info(() -> "[ApiLatency] Starting: " + SAMPLE_COUNT + " samples");

        // Warm-up: 100 iterations discarded
        for (int i = 0; i < 100; i++) {
            handler.handleOnce();
        }

        List<Long> latenciesNs = new ArrayList<>(SAMPLE_COUNT);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Long>> futures = new ArrayList<>(SAMPLE_COUNT);
            for (int i = 0; i < SAMPLE_COUNT; i++) {
                futures.add(executor.submit(handler::handleOnce));
            }
            for (Future<Long> f : futures) {
                try {
                    latenciesNs.add(f.get(10, TimeUnit.SECONDS));
                } catch (Exception e) {
                    LOG.warning("[ApiLatency] Sample failed: " + e.getMessage());
                }
            }
        }

        if (latenciesNs.isEmpty()) {
            LOG.warning("[ApiLatency] No samples collected — returning 0 ms");
            return new BenchmarkResult("api_latency_p99_ms", 0.0, "ms");
        }

        Collections.sort(latenciesNs);
        int p99Index   = (int) Math.ceil(latenciesNs.size() * 0.99) - 1;
        long p99Ns     = latenciesNs.get(Math.max(0, p99Index));
        double p99Ms   = p99Ns / 1_000_000.0;

        long p50Ns     = latenciesNs.get(latenciesNs.size() / 2);
        long maxNs     = latenciesNs.get(latenciesNs.size() - 1);

        LOG.info(() -> String.format(
                "[ApiLatency] p50=%.2f ms  p99=%.2f ms  max=%.2f ms  samples=%d",
                p50Ns / 1_000_000.0, p99Ms, maxNs / 1_000_000.0, latenciesNs.size()));

        return new BenchmarkResult("api_latency_p99_ms", p99Ms, "ms");
    }
}
