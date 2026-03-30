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
package com.b3meter.web.api.config;

import com.b3meter.web.api.repository.TestRunRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Micrometer observability configuration.
 *
 * <p>Registers the custom meters that surface b3meter operational state on
 * the {@code /actuator/prometheus} endpoint:
 * <ul>
 *   <li>{@code jmeter_runs_active} — gauge: number of test runs currently in
 *       RUNNING or STOPPING state, derived from the persistent run store.</li>
 *   <li>{@code jmeter_samples_total} — counter: total sample results processed
 *       since application start (incremented by {@link ObservabilityFacade}).</li>
 *   <li>{@code jmeter_errors_total} — counter: total error results processed
 *       since application start.</li>
 *   <li>{@code jmeter_run_duration_seconds} — timer: elapsed wall-clock time per
 *       completed test run, recorded by {@link ObservabilityFacade}.</li>
 * </ul>
 *
 * <p>All meter names follow Prometheus snake_case convention and are prefixed
 * with {@code jmeter_} to avoid collisions with JVM / Spring built-ins.
 */
@Configuration
public class ObservabilityConfig {

    /**
     * Registers all custom jmeter_* Micrometer meters.
     *
     * <p>The gauge reads the active-run count on every scrape via
     * {@link TestRunRepository#countActive()}, which is a lightweight DB query.
     * Counters and the timer are returned as an {@link ObservabilityFacade} bean
     * so other components can increment them without taking a direct dependency
     * on {@link MeterRegistry}.
     *
     * @param registry   the auto-configured {@link MeterRegistry} provided by
     *                   Spring Boot Actuator
     * @param runRepository the run repository used to compute the active-run gauge
     * @return the facade that exposes increment helpers for counters and the timer
     */
    @Bean
    public ObservabilityFacade observabilityFacade(MeterRegistry registry,
                                                    TestRunRepository runRepository) {

        // Gauge: sampled lazily — reads DB on every Prometheus scrape.
        io.micrometer.core.instrument.Gauge.builder(
                        "jmeter_runs_active",
                        runRepository,
                        repo -> repo.countActive())
                .description("Number of test runs currently in RUNNING or STOPPING state")
                .register(registry);

        // Counter: total samples processed since startup.
        Counter samplesCounter = Counter.builder("jmeter_samples_total")
                .description("Total sample results processed since application start")
                .register(registry);

        // Counter: total error results processed since startup.
        Counter errorsCounter = Counter.builder("jmeter_errors_total")
                .description("Total error results processed since application start")
                .register(registry);

        // Timer: elapsed wall-clock time per completed test run.
        Timer runDurationTimer = Timer.builder("jmeter_run_duration_seconds")
                .description("Elapsed wall-clock time per completed test run")
                .register(registry);

        return new ObservabilityFacade(samplesCounter, errorsCounter, runDurationTimer);
    }

    // -------------------------------------------------------------------------
    // Inner facade — keeps MeterRegistry out of callers' signatures
    // -------------------------------------------------------------------------

    /**
     * Thin facade around the custom jmeter_* meters.
     *
     * <p>Inject this bean wherever sample / error counts need to be incremented or
     * a run duration recorded. It deliberately exposes only the narrow mutating
     * surface needed by the application, not the full {@link MeterRegistry}.
     */
    public static final class ObservabilityFacade {

        private final Counter samplesCounter;
        private final Counter errorsCounter;
        private final Timer   runDurationTimer;

        ObservabilityFacade(Counter samplesCounter,
                            Counter errorsCounter,
                            Timer   runDurationTimer) {
            this.samplesCounter   = samplesCounter;
            this.errorsCounter    = errorsCounter;
            this.runDurationTimer = runDurationTimer;
        }

        /**
         * Increments {@code jmeter_samples_total} by {@code count}.
         *
         * @param count number of new samples to record; must be positive
         */
        public void recordSamples(long count) {
            samplesCounter.increment(count);
        }

        /**
         * Increments {@code jmeter_errors_total} by {@code count}.
         *
         * @param count number of new errors to record; must be positive
         */
        public void recordErrors(long count) {
            errorsCounter.increment(count);
        }

        /**
         * Records a completed run duration into {@code jmeter_run_duration_seconds}.
         *
         * @param durationMs elapsed duration in milliseconds
         */
        public void recordRunDuration(long durationMs) {
            runDurationTimer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        }

        // Package-private accessors for tests only.
        Counter samplesCounter()   { return samplesCounter; }
        Counter errorsCounter()    { return errorsCounter; }
        Timer   runDurationTimer() { return runDurationTimer; }
    }
}
