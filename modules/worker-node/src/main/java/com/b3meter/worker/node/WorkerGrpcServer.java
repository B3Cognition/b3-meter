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
package com.b3meter.worker.node;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lifecycle manager for the worker node's gRPC server.
 *
 * <p>Wraps a Netty-backed {@link Server} and exposes simple {@link #start} / {@link #stop}
 * methods.  The server hosts a single service — {@link WorkerServiceImpl} — and binds to
 * the specified TCP port.
 *
 * <p>Usage:
 * <pre>{@code
 * WorkerGrpcServer server = new WorkerGrpcServer();
 * server.start(50051);
 * server.awaitTermination();   // blocks until the server is stopped
 * }</pre>
 *
 * <p>Thread-safety: {@link #start} and {@link #stop} must be called from the same thread
 * (or with external synchronisation). The {@link #awaitTermination} method may be called
 * from any thread.
 */
public class WorkerGrpcServer {

    private static final Logger LOG = Logger.getLogger(WorkerGrpcServer.class.getName());

    /** Maximum time (seconds) to wait for in-flight RPCs to complete on graceful shutdown. */
    private static final long GRACEFUL_SHUTDOWN_TIMEOUT_S = 30L;

    private Server server;
    private WorkerServiceImpl service;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Builds and starts the gRPC server on the given TCP {@code port}.
     *
     * @param port TCP port to listen on; must be in the range [1, 65535]
     * @throws IOException              if the server cannot bind to the port
     * @throws IllegalArgumentException if {@code port} is out of range
     * @throws IllegalStateException    if the server has already been started
     */
    public void start(int port) throws IOException {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be in [1, 65535], got: " + port);
        }
        if (server != null) {
            throw new IllegalStateException("Server is already running on port " + server.getPort());
        }

        service = new WorkerServiceImpl();
        server = ServerBuilder.forPort(port)
                .addService(service)
                .build()
                .start();

        LOG.log(Level.INFO, "WorkerGrpcServer started on port {0}", port);

        // Register a JVM shutdown hook so the server is cleanly terminated when
        // the process receives SIGTERM / SIGINT.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("JVM shutdown hook: stopping WorkerGrpcServer");
            try {
                WorkerGrpcServer.this.stop();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                LOG.log(Level.WARNING, "Interrupted during shutdown", ex);
            }
        }, "worker-grpc-shutdown-hook"));
    }

    /**
     * Initiates a graceful shutdown of the gRPC server.
     *
     * <p>New RPCs are immediately rejected. In-flight RPCs are given
     * {@value #GRACEFUL_SHUTDOWN_TIMEOUT_S} seconds to complete; after that the server
     * is forcefully terminated.
     *
     * @throws InterruptedException if the calling thread is interrupted while waiting
     * @throws IllegalStateException if {@link #start} has not been called
     */
    public void stop() throws InterruptedException {
        if (server == null) {
            throw new IllegalStateException("Server has not been started");
        }

        LOG.info("WorkerGrpcServer shutting down…");
        server.shutdown();

        boolean terminated = server.awaitTermination(GRACEFUL_SHUTDOWN_TIMEOUT_S, TimeUnit.SECONDS);
        if (!terminated) {
            LOG.warning("Graceful shutdown timed out — forcing shutdown");
            server.shutdownNow();
            server.awaitTermination(5, TimeUnit.SECONDS);
        }

        if (service != null) {
            service.shutdown();
        }

        LOG.info("WorkerGrpcServer stopped");
    }

    /**
     * Blocks until the server is shut down.
     *
     * <p>Intended for use in {@code main()} after {@link #start} to keep the process
     * alive until the server terminates.
     *
     * @throws InterruptedException if the calling thread is interrupted
     * @throws IllegalStateException if {@link #start} has not been called
     */
    public void awaitTermination() throws InterruptedException {
        Objects.requireNonNull(server, "Server has not been started");
        server.awaitTermination();
    }

    /**
     * Returns the port the server is listening on after {@link #start} has been called.
     *
     * @return the bound port number; -1 if the server is not yet started
     */
    public int getPort() {
        return server == null ? -1 : server.getPort();
    }

    /**
     * Returns {@code true} if the server was started and has not yet been shut down.
     *
     * @return {@code true} while the server is running
     */
    public boolean isRunning() {
        return server != null && !server.isShutdown();
    }
}
