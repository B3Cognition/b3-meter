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

/**
 * Structured exit codes for b3meter CLI and CI integration.
 *
 * <p>Each exit code represents a distinct outcome category, allowing CI/CD
 * pipelines to react differently based on how the test run ended:
 *
 * <ul>
 *   <li>{@link #OK} (0) — All tests passed, all SLAs met</li>
 *   <li>{@link #TEST_FAILED} (1) — Test completed but errors were detected</li>
 *   <li>{@link #CONFIG_ERROR} (2) — Invalid configuration or test plan</li>
 *   <li>{@link #RUNTIME_ERROR} (3) — Unexpected error during execution</li>
 *   <li>{@link #SLA_VIOLATION} (4) — Specific SLA threshold was breached</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * ExitCode code = evaluateRun(result, thresholds);
 * System.exit(code.getCode());
 * }</pre>
 */
public enum ExitCode {

    /** Test completed successfully, all SLAs met. */
    OK(0),

    /** Test completed but SLA violations or errors were found. */
    TEST_FAILED(1),

    /** Invalid configuration or test plan — run did not start. */
    CONFIG_ERROR(2),

    /** Unexpected error during test execution. */
    RUNTIME_ERROR(3),

    /** Specific SLA threshold was breached. */
    SLA_VIOLATION(4);

    private final int code;

    ExitCode(int code) {
        this.code = code;
    }

    /**
     * Returns the numeric process exit code.
     *
     * @return the integer exit code (0–4)
     */
    public int getCode() {
        return code;
    }

    /**
     * Returns the {@link ExitCode} matching the given numeric code.
     *
     * @param code the numeric exit code
     * @return the matching enum constant
     * @throws IllegalArgumentException if no constant matches the given code
     */
    public static ExitCode fromCode(int code) {
        for (ExitCode ec : values()) {
            if (ec.code == code) {
                return ec;
            }
        }
        throw new IllegalArgumentException("Unknown exit code: " + code);
    }
}
