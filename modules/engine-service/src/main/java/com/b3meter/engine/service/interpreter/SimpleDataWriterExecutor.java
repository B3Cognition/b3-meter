package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Writes each {@link SampleResult} to a JTL file in CSV format.
 *
 * <p>The JTL CSV format is compatible with JMeter 5.x:
 * <pre>
 * timeStamp,elapsed,label,responseCode,responseMessage,threadName,dataType,success,
 * failureMessage,bytes,sentBytes,grpThreads,allThreads,URL,Latency,IdleTime,Connect
 * </pre>
 *
 * <p>Each active writer is keyed by its output file path. The writer is opened on
 * first use and closed via {@link #close(String)}.
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class SimpleDataWriterExecutor {

    private static final Logger LOG = Logger.getLogger(SimpleDataWriterExecutor.class.getName());

    /** JTL CSV header line. */
    static final String CSV_HEADER =
            "timeStamp,elapsed,label,responseCode,responseMessage,threadName,"
            + "dataType,success,failureMessage,bytes,sentBytes,grpThreads,allThreads,"
            + "URL,Latency,IdleTime,Connect";

    /** Active writers keyed by file path. */
    private static final ConcurrentHashMap<String, BufferedWriter> WRITERS =
            new ConcurrentHashMap<>();

    private SimpleDataWriterExecutor() {}

    /**
     * Opens a JTL writer for the given file path and writes the CSV header.
     *
     * @param filePath path to the JTL output file; must not be {@code null}
     * @throws IOException if the file cannot be opened
     */
    public static void open(String filePath) throws IOException {
        Objects.requireNonNull(filePath, "filePath must not be null");

        Path path = Path.of(filePath);

        // Ensure parent directories exist
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);

        writer.write(CSV_HEADER);
        writer.newLine();
        writer.flush();

        WRITERS.put(filePath, writer);
        LOG.log(Level.INFO, "SimpleDataWriter: opened JTL file [{0}]", filePath);
    }

    /**
     * Writes a single sample result as a CSV line to the JTL file.
     *
     * @param filePath the JTL file path (must have been opened via {@link #open})
     * @param result   the sample result to write; must not be {@code null}
     */
    public static void writeSample(String filePath, SampleResult result) {
        Objects.requireNonNull(filePath, "filePath must not be null");
        Objects.requireNonNull(result, "result must not be null");

        BufferedWriter writer = WRITERS.get(filePath);
        if (writer == null) {
            LOG.log(Level.WARNING,
                    "SimpleDataWriter: no open writer for [{0}] — skipping sample", filePath);
            return;
        }

        String line = formatCsvLine(result);
        try {
            synchronized (writer) {
                writer.write(line);
                writer.newLine();
                writer.flush();
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING,
                    "SimpleDataWriter: failed to write sample to [{0}]: {1}",
                    new Object[]{filePath, e.getMessage()});
        }
    }

    /**
     * Closes the JTL writer for the given file path.
     *
     * @param filePath the JTL file path
     */
    public static void close(String filePath) {
        if (filePath == null) return;
        BufferedWriter writer = WRITERS.remove(filePath);
        if (writer != null) {
            try {
                writer.flush();
                writer.close();
                LOG.log(Level.INFO, "SimpleDataWriter: closed JTL file [{0}]", filePath);
            } catch (IOException e) {
                LOG.log(Level.WARNING,
                        "SimpleDataWriter: error closing [{0}]: {1}",
                        new Object[]{filePath, e.getMessage()});
            }
        }
    }

    /**
     * Formats a SampleResult as a JTL CSV line.
     *
     * @param result the sample result
     * @return CSV-formatted line (without trailing newline)
     */
    static String formatCsvLine(SampleResult result) {
        String threadName = Thread.currentThread().getName();
        long timestamp = Instant.now().toEpochMilli();

        // timeStamp,elapsed,label,responseCode,responseMessage,threadName,dataType,
        // success,failureMessage,bytes,sentBytes,grpThreads,allThreads,URL,Latency,IdleTime,Connect
        return String.join(",",
                String.valueOf(timestamp),
                String.valueOf(result.getTotalTimeMs()),
                escapeCsv(result.getLabel()),
                String.valueOf(result.getStatusCode()),
                result.isSuccess() ? "OK" : "FAIL",
                escapeCsv(threadName),
                "text",
                String.valueOf(result.isSuccess()),
                escapeCsv(result.getFailureMessage()),
                String.valueOf(result.getResponseBody().length()),
                "0",   // sentBytes — not tracked yet
                "1",   // grpThreads — simplified
                "1",   // allThreads — simplified
                "",    // URL — not available in SampleResult currently
                String.valueOf(result.getLatencyMs()),
                "0",   // IdleTime
                String.valueOf(result.getConnectTimeMs())
        );
    }

    /** Escapes a string for CSV (wraps in quotes if it contains comma, quote, or newline). */
    private static String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /** Closes all open writers. For test isolation. */
    static void closeAll() {
        WRITERS.keySet().forEach(SimpleDataWriterExecutor::close);
    }

    /** Returns true if a writer is open for the given path. For testing. */
    static boolean isOpen(String filePath) {
        return WRITERS.containsKey(filePath);
    }
}
