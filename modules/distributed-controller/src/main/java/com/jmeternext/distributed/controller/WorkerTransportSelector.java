package com.jmeternext.distributed.controller;

import com.jmeternext.worker.proto.HealthStatus;
import com.jmeternext.worker.proto.WorkerState;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Selects the best available {@link WorkerTransport} for a given worker node.
 *
 * <p>The selection algorithm is:
 * <ol>
 *   <li>Try a gRPC {@code GetHealth} probe on {@code host:grpcPort}.</li>
 *   <li>If the probe succeeds (returns any state other than
 *       {@code WORKER_STATE_UNSPECIFIED}), return a {@link GrpcWorkerTransport}.</li>
 *   <li>Otherwise, attempt a WebSocket connection to {@code host:wsPort}.</li>
 *   <li>If the WebSocket connection succeeds, return a {@link WebSocketWorkerTransport}.</li>
 *   <li>If neither is reachable, throw {@link TransportUnavailableException}.</li>
 * </ol>
 *
 * <p>This class is the primary entry point for obtaining a transport when the controller
 * does not know in advance whether gRPC is accessible (e.g. behind a corporate proxy).
 *
 * <p>Thread-safety: {@link #select} may be called from multiple threads concurrently.
 * Each call is independent and returns its own transport instance.
 *
 * <h3>Usage example</h3>
 * <pre>{@code
 * WorkerTransportSelector selector = new WorkerTransportSelector();
 * WorkerTransport transport = selector.select("worker-host", 50051, 8080);
 * ConfigureAck ack = transport.configure(plan);
 * }</pre>
 */
public class WorkerTransportSelector {

    private static final Logger LOG = Logger.getLogger(WorkerTransportSelector.class.getName());

    /**
     * Timeout applied to the gRPC health probe.  Kept short so the fallback is fast.
     */
    static final long GRPC_PROBE_TIMEOUT_SECONDS = 5L;

    /**
     * Timeout applied to the WebSocket connect + handshake probe.
     */
    static final int WS_PROBE_TIMEOUT_MS = WebSocketWorkerTransport.CONNECT_TIMEOUT_MS;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Probes the worker and returns the best available transport.
     *
     * @param workerId the logical worker identifier (used for logging and in the
     *                 returned transport)
     * @param host     hostname or IP of the worker node
     * @param grpcPort gRPC port to probe first
     * @param wsPort   WebSocket port to use if gRPC is unavailable
     * @return a connected, ready-to-use {@link WorkerTransport}
     * @throws TransportUnavailableException if neither gRPC nor WebSocket is reachable
     */
    public WorkerTransport select(String workerId, String host, int grpcPort, int wsPort) {
        Objects.requireNonNull(workerId, "workerId");
        Objects.requireNonNull(host, "host");

        LOG.log(Level.INFO,
                "Probing transports for worker={0} at {1} (gRPC:{2}, WS:{3})",
                new Object[]{workerId, host, grpcPort, wsPort});

        // --- Phase 1: gRPC health probe ---
        if (isGrpcReachable(workerId, host, grpcPort)) {
            LOG.log(Level.INFO,
                    "gRPC reachable for worker={0}; using GrpcWorkerTransport", workerId);
            WorkerClient client = new WorkerClient(workerId, host, grpcPort);
            return new GrpcWorkerTransport(client);
        }

        LOG.log(Level.WARNING,
                "gRPC probe failed for worker={0}; trying WebSocket fallback on port {1}",
                new Object[]{workerId, wsPort});

        // --- Phase 2: WebSocket probe ---
        WebSocketWorkerTransport wsTransport =
                new WebSocketWorkerTransport(workerId, host, wsPort);
        try {
            wsTransport.connect();
        } catch (IOException ex) {
            throw new TransportUnavailableException(
                    "Neither gRPC nor WebSocket is reachable for worker " + workerId
                            + " at " + host + " (gRPC:" + grpcPort + ", WS:" + wsPort + "): "
                            + ex.getMessage(),
                    ex);
        }

        // Confirm the WebSocket health probe succeeds
        HealthStatus wsHealth = wsTransport.getHealth();
        if (wsHealth.getState() == WorkerState.WORKER_STATE_UNSPECIFIED) {
            wsTransport.close();
            throw new TransportUnavailableException(
                    "WebSocket connected to worker=" + workerId
                            + " but health probe returned UNSPECIFIED state; "
                            + "worker may not be ready");
        }

        LOG.log(Level.INFO,
                "WebSocket fallback active for worker={0}; state={1}",
                new Object[]{workerId, wsHealth.getState()});
        return wsTransport;
    }

    // -------------------------------------------------------------------------
    // Internal probe helpers
    // -------------------------------------------------------------------------

    /**
     * Attempts a gRPC {@code GetHealth} RPC with a short deadline.
     *
     * @return {@code true} if the health probe returned a non-UNSPECIFIED state
     */
    boolean isGrpcReachable(String workerId, String host, int grpcPort) {
        ManagedChannel channel = null;
        try {
            channel = ManagedChannelBuilder.forAddress(host, grpcPort)
                    .usePlaintext()
                    .build();
            WorkerClient client = new WorkerClient(workerId, channel);
            HealthStatus status = client.getHealth("");
            return status.getState() != WorkerState.WORKER_STATE_UNSPECIFIED;
        } catch (Exception ex) {
            LOG.log(Level.FINE,
                    "gRPC probe failed for worker={0}: {1}", new Object[]{workerId, ex.getMessage()});
            return false;
        } finally {
            if (channel != null) {
                try {
                    channel.shutdown().awaitTermination(GRPC_PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    channel.shutdownNow();
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Exception type
    // -------------------------------------------------------------------------

    /**
     * Thrown when {@link WorkerTransportSelector#select} cannot reach the worker via
     * any supported transport.
     */
    public static final class TransportUnavailableException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        /**
         * Creates a new exception with the given message.
         *
         * @param message human-readable description of why no transport was available
         */
        public TransportUnavailableException(String message) {
            super(message);
        }

        /**
         * Creates a new exception with the given message and cause.
         *
         * @param message human-readable description
         * @param cause   the underlying exception
         */
        public TransportUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
