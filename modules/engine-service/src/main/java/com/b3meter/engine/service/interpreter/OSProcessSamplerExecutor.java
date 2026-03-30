package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes an {@code OSProcessSampler} {@link PlanNode} using {@link ProcessBuilder}.
 *
 * <p>Reads the following sampler properties:
 * <ul>
 *   <li>{@code OSProcessSampler.command} — the command to execute (e.g. "curl", "/bin/bash")</li>
 *   <li>{@code OSProcessSampler.arguments} — newline-separated arguments</li>
 *   <li>{@code OSProcessSampler.workingDirectory} — working directory (default ".")</li>
 *   <li>{@code OSProcessSampler.environment} — newline-separated KEY=VALUE pairs</li>
 *   <li>{@code OSProcessSampler.timeout} — timeout in ms (default 60000)</li>
 *   <li>{@code OSProcessSampler.expectedReturnCode} — expected exit code (default 0)</li>
 * </ul>
 *
 * <p>This sampler allows users to run arbitrary commands — {@code curl}, {@code wrk},
 * {@code k6}, custom scripts, database queries via CLI, or any other executable.
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class OSProcessSamplerExecutor {

    private static final Logger LOG = Logger.getLogger(OSProcessSamplerExecutor.class.getName());
    private static final int DEFAULT_TIMEOUT_MS = 60000;
    private static final int DEFAULT_EXPECTED_RETURN_CODE = 0;

    private OSProcessSamplerExecutor() {
        // utility class — not instantiable
    }

    /**
     * Executes the OS process described by {@code node}.
     *
     * @param node      the OSProcessSampler node; must not be {@code null}
     * @param result    the sample result to populate; must not be {@code null}
     * @param variables current VU variable scope for {@code ${varName}} substitution
     */
    public static void execute(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String command = resolve(node.getStringProp("OSProcessSampler.command", ""), variables);
        String argumentsRaw = resolve(node.getStringProp("OSProcessSampler.arguments", ""), variables);
        String workingDir = resolve(node.getStringProp("OSProcessSampler.workingDirectory", "."), variables);
        String environmentRaw = resolve(node.getStringProp("OSProcessSampler.environment", ""), variables);
        int timeout = node.getIntProp("OSProcessSampler.timeout", DEFAULT_TIMEOUT_MS);
        int expectedReturnCode = node.getIntProp("OSProcessSampler.expectedReturnCode", DEFAULT_EXPECTED_RETURN_CODE);

        if (command.isBlank()) {
            result.setFailureMessage("OSProcessSampler.command is empty");
            return;
        }

        // Build command list
        List<String> cmdList = new ArrayList<>();
        cmdList.add(command);
        if (!argumentsRaw.isBlank()) {
            for (String arg : argumentsRaw.split("\n")) {
                String trimmed = arg.trim();
                if (!trimmed.isEmpty()) {
                    cmdList.add(trimmed);
                }
            }
        }

        LOG.log(Level.FINE, "OSProcessSamplerExecutor: executing {0}", cmdList);

        long start = System.currentTimeMillis();

        try {
            ProcessBuilder pb = new ProcessBuilder(cmdList);
            pb.redirectErrorStream(false);

            // Set working directory
            if (!workingDir.isBlank()) {
                File dir = new File(workingDir);
                if (dir.isDirectory()) {
                    pb.directory(dir);
                }
            }

            // Set environment variables
            if (!environmentRaw.isBlank()) {
                Map<String, String> env = pb.environment();
                for (String line : environmentRaw.split("\n")) {
                    String trimmed = line.trim();
                    int eqIdx = trimmed.indexOf('=');
                    if (eqIdx > 0) {
                        String key = trimmed.substring(0, eqIdx).trim();
                        String value = trimmed.substring(eqIdx + 1).trim();
                        env.put(key, value);
                    }
                }
            }

            Process process = pb.start();

            // Read stdout and stderr in background threads to prevent pipe buffer deadlock
            StringBuilder stdoutBuf = new StringBuilder();
            StringBuilder stderrBuf = new StringBuilder();

            Thread stdoutReader = Thread.ofVirtual().start(() -> {
                try (InputStream in = process.getInputStream()) {
                    stdoutBuf.append(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                } catch (IOException ignored) {
                    // process destroyed — ignore
                }
            });

            Thread stderrReader = Thread.ofVirtual().start(() -> {
                try (InputStream in = process.getErrorStream()) {
                    stderrBuf.append(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                } catch (IOException ignored) {
                    // process destroyed — ignore
                }
            });

            boolean finished = process.waitFor(timeout, TimeUnit.MILLISECONDS);
            long total = System.currentTimeMillis() - start;
            result.setTotalTimeMs(total);

            if (!finished) {
                process.destroyForcibly();
                // Give reader threads a moment to finish after destroy
                stdoutReader.join(1000);
                stderrReader.join(1000);
                result.setFailureMessage("OSProcessSampler: process timed out after " + timeout + "ms");
                result.setResponseBody(stdoutBuf.toString());
                return;
            }

            // Wait for reader threads to complete
            stdoutReader.join(5000);
            stderrReader.join(5000);

            String stdout = stdoutBuf.toString();
            String stderr = stderrBuf.toString();

            int exitCode = process.exitValue();
            result.setStatusCode(exitCode);

            // Build response body
            StringBuilder body = new StringBuilder(stdout);
            if (!stderr.isBlank()) {
                if (!body.isEmpty()) {
                    body.append("\n--- STDERR ---\n");
                }
                body.append(stderr);
            }
            result.setResponseBody(body.toString());

            // Check exit code against expected
            if (exitCode != expectedReturnCode) {
                result.setSuccess(false);
                result.setFailureMessage("OSProcessSampler: exit code " + exitCode
                        + " (expected " + expectedReturnCode + ")");
            }

        } catch (IOException e) {
            long total = System.currentTimeMillis() - start;
            result.setTotalTimeMs(total);
            result.setFailureMessage("OSProcessSampler: " + e.getMessage());
            LOG.log(Level.WARNING, "OSProcessSamplerExecutor: failed to start process", e);
        } catch (InterruptedException e) {
            long total = System.currentTimeMillis() - start;
            result.setTotalTimeMs(total);
            result.setFailureMessage("OSProcessSampler: interrupted");
            Thread.currentThread().interrupt();
        }
    }

    private static String resolve(String value, Map<String, String> variables) {
        if (value == null) return "";
        return VariableResolver.resolve(value, variables);
    }
}
