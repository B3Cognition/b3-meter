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
package com.b3meter.distributed.controller;

import com.b3meter.engine.service.SampleBucket;
import com.b3meter.engine.service.SampleBucketConsumer;
import com.b3meter.worker.proto.ConfigureAck;
import com.b3meter.worker.proto.HealthRequest;
import com.b3meter.worker.proto.HealthStatus;
import com.b3meter.worker.proto.SampleResultBatch;
import com.b3meter.worker.proto.StartAck;
import com.b3meter.worker.proto.StartMessage;
import com.b3meter.worker.proto.StopAck;
import com.b3meter.worker.proto.StopMessage;
import com.b3meter.worker.proto.TestPlanMessage;
import com.b3meter.worker.proto.WorkerState;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link WorkerTransport} implementation that communicates with a worker node over
 * a WebSocket connection, using the binary framing defined in
 * {@code contracts/websocket-transport.md}.
 *
 * <h2>Wire format (binary frames)</h2>
 * <pre>
 *   bytes 0–3  : big-endian uint32 — total message length (including the 8-byte header)
 *   bytes 4–7  : big-endian uint32 — message type
 *   bytes 8+   : protobuf-serialised message body
 * </pre>
 *
 * <h2>Reconnection policy</h2>
 * If the connection drops, the client performs automatic reconnection with exponential
 * back-off: 1 s, 2 s, 4 s, 8 s, 16 s, then 30 s cap for all subsequent attempts.
 *
 * <h2>Subprotocol</h2>
 * Every connection advertises {@value #SUBPROTOCOL}; connections whose servers do not
 * echo the subprotocol are rejected.
 */
public final class WebSocketWorkerTransport implements WorkerTransport {

    private static final Logger LOG = Logger.getLogger(WebSocketWorkerTransport.class.getName());

    /** WebSocket subprotocol negotiated on every connection. */
    public static final String SUBPROTOCOL = "jmeter-worker/v1";

    /** RFC-6455 GUID used to derive the Sec-WebSocket-Accept header. */
    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    /** WebSocket binary opcode. */
    private static final int OPCODE_BINARY = 0x2;

    /** WebSocket close opcode. */
    private static final int OPCODE_CLOSE = 0x8;

    /** Reconnection back-off delays in seconds; last entry is the cap. */
    private static final long[] BACKOFF_SECONDS = {1, 2, 4, 8, 16, 30};

    // -------------------------------------------------------------------------
    // Message type constants (mirror of the server-side constants)
    // -------------------------------------------------------------------------

    static final int MSG_CONFIGURE_REQUEST   = 1;
    static final int MSG_CONFIGURE_ACK       = 2;
    static final int MSG_START_REQUEST       = 3;
    static final int MSG_START_ACK           = 4;
    static final int MSG_STOP_REQUEST        = 5;
    static final int MSG_STOP_ACK            = 6;
    static final int MSG_SAMPLE_RESULT_BATCH = 7;
    static final int MSG_HEALTH_REQUEST      = 8;
    static final int MSG_HEALTH_STATUS       = 9;

    // -------------------------------------------------------------------------
    // Connect timeout
    // -------------------------------------------------------------------------

    /** TCP connect timeout in milliseconds. */
    static final int CONNECT_TIMEOUT_MS = 5_000;

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final String workerId;
    private final String host;
    private final int port;

    /** Current open socket (may be null when disconnected). */
    private volatile Socket socket;
    private volatile InputStream socketIn;
    private volatile OutputStream socketOut;

    /** {@code true} once {@link #close()} has been called. */
    private final AtomicBoolean terminated = new AtomicBoolean(false);

    /** {@code true} while a WebSocket connection is established. */
    private final AtomicBoolean connected = new AtomicBoolean(false);

    /** Pending request futures, keyed by expected response message type. */
    private final java.util.concurrent.ConcurrentHashMap<Integer, CompletableFuture<byte[]>>
            pendingResponses = new java.util.concurrent.ConcurrentHashMap<>();

    /** Consumer for streamed sample results (set when streamResults is active). */
    private volatile SampleBucketConsumer resultConsumer;

    /** Thread that drives the receive loop. */
    private volatile Thread receiveThread;

    /**
     * Creates a transport that will connect to the worker at {@code host:port}.
     *
     * @param workerId logical identifier of the worker (used for logging)
     * @param host     hostname or IP address of the worker node
     * @param port     WebSocket port of the worker node
     */
    public WebSocketWorkerTransport(String workerId, String host, int port) {
        this.workerId = Objects.requireNonNull(workerId, "workerId");
        this.host = Objects.requireNonNull(host, "host");
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be in [1, 65535], got: " + port);
        }
        this.port = port;
    }

    // -------------------------------------------------------------------------
    // Connection management
    // -------------------------------------------------------------------------

    /**
     * Establishes the WebSocket connection to the worker.
     *
     * <p>Must be called before any other method. If the connection fails, the
     * caller should call {@link #connectWithRetry()} or use
     * {@link WorkerTransportSelector} which probes transports before returning one.
     *
     * @throws IOException if the TCP connection or WebSocket upgrade fails
     */
    public void connect() throws IOException {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        this.socket = s;
        this.socketIn = s.getInputStream();
        this.socketOut = s.getOutputStream();

        performHandshake(socketIn, socketOut);
        connected.set(true);

        receiveThread = new Thread(this::receiveLoop, "ws-ctrl-recv-" + workerId);
        receiveThread.setDaemon(true);
        receiveThread.start();

        LOG.log(Level.INFO, "WebSocket connected to worker={0} at {1}:{2}",
                new Object[]{workerId, host, port});
    }

    /**
     * Attempts to connect with exponential back-off, retrying indefinitely until either
     * a connection is established or {@link #close()} is called.
     */
    public void connectWithRetry() {
        int attempt = 0;
        while (!terminated.get()) {
            try {
                connect();
                return;
            } catch (IOException ex) {
                long delaySecs = BACKOFF_SECONDS[Math.min(attempt, BACKOFF_SECONDS.length - 1)];
                LOG.log(Level.WARNING,
                        "WebSocket connect attempt {0} failed for worker={1}: {2}. "
                                + "Retrying in {3}s",
                        new Object[]{attempt + 1, workerId, ex.getMessage(), delaySecs});
                attempt++;
                try {
                    Thread.sleep(delaySecs * 1_000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // WorkerTransport implementation
    // -------------------------------------------------------------------------

    @Override
    public ConfigureAck configure(TestPlanMessage plan) {
        byte[] responseBody = sendAndWait(MSG_CONFIGURE_REQUEST, plan.toByteArray(), MSG_CONFIGURE_ACK);
        try {
            return responseBody != null
                    ? ConfigureAck.parseFrom(responseBody)
                    : ConfigureAck.newBuilder()
                            .setAccepted(false)
                            .setMessage("No response from worker")
                            .build();
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Failed to parse ConfigureAck", ex);
            return ConfigureAck.newBuilder().setAccepted(false)
                    .setMessage("Parse error: " + ex.getMessage()).build();
        }
    }

    @Override
    public StartAck start(StartMessage msg) {
        byte[] responseBody = sendAndWait(MSG_START_REQUEST, msg.toByteArray(), MSG_START_ACK);
        try {
            return responseBody != null
                    ? StartAck.parseFrom(responseBody)
                    : StartAck.newBuilder()
                            .setAccepted(false)
                            .setMessage("No response from worker")
                            .build();
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Failed to parse StartAck", ex);
            return StartAck.newBuilder().setAccepted(false)
                    .setMessage("Parse error: " + ex.getMessage()).build();
        }
    }

    @Override
    public StopAck stop() {
        StopMessage msg = StopMessage.newBuilder().build();
        byte[] responseBody = sendAndWait(MSG_STOP_REQUEST, msg.toByteArray(), MSG_STOP_ACK);
        try {
            return responseBody != null
                    ? StopAck.parseFrom(responseBody)
                    : StopAck.newBuilder()
                            .setAccepted(false)
                            .setMessage("No response from worker")
                            .build();
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Failed to parse StopAck", ex);
            return StopAck.newBuilder().setAccepted(false)
                    .setMessage("Parse error: " + ex.getMessage()).build();
        }
    }

    /**
     * Stops a specific run.
     *
     * @param runId the run to stop
     * @return the worker's acknowledgement
     */
    public StopAck stop(String runId) {
        StopMessage msg = StopMessage.newBuilder().setRunId(runId).build();
        byte[] responseBody = sendAndWait(MSG_STOP_REQUEST, msg.toByteArray(), MSG_STOP_ACK);
        try {
            return responseBody != null
                    ? StopAck.parseFrom(responseBody)
                    : StopAck.newBuilder().setAccepted(false)
                            .setMessage("No response from worker").build();
        } catch (Exception ex) {
            return StopAck.newBuilder().setAccepted(false)
                    .setMessage("Parse error: " + ex.getMessage()).build();
        }
    }

    @Override
    public void streamResults(String runId, SampleBucketConsumer consumer) {
        this.resultConsumer = consumer;
        // No explicit RPC needed: the worker pushes SampleResultBatch frames after Start.
        LOG.log(Level.INFO, "StreamResults consumer registered for runId={0} via WebSocket", runId);
    }

    @Override
    public HealthStatus getHealth() {
        HealthRequest req = HealthRequest.getDefaultInstance();
        byte[] responseBody = sendAndWait(MSG_HEALTH_REQUEST, req.toByteArray(), MSG_HEALTH_STATUS);
        try {
            return responseBody != null
                    ? HealthStatus.parseFrom(responseBody)
                    : HealthStatus.newBuilder()
                            .setState(WorkerState.WORKER_STATE_UNSPECIFIED)
                            .setMessage("No health response from worker")
                            .setTimestampMs(System.currentTimeMillis())
                            .build();
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Failed to parse HealthStatus", ex);
            return HealthStatus.newBuilder()
                    .setState(WorkerState.WORKER_STATE_UNSPECIFIED)
                    .setMessage("Parse error: " + ex.getMessage())
                    .setTimestampMs(System.currentTimeMillis())
                    .build();
        }
    }

    @Override
    public void close() {
        if (terminated.compareAndSet(false, true)) {
            connected.set(false);
            try {
                sendCloseFrame();
            } catch (Exception ignored) {}
            try {
                Socket s = socket;
                if (s != null) s.close();
            } catch (IOException ignored) {}
            LOG.log(Level.INFO, "WebSocketWorkerTransport closed for worker={0}", workerId);
        }
    }

    // -------------------------------------------------------------------------
    // Send + wait helpers
    // -------------------------------------------------------------------------

    /**
     * Sends a message and blocks (up to 30 s) waiting for the response of the given type.
     *
     * @param reqType   message type of the request
     * @param protoBody serialised protobuf body to send
     * @param respType  expected response message type
     * @return the response body bytes, or {@code null} on timeout / error
     */
    private byte[] sendAndWait(int reqType, byte[] protoBody, int respType) {
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        pendingResponses.put(respType, future);
        try {
            sendFrame(reqType, protoBody);
            return future.get(30, TimeUnit.SECONDS);
        } catch (Exception ex) {
            LOG.log(Level.WARNING,
                    "sendAndWait failed for reqType={0}, worker={1}: {2}",
                    new Object[]{reqType, workerId, ex.getMessage()});
            return null;
        } finally {
            pendingResponses.remove(respType, future);
        }
    }

    // -------------------------------------------------------------------------
    // Frame encoding/sending
    // -------------------------------------------------------------------------

    /**
     * Encodes and sends a binary message frame over the WebSocket connection.
     *
     * <p>Frame layout:
     * <pre>
     *   [4-byte big-endian total length][4-byte big-endian message type][protobuf body]
     * </pre>
     *
     * <p>Outgoing client frames are masked as required by RFC 6455 §5.1.
     *
     * @param msgType   message type constant
     * @param protoBody serialised protobuf bytes
     */
    synchronized void sendFrame(int msgType, byte[] protoBody) throws IOException {
        if (!connected.get()) throw new IOException("Not connected to worker " + workerId);

        int total = 8 + protoBody.length;
        byte[] payload = new byte[total];
        writeUint32(payload, 0, total);
        writeUint32(payload, 4, msgType);
        if (protoBody.length > 0) {
            System.arraycopy(protoBody, 0, payload, 8, protoBody.length);
        }

        // Client frames MUST be masked (RFC 6455 §5.1)
        byte[] maskKey = generateMaskKey();
        byte[] maskedPayload = new byte[payload.length];
        for (int i = 0; i < payload.length; i++) {
            maskedPayload[i] = (byte) (payload[i] ^ maskKey[i % 4]);
        }

        ByteArrayOutputStream buf = new ByteArrayOutputStream(10 + maskedPayload.length);
        buf.write(0x82); // FIN=1, opcode=2 (binary)
        int len = maskedPayload.length;
        if (len < 126) {
            buf.write(0x80 | len); // MASK=1
        } else if (len < 65536) {
            buf.write(0x80 | 126);
            buf.write((len >> 8) & 0xFF);
            buf.write(len & 0xFF);
        } else {
            buf.write(0x80 | 127);
            for (int i = 7; i >= 0; i--) {
                buf.write((len >> (8 * i)) & 0xFF);
            }
        }
        buf.write(maskKey);
        buf.write(maskedPayload);

        socketOut.write(buf.toByteArray());
        socketOut.flush();
    }

    private void sendCloseFrame() throws IOException {
        // FIN=1, opcode=8, MASK=1, length=2, mask+status
        byte[] maskKey = generateMaskKey();
        byte[] status = {0x03, (byte) 0xE8}; // 1000 = normal closure
        byte[] maskedStatus = {
                (byte) (status[0] ^ maskKey[0]),
                (byte) (status[1] ^ maskKey[1])
        };
        byte[] frame = {
                (byte) 0x88,       // FIN=1, opcode=close
                (byte) 0x82,       // MASK=1, length=2
                maskKey[0], maskKey[1], maskKey[2], maskKey[3],
                maskedStatus[0], maskedStatus[1]
        };
        OutputStream o = socketOut;
        if (o != null) {
            o.write(frame);
            o.flush();
        }
    }

    // -------------------------------------------------------------------------
    // Receive loop
    // -------------------------------------------------------------------------

    private void receiveLoop() {
        while (!terminated.get() && connected.get()) {
            try {
                byte[] payload = readFrame();
                if (payload == null) {
                    // Connection closed by peer — attempt reconnect
                    handleDisconnect();
                    return;
                }
                if (payload.length > 0) {
                    dispatch(payload);
                }
            } catch (IOException ex) {
                if (!terminated.get()) {
                    LOG.log(Level.WARNING,
                            "Receive error from worker={0}: {1}. Will reconnect.",
                            new Object[]{workerId, ex.getMessage()});
                    handleDisconnect();
                }
                return;
            }
        }
    }

    /**
     * Handles a dropped connection: marks the transport as disconnected, completes any
     * pending futures exceptionally, and schedules a reconnect attempt.
     */
    private void handleDisconnect() {
        connected.set(false);
        // Complete pending futures with an exception so callers don't hang
        RuntimeException ex = new RuntimeException("WebSocket connection lost to worker " + workerId);
        pendingResponses.values().forEach(f -> f.completeExceptionally(ex));
        pendingResponses.clear();

        if (!terminated.get()) {
            LOG.log(Level.INFO,
                    "WebSocket disconnected from worker={0}; reconnecting with back-off", workerId);
            Thread reconnectThread = new Thread(this::reconnect, "ws-ctrl-reconnect-" + workerId);
            reconnectThread.setDaemon(true);
            reconnectThread.start();
        }
    }

    /**
     * Performs a reconnect + re-sync as specified in the wire contract:
     * <ol>
     *   <li>Reconnects with exponential back-off.</li>
     *   <li>The controller is responsible for re-sending ConfigureRequest to re-sync state.</li>
     * </ol>
     */
    private void reconnect() {
        connectWithRetry();
        LOG.log(Level.INFO,
                "WebSocket reconnected to worker={0}. Caller should re-configure if test is active.",
                workerId);
    }

    // -------------------------------------------------------------------------
    // Frame reading
    // -------------------------------------------------------------------------

    /**
     * Reads one complete WebSocket frame from the input stream.
     *
     * @return the unmasked (server frames are not masked) payload, or {@code null} on close
     */
    private byte[] readFrame() throws IOException {
        InputStream in = socketIn;
        if (in == null) throw new IOException("Not connected");

        int b0 = in.read();
        int b1 = in.read();
        if (b0 < 0 || b1 < 0) return null;

        int opcode = b0 & 0x0F;
        boolean masked = (b1 & 0x80) != 0;
        long payloadLen = b1 & 0x7F;

        if (payloadLen == 126) {
            byte[] ext = readExact(in, 2);
            payloadLen = ((ext[0] & 0xFFL) << 8) | (ext[1] & 0xFFL);
        } else if (payloadLen == 127) {
            byte[] ext = readExact(in, 8);
            payloadLen = 0;
            for (int i = 0; i < 8; i++) {
                payloadLen = (payloadLen << 8) | (ext[i] & 0xFFL);
            }
        }

        byte[] maskKey = masked ? readExact(in, 4) : null;
        byte[] payload = readExact(in, (int) payloadLen);

        if (masked && maskKey != null) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] ^= maskKey[i % 4];
            }
        }

        if (opcode == OPCODE_CLOSE) {
            return null; // peer closing
        }
        if (opcode != OPCODE_BINARY) {
            return new byte[0]; // skip non-binary frames
        }

        // Validate declared length
        if (payload.length >= 4) {
            long declared = readUint32(payload, 0);
            if (declared != payload.length) {
                LOG.log(Level.WARNING,
                        "Frame length mismatch from worker={0}: declared={1}, actual={2}",
                        new Object[]{workerId, declared, payload.length});
                // Per spec: close with 1002 (Protocol Error) on length mismatch
                sendCloseFrameStatus((short) 1002);
                return null;
            }
        }

        return payload;
    }

    private void sendCloseFrameStatus(short code) {
        try {
            byte[] maskKey = generateMaskKey();
            byte[] status = {(byte) (code >> 8), (byte) (code & 0xFF)};
            byte[] maskedStatus = {
                    (byte) (status[0] ^ maskKey[0]),
                    (byte) (status[1] ^ maskKey[1])
            };
            byte[] frame = {
                    (byte) 0x88, (byte) 0x82,
                    maskKey[0], maskKey[1], maskKey[2], maskKey[3],
                    maskedStatus[0], maskedStatus[1]
            };
            OutputStream o = socketOut;
            if (o != null) { o.write(frame); o.flush(); }
        } catch (IOException ignored) {}
    }

    // -------------------------------------------------------------------------
    // Dispatch
    // -------------------------------------------------------------------------

    /**
     * Dispatches a received payload to the appropriate handler.
     *
     * <p>Unknown message types are silently discarded per the wire contract spec.
     */
    private void dispatch(byte[] payload) {
        if (payload.length < 8) return;

        int msgType = (int) readUint32(payload, 4);
        byte[] body = new byte[Math.max(0, payload.length - 8)];
        if (body.length > 0) System.arraycopy(payload, 8, body, 0, body.length);

        // Check if there is a pending future waiting for this response type
        CompletableFuture<byte[]> pending = pendingResponses.remove(msgType);
        if (pending != null) {
            pending.complete(body);
            return;
        }

        // Unsolicited frames (e.g. server-pushed SampleResultBatch)
        if (msgType == MSG_SAMPLE_RESULT_BATCH) {
            handleSampleBatch(body);
        } else {
            LOG.log(Level.FINE,
                    "Received unsolicited message type={0} from worker={1}; discarding",
                    new Object[]{msgType, workerId});
        }
    }

    private void handleSampleBatch(byte[] body) {
        SampleBucketConsumer consumer = resultConsumer;
        if (consumer == null) return;
        try {
            SampleResultBatch batch = SampleResultBatch.parseFrom(body);
            consumer.onBucket(toBucket(batch));
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Error processing SampleResultBatch from worker=" + workerId, ex);
        }
    }

    // -------------------------------------------------------------------------
    // WebSocket handshake (client side)
    // -------------------------------------------------------------------------

    /**
     * Sends the HTTP upgrade request and validates the server's 101 response.
     *
     * @throws IOException if the upgrade is rejected or the response is malformed
     */
    private void performHandshake(InputStream in, OutputStream out) throws IOException {
        byte[] keyBytes = new byte[16];
        new SecureRandom().nextBytes(keyBytes);
        String key = Base64.getEncoder().encodeToString(keyBytes);

        String request = "GET /worker-ws HTTP/1.1\r\n"
                + "Host: " + host + ":" + port + "\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + key + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n"
                + "Sec-WebSocket-Protocol: " + SUBPROTOCOL + "\r\n"
                + "\r\n";
        out.write(request.getBytes(StandardCharsets.ISO_8859_1));
        out.flush();

        // Read response headers
        byte[] buf = new byte[4096];
        int total = 0;
        while (total < buf.length) {
            int r = in.read(buf, total, 1);
            if (r < 0) throw new IOException("Server closed connection during WebSocket handshake");
            total++;
            if (total >= 4
                    && buf[total - 4] == '\r'
                    && buf[total - 3] == '\n'
                    && buf[total - 2] == '\r'
                    && buf[total - 1] == '\n') {
                break;
            }
        }
        String response = new String(buf, 0, total, StandardCharsets.ISO_8859_1);

        if (!response.startsWith("HTTP/1.1 101")) {
            throw new IOException("WebSocket upgrade rejected by worker: "
                    + response.substring(0, Math.min(response.indexOf('\r'), 80)));
        }

        // Validate Sec-WebSocket-Protocol echo
        String proto = extractHeader(response, "Sec-WebSocket-Protocol");
        if (proto == null || !proto.trim().equalsIgnoreCase(SUBPROTOCOL)) {
            throw new IOException(
                    "Worker did not echo required subprotocol '" + SUBPROTOCOL
                            + "'; got: " + proto);
        }

        // Validate Sec-WebSocket-Accept
        String expectedAccept = computeAccept(key);
        String actualAccept = extractHeader(response, "Sec-WebSocket-Accept");
        if (!expectedAccept.equals(actualAccept != null ? actualAccept.trim() : "")) {
            throw new IOException("Sec-WebSocket-Accept mismatch");
        }
    }

    // -------------------------------------------------------------------------
    // Encoding/decoding helpers (package-private for testing)
    // -------------------------------------------------------------------------

    static long readUint32(byte[] buf, int offset) {
        return ((buf[offset]     & 0xFFL) << 24)
             | ((buf[offset + 1] & 0xFFL) << 16)
             | ((buf[offset + 2] & 0xFFL) <<  8)
             |  (buf[offset + 3] & 0xFFL);
    }

    static void writeUint32(byte[] buf, int offset, long value) {
        buf[offset]     = (byte) ((value >> 24) & 0xFF);
        buf[offset + 1] = (byte) ((value >> 16) & 0xFF);
        buf[offset + 2] = (byte) ((value >>  8) & 0xFF);
        buf[offset + 3] = (byte)  (value         & 0xFF);
    }

    /**
     * Encodes a binary message according to the wire contract:
     * {@code [4-byte total][4-byte type][body]}.
     *
     * @param msgType   message type constant
     * @param protoBody serialised protobuf bytes
     * @return the encoded payload (not yet WebSocket-framed)
     */
    static byte[] encodeMessage(int msgType, byte[] protoBody) {
        int total = 8 + protoBody.length;
        byte[] out = new byte[total];
        writeUint32(out, 0, total);
        writeUint32(out, 4, msgType);
        if (protoBody.length > 0) System.arraycopy(protoBody, 0, out, 8, protoBody.length);
        return out;
    }

    /**
     * Decodes a message payload: extracts the message type and body.
     *
     * @param payload the raw payload bytes (must be at least 8 bytes)
     * @return a two-element array: {@code [msgType (as int[]), body (as byte[])]}
     *         — or {@code null} if the payload is too short
     */
    static Object[] decodeMessage(byte[] payload) {
        if (payload == null || payload.length < 8) return null;
        int msgType = (int) readUint32(payload, 4);
        byte[] body = new byte[payload.length - 8];
        if (body.length > 0) System.arraycopy(payload, 8, body, 0, body.length);
        return new Object[]{msgType, body};
    }

    // -------------------------------------------------------------------------
    // Proto → engine model conversion (same logic as GrpcWorkerTransport)
    // -------------------------------------------------------------------------

    private static SampleBucket toBucket(SampleResultBatch batch) {
        Instant ts = Instant.ofEpochSecond(
                batch.getTimestamp().getSeconds(),
                batch.getTimestamp().getNanos());
        double p90 = batch.getPercentilesOrDefault("p90", 0.0);
        double p95 = batch.getPercentilesOrDefault("p95", 0.0);
        double p99 = batch.getPercentilesOrDefault("p99", 0.0);
        return new SampleBucket(
                ts,
                batch.getSamplerLabel(),
                batch.getSampleCount(),
                batch.getErrorCount(),
                batch.getAvgResponseTime(),
                batch.getAvgResponseTime(),
                p99 > 0 ? p99 : batch.getAvgResponseTime(),
                p90, p95, p99,
                (double) batch.getSampleCount());
    }

    // -------------------------------------------------------------------------
    // Misc helpers
    // -------------------------------------------------------------------------

    private static String computeAccept(String key) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest(
                    (key.trim() + WS_GUID).getBytes(StandardCharsets.ISO_8859_1));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 not available", ex);
        }
    }

    private static String extractHeader(String response, String name) {
        String lower = response.toLowerCase(java.util.Locale.ROOT);
        String lowerName = name.toLowerCase(java.util.Locale.ROOT) + ":";
        int idx = lower.indexOf(lowerName);
        if (idx < 0) return null;
        int start = idx + lowerName.length();
        int end = response.indexOf('\r', start);
        if (end < 0) end = response.indexOf('\n', start);
        if (end < 0) end = response.length();
        return response.substring(start, end).trim();
    }

    private static byte[] readExact(InputStream in, int length) throws IOException {
        byte[] buf = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(buf, offset, length - offset);
            if (read < 0) throw new IOException("Unexpected end of stream");
            offset += read;
        }
        return buf;
    }

    private static byte[] generateMaskKey() {
        byte[] key = new byte[4];
        new SecureRandom().nextBytes(key);
        return key;
    }
}
