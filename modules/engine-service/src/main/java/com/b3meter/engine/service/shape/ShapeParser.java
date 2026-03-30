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
package com.b3meter.engine.service.shape;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses shape specification strings into {@link LoadShape} instances.
 *
 * <p>Supported formats:
 * <ul>
 *   <li>{@code "constant:50:60s"} &rarr; {@link ConstantShape}(50, 60s)</li>
 *   <li>{@code "ramp:0:100:60s"} &rarr; {@link RampShape}(0, 100, 60s)</li>
 *   <li>{@code "stages:0:10:30s,10:50:60s,50:0:30s"} &rarr; {@link StagesShape} with three stages</li>
 *   <li>{@code "step:10:15s:100:120s"} &rarr; {@link StepShape}(10, 15s, 100, 120s)</li>
 *   <li>{@code "sine:10:100:30s:120s"} &rarr; {@link SinusoidalShape}(10, 100, 30s, 120s)</li>
 * </ul>
 *
 * <p>Duration values support the suffixes {@code s} (seconds), {@code m} (minutes), and
 * {@code h} (hours). A bare number without a suffix is interpreted as seconds.
 *
 * <p>This class is stateless and all methods are static.
 */
public final class ShapeParser {

    private static final Logger LOG = Logger.getLogger(ShapeParser.class.getName());

    /**
     * Pattern for parsing duration strings like "60s", "5m", "2h", or bare "30".
     */
    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)([smh])?$");

    private ShapeParser() {
        // utility class
    }

    /**
     * Parses a shape specification string into a {@link LoadShape} instance.
     *
     * @param spec the shape specification string; must not be {@code null} or blank
     * @return the parsed {@link LoadShape}
     * @throws IllegalArgumentException if the spec cannot be parsed or is invalid
     * @throws NullPointerException     if {@code spec} is {@code null}
     */
    public static LoadShape parse(String spec) {
        if (spec == null) {
            throw new NullPointerException("spec must not be null");
        }
        String trimmed = spec.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("spec must not be blank");
        }

        // Split on the first colon to get the type
        int firstColon = trimmed.indexOf(':');
        if (firstColon < 0) {
            throw new IllegalArgumentException("Invalid shape spec (no type prefix): " + spec);
        }

        String type = trimmed.substring(0, firstColon).toLowerCase();
        String params = trimmed.substring(firstColon + 1);

        return switch (type) {
            case "constant" -> parseConstant(params, spec);
            case "ramp"     -> parseRamp(params, spec);
            case "stages"   -> parseStages(params, spec);
            case "step"     -> parseStep(params, spec);
            case "sine"     -> parseSine(params, spec);
            default -> throw new IllegalArgumentException(
                    "Unknown shape type '" + type + "' in spec: " + spec
                            + ". Supported types: constant, ramp, stages, step, sine");
        };
    }

    /**
     * Parses {@code "users:duration"} into a {@link ConstantShape}.
     */
    private static LoadShape parseConstant(String params, String originalSpec) {
        String[] parts = params.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "constant shape requires 2 params (users:duration), got " + parts.length
                            + " in spec: " + originalSpec);
        }
        int users = parseInt(parts[0], "users", originalSpec);
        Duration duration = parseDuration(parts[1], originalSpec);
        return new ConstantShape(users, duration);
    }

    /**
     * Parses {@code "startUsers:endUsers:rampDuration"} into a {@link RampShape}.
     */
    private static LoadShape parseRamp(String params, String originalSpec) {
        String[] parts = params.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException(
                    "ramp shape requires 3 params (startUsers:endUsers:rampDuration), got "
                            + parts.length + " in spec: " + originalSpec);
        }
        int startUsers = parseInt(parts[0], "startUsers", originalSpec);
        int endUsers = parseInt(parts[1], "endUsers", originalSpec);
        Duration rampDuration = parseDuration(parts[2], originalSpec);
        return new RampShape(startUsers, endUsers, rampDuration);
    }

    /**
     * Parses {@code "from:to:dur,from:to:dur,..."} into a {@link StagesShape}.
     *
     * <p>Each stage triple defines: the starting user count (used to derive
     * the target via linear interpolation from the previous stage's end),
     * the target user count at the end of the stage, and the stage duration.
     *
     * <p>Note: the {@link StagesShape} constructor computes stage start users
     * automatically (first stage starts from 0, subsequent stages start from
     * the previous stage's target). The {@code from} value in the spec is
     * effectively documentation; only {@code to} and {@code dur} are used.
     */
    private static LoadShape parseStages(String params, String originalSpec) {
        String[] stageSpecs = params.split(",");
        if (stageSpecs.length == 0) {
            throw new IllegalArgumentException(
                    "stages shape requires at least one stage in spec: " + originalSpec);
        }

        List<StagesShape.Stage> stages = new ArrayList<>();
        for (String stageSpec : stageSpecs) {
            String[] parts = stageSpec.trim().split(":");
            if (parts.length != 3) {
                throw new IllegalArgumentException(
                        "Each stage requires 3 params (from:to:duration), got "
                                + parts.length + " in stage '" + stageSpec
                                + "' of spec: " + originalSpec);
            }
            // parts[0] = from (informational — StagesShape derives it)
            int targetUsers = parseInt(parts[1], "targetUsers", originalSpec);
            Duration duration = parseDuration(parts[2], originalSpec);
            stages.add(new StagesShape.Stage(duration, targetUsers));
        }
        return new StagesShape(stages);
    }

    /**
     * Parses {@code "stepSize:stepInterval:maxUsers:totalDuration"} into a {@link StepShape}.
     */
    private static LoadShape parseStep(String params, String originalSpec) {
        String[] parts = params.split(":");
        if (parts.length != 4) {
            throw new IllegalArgumentException(
                    "step shape requires 4 params (stepSize:stepInterval:maxUsers:totalDuration), got "
                            + parts.length + " in spec: " + originalSpec);
        }
        int stepSize = parseInt(parts[0], "stepSize", originalSpec);
        Duration stepInterval = parseDuration(parts[1], originalSpec);
        int maxUsers = parseInt(parts[2], "maxUsers", originalSpec);
        Duration totalDuration = parseDuration(parts[3], originalSpec);
        return new StepShape(stepSize, stepInterval, maxUsers, totalDuration);
    }

    /**
     * Parses {@code "minUsers:maxUsers:period:totalDuration"} into a {@link SinusoidalShape}.
     */
    private static LoadShape parseSine(String params, String originalSpec) {
        String[] parts = params.split(":");
        if (parts.length != 4) {
            throw new IllegalArgumentException(
                    "sine shape requires 4 params (minUsers:maxUsers:period:totalDuration), got "
                            + parts.length + " in spec: " + originalSpec);
        }
        int minUsers = parseInt(parts[0], "minUsers", originalSpec);
        int maxUsers = parseInt(parts[1], "maxUsers", originalSpec);
        Duration period = parseDuration(parts[2], originalSpec);
        Duration totalDuration = parseDuration(parts[3], originalSpec);
        return new SinusoidalShape(minUsers, maxUsers, period, totalDuration);
    }

    /**
     * Parses a duration string with optional suffix.
     *
     * <p>Supported formats:
     * <ul>
     *   <li>{@code "30"} or {@code "30s"} &rarr; 30 seconds</li>
     *   <li>{@code "5m"} &rarr; 5 minutes</li>
     *   <li>{@code "2h"} &rarr; 2 hours</li>
     * </ul>
     *
     * @param value        the duration string to parse
     * @param originalSpec the original spec string (for error messages)
     * @return the parsed {@link Duration}
     * @throws IllegalArgumentException if the value cannot be parsed
     */
    static Duration parseDuration(String value, String originalSpec) {
        String trimmed = value.trim();
        Matcher matcher = DURATION_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Invalid duration '" + value + "' in spec: " + originalSpec
                            + ". Expected format: <number>[s|m|h]");
        }
        long amount = Long.parseLong(matcher.group(1));
        String suffix = matcher.group(2);
        if (suffix == null || suffix.equals("s")) {
            return Duration.ofSeconds(amount);
        } else if (suffix.equals("m")) {
            return Duration.ofMinutes(amount);
        } else { // "h"
            return Duration.ofHours(amount);
        }
    }

    /**
     * Parses an integer value from a string.
     *
     * @param value        the string to parse
     * @param paramName    the parameter name (for error messages)
     * @param originalSpec the original spec string (for error messages)
     * @return the parsed integer
     * @throws IllegalArgumentException if the value cannot be parsed
     */
    private static int parseInt(String value, String paramName, String originalSpec) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid " + paramName + " '" + value + "' in spec: " + originalSpec
                            + ". Expected an integer.");
        }
    }
}
