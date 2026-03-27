package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import java.text.DecimalFormat;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a {@code CounterConfig} {@link PlanNode} to generate incrementing numbers.
 *
 * <p>Generates a counter value that increments on each invocation and stores it
 * in a named variable. Supports shared (global) and per-user (per-thread) counters,
 * optional formatting via {@link DecimalFormat}, and wrap-around at the end value.
 *
 * <p>Reads the following JMX properties:
 * <ul>
 *   <li>{@code CounterConfig.start} — starting value (default "1")</li>
 *   <li>{@code CounterConfig.end} — maximum value (empty = Long.MAX_VALUE)</li>
 *   <li>{@code CounterConfig.incr} — increment per iteration (default "1")</li>
 *   <li>{@code CounterConfig.name} — variable name to store counter</li>
 *   <li>{@code CounterConfig.format} — DecimalFormat pattern</li>
 *   <li>{@code CounterConfig.per_user} — per-thread counter (vs shared)</li>
 *   <li>{@code CounterConfig.reset_on_tg_iteration} — reset when thread group loop restarts</li>
 * </ul>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class CounterConfigExecutor {

    private static final Logger LOG = Logger.getLogger(CounterConfigExecutor.class.getName());

    /** Shared counters for global (non-per_user) mode. Key: variable name. */
    private static final ConcurrentHashMap<String, AtomicLong> GLOBAL_COUNTERS =
            new ConcurrentHashMap<>();

    /** Per-thread counter key prefix stored in VU variables. */
    private static final String PER_USER_PREFIX = "__jmn_counter_";

    private CounterConfigExecutor() {}

    /**
     * Executes the counter: increments (or initialises) the counter and stores
     * the formatted value in the VU variable map.
     *
     * @param node      the CounterConfig node; must not be {@code null}
     * @param variables mutable VU variable map
     */
    public static void execute(PlanNode node, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String startStr = node.getStringProp("CounterConfig.start", "1");
        String endStr = node.getStringProp("CounterConfig.end", "");
        String incrStr = node.getStringProp("CounterConfig.incr", "1");
        String varName = node.getStringProp("CounterConfig.name", "");
        String format = node.getStringProp("CounterConfig.format", "");
        boolean perUser = node.getBoolProp("CounterConfig.per_user", false);

        if (varName.isEmpty()) {
            LOG.log(Level.FINE,
                    "CounterConfigExecutor [{0}]: no variable name specified — skipping",
                    node.getTestName());
            return;
        }

        long start = parseLong(startStr, 1L);
        long end = endStr.isEmpty() ? Long.MAX_VALUE : parseLong(endStr, Long.MAX_VALUE);
        long incr = parseLong(incrStr, 1L);

        long value;
        if (perUser) {
            value = getAndIncrementPerUser(varName, start, end, incr, variables);
        } else {
            value = getAndIncrementGlobal(varName, start, end, incr);
        }

        String formatted;
        if (format != null && !format.isEmpty()) {
            try {
                formatted = new DecimalFormat(format).format(value);
            } catch (IllegalArgumentException e) {
                formatted = String.valueOf(value);
            }
        } else {
            formatted = String.valueOf(value);
        }

        variables.put(varName, formatted);

        LOG.log(Level.FINE,
                "CounterConfigExecutor [{0}]: {1} = {2}",
                new Object[]{node.getTestName(), varName, formatted});
    }

    /**
     * Gets the current counter value and increments for per-user mode.
     * Uses VU variables to track per-thread state.
     */
    private static long getAndIncrementPerUser(String varName, long start, long end,
                                                 long incr, Map<String, String> variables) {
        String key = PER_USER_PREFIX + varName;
        String currentStr = variables.get(key);

        long current;
        if (currentStr == null) {
            current = start;
        } else {
            current = parseLong(currentStr, start);
        }

        long value = current;

        // Calculate next value
        long next = current + incr;
        if (next > end) {
            next = start; // Wrap around
        }
        variables.put(key, String.valueOf(next));

        return value;
    }

    /**
     * Gets the current counter value and increments for global (shared) mode.
     * Uses AtomicLong for thread safety.
     */
    private static long getAndIncrementGlobal(String varName, long start, long end, long incr) {
        AtomicLong counter = GLOBAL_COUNTERS.computeIfAbsent(varName,
                k -> new AtomicLong(start));

        long value;
        long next;
        do {
            value = counter.get();
            next = value + incr;
            if (next > end) {
                next = start; // Wrap around
            }
        } while (!counter.compareAndSet(value, next));

        return value;
    }

    private static long parseLong(String s, long defaultValue) {
        if (s == null || s.isEmpty()) return defaultValue;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Resets all global counters. Intended for testing only.
     */
    static void resetCounters() {
        GLOBAL_COUNTERS.clear();
    }
}
