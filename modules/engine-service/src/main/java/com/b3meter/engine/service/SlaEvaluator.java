/*
 * Copyright 2024-2026 b3meter Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.b3meter.engine.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Real-time SLA evaluator that checks every {@link SampleBucket} against configurable thresholds.
 *
 * <p>Subscribes to the {@link SampleStreamBroker} and records violations as they happen.
 * Violations are queryable via {@link #getViolations()} and {@link #getStatus()}.
 *
 * <p>Only JDK types — framework-free (Constitution Principle I).
 */
public class SlaEvaluator implements SampleBucketConsumer {

    private static final Logger LOG = Logger.getLogger(SlaEvaluator.class.getName());

    private final double maxP95Ms;
    private final double maxP99Ms;
    private final double maxAvgMs;
    private final double maxErrorPercent;

    private final CopyOnWriteArrayList<SlaViolation> violations = new CopyOnWriteArrayList<>();
    private final AtomicLong totalChecks = new AtomicLong();
    private final AtomicLong totalViolations = new AtomicLong();

    public SlaEvaluator(double maxP95Ms, double maxP99Ms, double maxAvgMs, double maxErrorPercent) {
        this.maxP95Ms = maxP95Ms;
        this.maxP99Ms = maxP99Ms;
        this.maxAvgMs = maxAvgMs;
        this.maxErrorPercent = maxErrorPercent;
    }

    @Override
    public void onBucket(SampleBucket bucket) {
        totalChecks.incrementAndGet();
        List<String> reasons = new ArrayList<>();

        if (maxP95Ms > 0 && bucket.percentile95() > maxP95Ms) {
            reasons.add(String.format("p95=%.0fms > %.0fms", bucket.percentile95(), maxP95Ms));
        }
        if (maxP99Ms > 0 && bucket.percentile99() > maxP99Ms) {
            reasons.add(String.format("p99=%.0fms > %.0fms", bucket.percentile99(), maxP99Ms));
        }
        if (maxAvgMs > 0 && bucket.avgResponseTime() > maxAvgMs) {
            reasons.add(String.format("avg=%.0fms > %.0fms", bucket.avgResponseTime(), maxAvgMs));
        }
        if (maxErrorPercent >= 0 && bucket.errorPercent() > maxErrorPercent) {
            reasons.add(String.format("errors=%.1f%% > %.1f%%", bucket.errorPercent(), maxErrorPercent));
        }

        if (!reasons.isEmpty()) {
            SlaViolation v = new SlaViolation(
                    Instant.now(),
                    bucket.samplerLabel(),
                    String.join(", ", reasons),
                    bucket.percentile95(),
                    bucket.percentile99(),
                    bucket.avgResponseTime(),
                    bucket.errorPercent()
            );
            violations.add(v);
            totalViolations.incrementAndGet();
            LOG.log(Level.WARNING, "SLA VIOLATION [{0}]: {1}",
                    new Object[]{bucket.samplerLabel(), String.join(", ", reasons)});
        }
    }

    public SlaStatus getStatus() {
        return new SlaStatus(
                violations.isEmpty() ? "PASS" : "VIOLATED",
                totalChecks.get(),
                totalViolations.get(),
                maxP95Ms, maxP99Ms, maxAvgMs, maxErrorPercent,
                Collections.unmodifiableList(new ArrayList<>(violations))
        );
    }

    public List<SlaViolation> getViolations() {
        return Collections.unmodifiableList(new ArrayList<>(violations));
    }

    public boolean isViolated() {
        return !violations.isEmpty();
    }

    public record SlaViolation(
            Instant timestamp,
            String samplerLabel,
            String reason,
            double actualP95,
            double actualP99,
            double actualAvg,
            double actualErrorPercent
    ) {}

    public record SlaStatus(
            String verdict,
            long totalChecks,
            long totalViolations,
            double thresholdP95,
            double thresholdP99,
            double thresholdAvg,
            double thresholdErrorPercent,
            List<SlaViolation> violations
    ) {}
}
