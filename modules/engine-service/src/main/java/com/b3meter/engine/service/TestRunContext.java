package com.jmeternext.engine.service;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-run state container for a single jmeter-next test execution.
 *
 * <p>Carries the immutable configuration for a run (runId, planPath, virtualUsers,
 * durationSeconds) plus the mutable runtime state that was previously stored in
 * static fields across JMeter internals (ResultCollector file handles, engine running
 * flag, per-run properties). Centralising this state here makes concurrent multi-run
 * scenarios possible without global statics.
 *
 * <p>Thread-safety contract:
 * <ul>
 *   <li>Immutable fields (runId, planPath, virtualUsers, durationSeconds, uiBridge,
 *       startedAt, runProperties, runState, resultWriters) are safe to read from any
 *       thread after construction.</li>
 *   <li>{@code status} and {@code running} are {@code volatile} so cross-thread
 *       visibility is guaranteed for simple reads/writes.</li>
 *   <li>{@code resultWriters} is a {@link ConcurrentHashMap} — concurrent put/remove
 *       from multiple threads is safe.</li>
 *   <li>{@code runState} is a {@link ConcurrentHashMap} — concurrent put/remove from
 *       multiple threads is safe.</li>
 * </ul>
 */
public final class TestRunContext {

    // -------------------------------------------------------------------------
    // Immutable run configuration
    // -------------------------------------------------------------------------

    private final String runId;
    private final String planPath;
    private final int virtualUsers;
    private final long durationSeconds;

    // -------------------------------------------------------------------------
    // Runtime collaborators and per-run properties
    // -------------------------------------------------------------------------

    private final UIBridge uiBridge;
    private final Properties runProperties;
    private final Instant startedAt;

    // -------------------------------------------------------------------------
    // Mutable runtime state (replaces legacy static fields)
    // -------------------------------------------------------------------------

    /**
     * General-purpose per-run state bag.
     *
     * <p>Replaces any miscellaneous static maps in JMeter that stored keyed
     * per-run objects. Concurrent access is safe because this is a
     * {@link ConcurrentHashMap}.
     */
    private final ConcurrentHashMap<String, Object> runState;

    /**
     * Open result writers keyed by their logical name.
     *
     * <p>Replaces {@code ResultCollector}'s static file-handle map. Each entry
     * is an {@link AutoCloseable} so callers can close it when the run ends.
     * Concurrent access is safe because this is a {@link ConcurrentHashMap}.
     */
    private final ConcurrentHashMap<String, AutoCloseable> resultWriters;

    /**
     * {@code true} while the engine is actively running the plan.
     *
     * <p>Replaces {@code StandardJMeterEngine.engine} static boolean. Marked
     * {@code volatile} so a stop-signal written on one thread is immediately
     * visible to the engine thread.
     */
    private volatile boolean running;

    /**
     * Lifecycle status of this run.
     *
     * <p>Marked {@code volatile} so status updates from the engine thread are
     * immediately visible to monitoring threads.
     */
    private volatile TestRunStatus status;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    private TestRunContext(Builder builder) {
        this.runId           = Objects.requireNonNull(builder.runId,    "runId must not be null");
        this.planPath        = Objects.requireNonNull(builder.planPath, "planPath must not be null");
        this.virtualUsers    = builder.virtualUsers;
        this.durationSeconds = builder.durationSeconds;
        this.uiBridge        = Objects.requireNonNull(builder.uiBridge, "uiBridge must not be null");
        this.runProperties   = builder.runProperties != null ? builder.runProperties : new Properties();
        this.startedAt       = builder.startedAt != null ? builder.startedAt : Instant.now();
        this.runState        = new ConcurrentHashMap<>();
        this.resultWriters   = new ConcurrentHashMap<>();
        this.running         = false;
        this.status          = TestRunStatus.CREATED;
    }

    // -------------------------------------------------------------------------
    // Lifecycle status enum
    // -------------------------------------------------------------------------

    /**
     * Lifecycle states a test run passes through.
     */
    public enum TestRunStatus {
        /** Context created, run not yet started. */
        CREATED,
        /** Run is actively executing. */
        RUNNING,
        /** Stop has been requested; engine is winding down. */
        STOPPING,
        /** Run has completed normally. */
        STOPPED,
        /** Run terminated due to an error. */
        ERROR
    }

    // -------------------------------------------------------------------------
    // Immutable accessors
    // -------------------------------------------------------------------------

    /** Unique identifier for this test run. */
    public String getRunId() {
        return runId;
    }

    /** Absolute path to the JMX test plan file. */
    public String getPlanPath() {
        return planPath;
    }

    /** Number of virtual users (threads) to simulate. */
    public int getVirtualUsers() {
        return virtualUsers;
    }

    /** Maximum duration of the run in seconds; 0 means run until plan completes. */
    public long getDurationSeconds() {
        return durationSeconds;
    }

    /** UI bridge to receive engine callbacks for this run. */
    public UIBridge getUiBridge() {
        return uiBridge;
    }

    /**
     * Per-run JMeter properties.
     *
     * <p>Returns a defensive copy to protect the internal state from external
     * mutation via the returned object.
     */
    public Properties getRunProperties() {
        Properties copy = new Properties();
        copy.putAll(runProperties);
        return copy;
    }

    /** Timestamp when this context was constructed (proxy for run-start time). */
    public Instant getStartedAt() {
        return startedAt;
    }

    // -------------------------------------------------------------------------
    // Mutable state accessors
    // -------------------------------------------------------------------------

    /** {@code true} while the engine is actively executing the plan. */
    public boolean isRunning() {
        return running;
    }

    /** Sets the running flag. Thread-safe due to {@code volatile}. */
    public void setRunning(boolean running) {
        this.running = running;
    }

    /** Current lifecycle status. Thread-safe due to {@code volatile}. */
    public TestRunStatus getStatus() {
        return status;
    }

    /** Transitions to a new status. Thread-safe due to {@code volatile}. */
    public void setStatus(TestRunStatus status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    // -------------------------------------------------------------------------
    // runState — general-purpose per-run state bag
    // -------------------------------------------------------------------------

    /**
     * Stores a value in the per-run state bag.
     *
     * @param key   non-null key
     * @param value non-null value
     */
    public void putState(String key, Object value) {
        runState.put(
                Objects.requireNonNull(key,   "key must not be null"),
                Objects.requireNonNull(value, "value must not be null"));
    }

    /**
     * Returns the value associated with {@code key}, or {@code null} if absent.
     *
     * @param key non-null key
     */
    public Object getState(String key) {
        return runState.get(Objects.requireNonNull(key, "key must not be null"));
    }

    /**
     * Removes and returns the value associated with {@code key}.
     *
     * @param key non-null key
     * @return the removed value, or {@code null} if the key was not present
     */
    public Object removeState(String key) {
        return runState.remove(Objects.requireNonNull(key, "key must not be null"));
    }

    /** Returns an unmodifiable view of the current run-state map. */
    public Map<String, Object> getRunState() {
        return Collections.unmodifiableMap(runState);
    }

    // -------------------------------------------------------------------------
    // resultWriters — open result-file handles
    // -------------------------------------------------------------------------

    /**
     * Registers an open result writer for this run.
     *
     * <p>Replaces the static file-handle map previously kept in ResultCollector.
     *
     * @param name   logical name for the writer (e.g. file path)
     * @param writer the writer to register; must be non-null
     */
    public void putResultWriter(String name, AutoCloseable writer) {
        resultWriters.put(
                Objects.requireNonNull(name,   "name must not be null"),
                Objects.requireNonNull(writer, "writer must not be null"));
    }

    /**
     * Returns the writer registered under {@code name}, or {@code null} if absent.
     *
     * @param name logical name used during {@link #putResultWriter}
     */
    public AutoCloseable getResultWriter(String name) {
        return resultWriters.get(Objects.requireNonNull(name, "name must not be null"));
    }

    /**
     * Removes and returns the writer registered under {@code name}.
     *
     * @param name logical name used during {@link #putResultWriter}
     * @return the removed writer, or {@code null} if not present
     */
    public AutoCloseable removeResultWriter(String name) {
        return resultWriters.remove(Objects.requireNonNull(name, "name must not be null"));
    }

    /** Returns the number of currently registered result writers. */
    public int resultWriterCount() {
        return resultWriters.size();
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /** Creates a new builder for {@link TestRunContext}. */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link TestRunContext}. */
    public static final class Builder {
        private String runId;
        private String planPath;
        private int virtualUsers = 1;
        private long durationSeconds = 0;
        private UIBridge uiBridge;
        private Properties runProperties;
        private Instant startedAt;

        private Builder() {}

        public Builder runId(String runId) {
            this.runId = runId;
            return this;
        }

        public Builder planPath(String planPath) {
            this.planPath = planPath;
            return this;
        }

        public Builder virtualUsers(int virtualUsers) {
            if (virtualUsers < 1) {
                throw new IllegalArgumentException("virtualUsers must be >= 1, got: " + virtualUsers);
            }
            this.virtualUsers = virtualUsers;
            return this;
        }

        public Builder durationSeconds(long durationSeconds) {
            if (durationSeconds < 0) {
                throw new IllegalArgumentException("durationSeconds must be >= 0, got: " + durationSeconds);
            }
            this.durationSeconds = durationSeconds;
            return this;
        }

        public Builder uiBridge(UIBridge uiBridge) {
            this.uiBridge = uiBridge;
            return this;
        }

        public Builder runProperties(Properties runProperties) {
            this.runProperties = runProperties;
            return this;
        }

        public Builder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public TestRunContext build() {
            return new TestRunContext(this);
        }
    }

    // -------------------------------------------------------------------------
    // Object overrides
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TestRunContext)) return false;
        TestRunContext other = (TestRunContext) obj;
        return Objects.equals(runId, other.runId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(runId);
    }

    @Override
    public String toString() {
        return "TestRunContext{runId='" + runId + '\''
                + ", planPath='" + planPath + '\''
                + ", virtualUsers=" + virtualUsers
                + ", durationSeconds=" + durationSeconds
                + ", status=" + status
                + '}';
    }
}
