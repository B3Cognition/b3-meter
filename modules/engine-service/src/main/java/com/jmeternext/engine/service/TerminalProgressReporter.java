package com.jmeternext.engine.service;

import java.io.PrintStream;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Prints a live progress line to the terminal every time a {@link SampleBucket}
 * is received, plus a summary table when the test completes.
 *
 * <p>Features:
 * <ul>
 *   <li>Uses {@code \r} (carriage return) to overwrite the line in TTY mode.</li>
 *   <li>Detects {@code System.console() != null} for TTY — falls back to
 *       line-per-second if piped.</li>
 *   <li>Respects {@code NO_COLOR} env var (suppresses ANSI codes).</li>
 *   <li>Accumulates stats across all {@link SampleBucket}s received.</li>
 *   <li>Thread-safe (uses {@link AtomicLong} counters).</li>
 *   <li>Pure JDK — no dependencies.</li>
 * </ul>
 *
 * <p>Progress line format:
 * <pre>{@code [00:05] VUs=50 | RPS=1,234 | p95=42ms | Errors=0 (0.0%) | 5s/60s}</pre>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class TerminalProgressReporter implements SampleBucketConsumer {

    private final PrintStream out;
    private final boolean isTty;
    private final boolean noColor;
    private final long plannedDurationSeconds;

    // Accumulated counters (thread-safe)
    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong totalErrors = new AtomicLong();
    private final AtomicLong totalResponseTimeMs = new AtomicLong();
    private final AtomicLong bucketCount = new AtomicLong();
    private final AtomicLong maxP95 = new AtomicLong();
    private final AtomicLong maxP99 = new AtomicLong();
    private final AtomicLong latestRps = new AtomicLong();
    private final AtomicLong latestVUs = new AtomicLong();
    private final AtomicLong elapsedSeconds = new AtomicLong();

    // For running min/max tracking
    private final AtomicLong minResponseTime = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxResponseTime = new AtomicLong(0);

    /**
     * Creates a new reporter.
     *
     * @param out                    output stream (usually {@code System.out})
     * @param plannedDurationSeconds total planned test duration in seconds (used in progress display)
     */
    public TerminalProgressReporter(PrintStream out, long plannedDurationSeconds) {
        this.out = out;
        this.isTty = System.console() != null;
        this.noColor = System.getenv("NO_COLOR") != null;
        this.plannedDurationSeconds = plannedDurationSeconds;
    }

    /**
     * Creates a reporter writing to {@code System.out}.
     *
     * @param plannedDurationSeconds total planned test duration in seconds
     */
    public TerminalProgressReporter(long plannedDurationSeconds) {
        this(System.out, plannedDurationSeconds);
    }

    @Override
    public void onBucket(SampleBucket bucket) {
        // Accumulate stats
        totalRequests.addAndGet(bucket.sampleCount());
        totalErrors.addAndGet(bucket.errorCount());
        totalResponseTimeMs.addAndGet((long) (bucket.avgResponseTime() * bucket.sampleCount()));
        bucketCount.incrementAndGet();

        // Track percentiles (keep the max seen across all buckets)
        updateMax(maxP95, (long) bucket.percentile95());
        updateMax(maxP99, (long) bucket.percentile99());

        // Track min/max response time
        updateMin(minResponseTime, (long) bucket.minResponseTime());
        updateMax(maxResponseTime, (long) bucket.maxResponseTime());

        // Latest instantaneous values
        latestRps.set((long) bucket.samplesPerSecond());

        long elapsed = elapsedSeconds.incrementAndGet();

        // Print progress line
        printProgressLine(elapsed, (long) bucket.samplesPerSecond(),
                (long) bucket.percentile95(), bucket.errorCount(), bucket.sampleCount());
    }

    /**
     * Prints the final summary table. Call this after the test completes.
     */
    public void printSummary() {
        long total = totalRequests.get();
        long errors = totalErrors.get();
        long elapsed = elapsedSeconds.get();
        long avgRps = elapsed > 0 ? total / elapsed : 0;
        long avgRt = total > 0 ? totalResponseTimeMs.get() / total : 0;
        long p95 = maxP95.get();
        long p99 = maxP99.get();
        double errorRate = total > 0 ? (double) errors / total * 100.0 : 0.0;

        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);

        if (isTty) {
            out.println(); // move past the \r line
        }

        String separator = "============================================================";
        out.println(separator);
        out.println("jMeter Next Test Summary");
        out.println(separator);
        out.printf("Total Requests:    %s%n", nf.format(total));
        out.printf("Avg RPS:           %s%n", nf.format(avgRps));
        out.printf("Avg Response Time: %dms%n", avgRt);
        out.printf("p95:               %dms%n", p95);
        out.printf("p99:               %dms%n", p99);
        out.printf("Error Rate:        %.1f%% (%s/%s)%n", errorRate, nf.format(errors), nf.format(total));
        out.println(separator);
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private void printProgressLine(long elapsed, long rps, long p95,
                                    long bucketErrors, long bucketSamples) {
        String timeStr = formatTime(elapsed);
        long totalErrs = totalErrors.get();
        long totalReqs = totalRequests.get();
        double errorPct = totalReqs > 0 ? (double) totalErrs / totalReqs * 100.0 : 0.0;

        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);

        String durationPart = plannedDurationSeconds > 0
                ? String.format("%ds/%ds", elapsed, plannedDurationSeconds)
                : String.format("%ds", elapsed);

        String line = String.format("[%s] VUs=%s | RPS=%s | p95=%dms | Errors=%s (%.1f%%) | %s",
                timeStr, nf.format(latestVUs.get()), nf.format(rps), p95,
                nf.format(totalErrs), errorPct, durationPart);

        if (isTty) {
            out.print("\r" + line);
            out.flush();
        } else {
            out.println(line);
        }
    }

    /**
     * Sets the current number of active virtual users (for display purposes).
     * Called externally by the VU executor.
     *
     * @param vus current active VU count
     */
    public void setActiveVUs(long vus) {
        latestVUs.set(vus);
    }

    private static String formatTime(long totalSeconds) {
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private static void updateMax(AtomicLong holder, long newValue) {
        long current;
        do {
            current = holder.get();
            if (newValue <= current) return;
        } while (!holder.compareAndSet(current, newValue));
    }

    private static void updateMin(AtomicLong holder, long newValue) {
        long current;
        do {
            current = holder.get();
            if (newValue >= current) return;
        } while (!holder.compareAndSet(current, newValue));
    }
}
