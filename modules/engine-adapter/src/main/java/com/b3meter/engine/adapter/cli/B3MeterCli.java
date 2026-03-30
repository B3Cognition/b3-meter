package com.jmeternext.engine.adapter.cli;

import com.jmeternext.engine.adapter.EngineServiceImpl;
import com.jmeternext.engine.adapter.InMemorySampleStreamBroker;
import com.jmeternext.engine.service.EngineService;
import com.jmeternext.engine.service.TestRunContext;
import com.jmeternext.engine.service.TestRunHandle;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CLI entry point for headless jmeter-next test execution.
 *
 * <p>Preserves JMeter CLI flag compatibility:
 * <ul>
 *   <li>{@code -n} — non-GUI (headless) mode; required for CLI execution</li>
 *   <li>{@code -t <file>} — test plan file (.jmx); required with {@code -n}</li>
 *   <li>{@code -l <file>} — results log file (.jtl)</li>
 *   <li>{@code -e} — generate report after test run</li>
 *   <li>{@code -o <dir>} — output directory for generated report</li>
 *   <li>{@code -J<key>=<value>} — property override; may be repeated</li>
 *   <li>{@code -p <file>} — additional properties file</li>
 * </ul>
 *
 * <p>Exit codes:
 * <ul>
 *   <li>{@code 0} — run completed successfully</li>
 *   <li>{@code 1} — test run failed (sampler errors exceed threshold or engine error)</li>
 *   <li>{@code 2} — configuration error (missing required flag, invalid file path, etc.)</li>
 * </ul>
 *
 * <p>The {@link EngineService} is injected via the constructor to keep this class
 * testable without a real JMeter engine.
 */
public final class JMeterNextCli {

    /** Exit code: run completed successfully. */
    public static final int EXIT_OK = 0;

    /** Exit code: test run failed. */
    public static final int EXIT_TEST_FAILURE = 1;

    /** Exit code: configuration or validation error. */
    public static final int EXIT_CONFIG_ERROR = 2;

    private static final Logger LOG = Logger.getLogger(JMeterNextCli.class.getName());

    private final EngineService engineService;
    private final PrintWriter out;
    private final PrintWriter err;

    /**
     * Creates a CLI instance using the provided {@link EngineService} and standard streams.
     *
     * @param engineService engine to delegate test execution to; must not be {@code null}
     */
    public JMeterNextCli(EngineService engineService) {
        this(engineService, new PrintWriter(System.out, true), new PrintWriter(System.err, true));
    }

    /**
     * Creates a CLI instance using the provided engine and custom output writers.
     *
     * <p>This constructor is intended for testing: pass {@code StringWriter}-backed
     * {@link PrintWriter}s to capture output without touching real streams.
     *
     * @param engineService engine to delegate test execution to; must not be {@code null}
     * @param out           writer for standard output; must not be {@code null}
     * @param err           writer for error output; must not be {@code null}
     */
    public JMeterNextCli(EngineService engineService, PrintWriter out, PrintWriter err) {
        this.engineService = Objects.requireNonNull(engineService, "engineService must not be null");
        this.out = Objects.requireNonNull(out, "out must not be null");
        this.err = Objects.requireNonNull(err, "err must not be null");
    }

    /**
     * Main entry point for the CLI.
     *
     * <p>Creates a {@link JMeterNextCli} with a no-op {@link EngineService} stub.
     * The real implementation is wired when {@code EngineServiceImpl} is ready (T014).
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        EngineService engineService = new EngineServiceImpl(
                new InMemorySampleStreamBroker(),
                new com.jmeternext.engine.adapter.http.Hc5HttpClientFactory(),
                com.jmeternext.engine.adapter.NoOpUIBridge.INSTANCE
        );
        System.exit(new JMeterNextCli(engineService).run(args));
    }

    /**
     * Parses arguments, validates inputs, and runs the test.
     *
     * @param args command-line arguments
     * @return exit code: {@link #EXIT_OK}, {@link #EXIT_TEST_FAILURE}, or {@link #EXIT_CONFIG_ERROR}
     */
    public int run(String[] args) {
        CliOptions options = new CliOptions();
        CommandLine cmd = new CommandLine(options)
                .setOut(out)
                .setErr(err);

        int parseExitCode = cmd.execute(args);

        // --help or --version was handled by picocli (exit code 0)
        if (cmd.isUsageHelpRequested() || cmd.isVersionHelpRequested()) {
            return EXIT_OK;
        }

        // Picocli signals a usage/parse error with exit code 2
        if (parseExitCode != 0) {
            return EXIT_CONFIG_ERROR;
        }

        return execute(options);
    }

    private int execute(CliOptions options) {
        // Standalone report generation mode: -g <jtl> -o <dir>
        if (options.generateFromJtl != null) {
            return executeStandaloneReport(options);
        }

        // -n is required for headless mode
        if (!options.nonGui) {
            err.println("ERROR: -n (non-GUI mode) is required for CLI execution.");
            return EXIT_CONFIG_ERROR;
        }

        // -t <plan> is required
        if (options.testPlan == null) {
            err.println("ERROR: -t <testplan> is required when using -n.");
            return EXIT_CONFIG_ERROR;
        }

        // Validate that the plan file exists
        File planFile = options.testPlan;
        if (!planFile.exists()) {
            err.println("ERROR: Test plan file does not exist: " + planFile.getAbsolutePath());
            return EXIT_CONFIG_ERROR;
        }
        if (!planFile.isFile()) {
            err.println("ERROR: Test plan path is not a file: " + planFile.getAbsolutePath());
            return EXIT_CONFIG_ERROR;
        }

        // Load properties file if specified (-p flag)
        Properties overrides = new Properties();
        if (options.propertiesFile != null) {
            File propFile = options.propertiesFile;
            if (!propFile.exists()) {
                err.println("ERROR: Properties file does not exist: " + propFile.getAbsolutePath());
                return EXIT_CONFIG_ERROR;
            }
            try (java.io.FileInputStream fis = new java.io.FileInputStream(propFile)) {
                Properties fileProps = new Properties();
                fileProps.load(fis);
                // Set into System properties for ${__P(name)} access
                for (String name : fileProps.stringPropertyNames()) {
                    System.setProperty(name, fileProps.getProperty(name));
                }
                overrides.putAll(fileProps);
                out.println("Loaded properties from: " + propFile.getName());
            } catch (IOException e) {
                err.println("ERROR: Failed to read properties file: " + e.getMessage());
                return EXIT_CONFIG_ERROR;
            }
        }

        // Build property overrides from -J flags (these take precedence over -p file)
        Properties jOverrides = buildOverrides(options.propertyOverrides);
        overrides.putAll(jOverrides);
        // Also set -J overrides into System properties
        for (String name : jOverrides.stringPropertyNames()) {
            System.setProperty(name, jOverrides.getProperty(name));
        }

        // Wire up JTL results file if specified (-l flag)
        String jtlFilePath = null;
        if (options.resultsLog != null) {
            jtlFilePath = options.resultsLog.getAbsolutePath();
            try {
                com.jmeternext.engine.service.interpreter.SimpleDataWriterExecutor.open(jtlFilePath);
                out.println("JTL output: " + options.resultsLog.getName());
            } catch (IOException e) {
                err.println("ERROR: Failed to open JTL file: " + e.getMessage());
                return EXIT_CONFIG_ERROR;
            }
        }

        String planId = planFile.getAbsolutePath();

        try {
            out.println("Starting test: " + planFile.getName());
            // Read the JMX file content and pass as jmxContent for the NodeInterpreter pipeline
            String jmxContent;
            try {
                jmxContent = new String(java.nio.file.Files.readAllBytes(planFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            } catch (IOException e) {
                err.println("ERROR: Failed to read JMX file: " + e.getMessage());
                return EXIT_CONFIG_ERROR;
            }
            Map<String, Object> treeData = new java.util.HashMap<>();
            treeData.put("jmxContent", jmxContent);
            treeData.put("label", planFile.getName());
            if (jtlFilePath != null) {
                treeData.put("jtlFilePath", jtlFilePath);
            }
            TestRunHandle handle = engineService.startRun(planId, treeData, overrides);

            TestRunContext.TestRunStatus finalStatus = handle.completion().get().finalStatus();
            out.println("Test completed with status: " + finalStatus);

            // Generate HTML report if -e flag is set
            if (options.generateReport && jtlFilePath != null) {
                generateReport(jtlFilePath, options.reportDir);
            } else if (options.generateReport && jtlFilePath == null) {
                err.println("WARNING: -e requires -l <logfile> to generate report. Skipping report.");
            }

            if (finalStatus == TestRunContext.TestRunStatus.STOPPED) {
                return EXIT_OK;
            }
            return EXIT_TEST_FAILURE;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            err.println("ERROR: Test run was interrupted.");
            return EXIT_TEST_FAILURE;
        } catch (ExecutionException e) {
            LOG.log(Level.SEVERE, "Engine execution error", e);
            err.println("ERROR: Engine execution failed: " + e.getCause().getMessage());
            return EXIT_TEST_FAILURE;
        } finally {
            // Close JTL writer if one was opened
            if (jtlFilePath != null) {
                com.jmeternext.engine.service.interpreter.SimpleDataWriterExecutor.close(jtlFilePath);
            }
        }
    }

    private int executeStandaloneReport(CliOptions options) {
        File jtlFile = options.generateFromJtl;
        if (!jtlFile.exists()) {
            err.println("ERROR: JTL file does not exist: " + jtlFile.getAbsolutePath());
            return EXIT_CONFIG_ERROR;
        }
        if (!jtlFile.isFile()) {
            err.println("ERROR: JTL path is not a file: " + jtlFile.getAbsolutePath());
            return EXIT_CONFIG_ERROR;
        }

        File reportDir = options.reportDir;
        if (reportDir == null) {
            err.println("ERROR: -o <reportdir> is required when using -g.");
            return EXIT_CONFIG_ERROR;
        }

        try {
            com.jmeternext.engine.adapter.report.HtmlReportGenerator.generate(
                    jtlFile.toPath(), reportDir.toPath());
            out.println("Report generated: " + reportDir.getAbsolutePath());
            return EXIT_OK;
        } catch (Exception e) {
            err.println("ERROR: Failed to generate report: " + e.getMessage());
            return EXIT_TEST_FAILURE;
        }
    }

    private void generateReport(String jtlFilePath, File reportDir) {
        if (reportDir == null) {
            // Default report directory: next to JTL file
            reportDir = new File(new File(jtlFilePath).getParentFile(), "report");
        }
        try {
            com.jmeternext.engine.adapter.report.HtmlReportGenerator.generate(
                    java.nio.file.Path.of(jtlFilePath), reportDir.toPath());
            out.println("Report generated: " + reportDir.getAbsolutePath());
        } catch (Exception e) {
            err.println("WARNING: Failed to generate report: " + e.getMessage());
        }
    }

    private static Properties buildOverrides(List<String> propertyOverrides) {
        Properties props = new Properties();
        if (propertyOverrides == null) {
            return props;
        }
        for (String override : propertyOverrides) {
            int eq = override.indexOf('=');
            if (eq > 0) {
                String key = override.substring(0, eq);
                String value = override.substring(eq + 1);
                props.setProperty(key, value);
            } else {
                // key with no value — treat as flag (empty string value)
                props.setProperty(override, "");
            }
        }
        return props;
    }

    // -------------------------------------------------------------------------
    // Picocli command model
    // -------------------------------------------------------------------------

    /**
     * Picocli-annotated command options model.
     *
     * <p>Declared as a static inner class so it can be constructed directly in tests
     * without instantiating a full {@link JMeterNextCli}.
     */
    @Command(
            name = "jmeter-next",
            mixinStandardHelpOptions = true,
            description = "jmeter-next headless test runner — JMeter CLI compatible",
            version = "jmeter-next 1.0.0-SNAPSHOT"
    )
    static final class CliOptions implements Runnable {

        @Option(names = "-n", description = "Non-GUI (headless) mode")
        boolean nonGui;

        @Option(names = "-t", description = "Test plan file (.jmx)", paramLabel = "<testplan>")
        File testPlan;

        @Option(names = "-l", description = "Results log file (.jtl)", paramLabel = "<logfile>")
        File resultsLog;

        @Option(names = "-e", description = "Generate HTML report after run")
        boolean generateReport;

        @Option(names = "-o", description = "Output directory for HTML report", paramLabel = "<reportdir>")
        File reportDir;

        @Option(names = "-g", description = "Generate report from existing JTL file (standalone)", paramLabel = "<jtlfile>")
        File generateFromJtl;

        @Option(
                names = "-J",
                description = "Property override in key=value format; may be repeated",
                paramLabel = "<key=value>"
        )
        List<String> propertyOverrides;

        @Option(names = "-p", description = "Additional properties file", paramLabel = "<propfile>")
        File propertiesFile;

        @Override
        public void run() {
            // Execution is delegated to JMeterNextCli.execute(); this method is intentionally empty.
        }
    }

    // -------------------------------------------------------------------------
    // No-op stub — used until EngineServiceImpl is ready (T014)
    // -------------------------------------------------------------------------

    /**
     * Stub {@link EngineService} that immediately completes every run with STOPPED status.
     *
     * <p>Used by {@link #main} until the real {@code EngineServiceImpl} is wired in T014.
     * Not intended for production use.
     */
    static final class NoOpEngineService implements EngineService {

        @Override
        public TestRunHandle startRun(String planId, Map<String, Object> treeData, Properties overrides) {
            String runId = UUID.randomUUID().toString();
            java.util.concurrent.CompletableFuture<com.jmeternext.engine.service.TestRunResult> future =
                    java.util.concurrent.CompletableFuture.completedFuture(
                            new com.jmeternext.engine.service.TestRunResult(
                                    runId,
                                    TestRunContext.TestRunStatus.STOPPED,
                                    java.time.Instant.now(),
                                    java.time.Instant.now(),
                                    0L,
                                    0L,
                                    java.time.Duration.ZERO
                            )
                    );
            return new TestRunHandle(runId, java.time.Instant.now(), future);
        }

        @Override
        public void stopRun(String runId) {
            // intentionally no-op
        }

        @Override
        public void stopRunNow(String runId) {
            // intentionally no-op
        }

        @Override
        public TestRunContext.TestRunStatus getRunStatus(String runId) {
            return null;
        }

        @Override
        public java.util.Collection<TestRunContext> activeRuns() {
            return Collections.emptyList();
        }
    }
}
