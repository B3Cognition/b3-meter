package com.jmeternext.web.api.controller.dto;

import java.util.List;

/**
 * Request body for starting a new test run.
 *
 * <p>Carries the plan identifier and optional execution parameters. When
 * {@code virtualUsers} or {@code durationSeconds} are {@code null} the
 * service will apply sensible defaults (1 virtual user, 0 = run until plan
 * completes).
 *
 * <p>For distributed runs, {@code workerAddresses} lists the
 * {@code host:port} strings of worker nodes that should participate.
 * When {@code null} or empty the run executes locally on the controller.
 *
 * @param planId           identifier of the saved test plan to execute; must not be null or blank
 * @param virtualUsers     number of concurrent virtual-user threads; null means default (1)
 * @param durationSeconds  maximum run duration in seconds; null means run until plan completes
 * @param workerAddresses  list of worker {@code host:port} addresses for distributed execution; null means local
 */
public record StartRunRequest(
        String planId,
        Integer virtualUsers,
        Long durationSeconds,
        List<String> workerAddresses
) {}
