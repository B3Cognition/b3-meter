package com.jmeternext.engine.adapter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Spawns N worker-node processes on the local machine for distributed testing
 * without requiring Docker or remote infrastructure.
 *
 * <p>Each worker is started via {@link ProcessBuilder} and assigned a unique port:
 * {@code basePort + workerIndex} (e.g., 9090, 9091, 9092, ...). After spawning,
 * the spawner waits briefly and verifies each worker is alive.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * var spawner = new LocalWorkerSpawner();
 * List<Process> workers = spawner.spawn(3, 9090, Path.of("worker-node.jar"));
 * try {
 *     // ... run distributed test ...
 * } finally {
 *     spawner.stopAll();
 * }
 * }</pre>
 *
 * <p>Uses {@link ReentrantLock} instead of {@code synchronized} to avoid
 * virtual-thread pinning.
 */
public class LocalWorkerSpawner {

    private static final Logger LOG = Logger.getLogger(LocalWorkerSpawner.class.getName());

    /** Milliseconds to wait per worker for startup before checking liveness. */
    private static final long STARTUP_WAIT_MS = 2000;

    private final ReentrantLock lock = new ReentrantLock();
    private final List<Process> processes = new ArrayList<>();

    /**
     * Spawns {@code count} worker-node processes on the local machine.
     *
     * <p>Each worker is started with:
     * {@code java -jar <workerJar> --port=<basePort + index>}
     *
     * <p>The method waits {@value #STARTUP_WAIT_MS}ms per worker and checks
     * that each process is alive. If a worker fails to start, previously spawned
     * workers are stopped and an {@link IOException} is thrown.
     *
     * @param count     number of workers to spawn; must be &gt; 0
     * @param basePort  base port number; workers use basePort, basePort+1, ...
     * @param workerJar path to the worker-node JAR file
     * @return unmodifiable list of spawned {@link Process} handles
     * @throws IOException              if a worker process fails to start
     * @throws IllegalArgumentException if count &lt;= 0 or basePort &lt; 1
     */
    public List<Process> spawn(int count, int basePort, Path workerJar) throws IOException {
        Objects.requireNonNull(workerJar, "workerJar must not be null");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be > 0, got: " + count);
        }
        if (basePort < 1) {
            throw new IllegalArgumentException("basePort must be >= 1, got: " + basePort);
        }

        lock.lock();
        try {
            for (int i = 0; i < count; i++) {
                int port = basePort + i;
                LOG.info(() -> "Spawning worker on port " + port + " from " + workerJar);

                ProcessBuilder pb = new ProcessBuilder(
                        "java", "-jar", workerJar.toAbsolutePath().toString(),
                        "--port=" + port
                );
                pb.redirectErrorStream(true);
                pb.inheritIO();

                Process process = pb.start();

                // Wait for startup
                try {
                    Thread.sleep(STARTUP_WAIT_MS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    stopAllInternal();
                    throw new IOException("Interrupted while waiting for worker startup", ex);
                }

                if (!process.isAlive()) {
                    int exitVal = process.exitValue();
                    stopAllInternal();
                    throw new IOException(
                            "Worker on port " + port + " failed to start (exit code: " + exitVal + ")");
                }

                processes.add(process);
                LOG.info(() -> "Worker on port " + port + " started (pid: " + process.pid() + ")");
            }

            return Collections.unmodifiableList(new ArrayList<>(processes));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Forcefully stops all spawned worker processes.
     *
     * <p>Calls {@link Process#destroyForcibly()} on each process.
     * Safe to call multiple times.
     */
    public void stopAll() {
        lock.lock();
        try {
            stopAllInternal();
        } finally {
            lock.unlock();
        }
    }

    private void stopAllInternal() {
        for (Process p : processes) {
            try {
                if (p.isAlive()) {
                    p.destroyForcibly();
                    LOG.info(() -> "Stopped worker (pid: " + p.pid() + ")");
                }
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "Failed to stop worker (pid: " + p.pid() + ")", ex);
            }
        }
        processes.clear();
    }
}
