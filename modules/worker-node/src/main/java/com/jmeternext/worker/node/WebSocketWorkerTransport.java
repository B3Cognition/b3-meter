package com.jmeternext.worker.node;

import com.google.protobuf.Timestamp;
import com.jmeternext.engine.service.SampleBucket;
import com.jmeternext.worker.proto.ConfigureAck;
import com.jmeternext.worker.proto.HealthRequest;
import com.jmeternext.worker.proto.HealthStatus;
import com.jmeternext.worker.proto.SampleResultBatch;
import com.jmeternext.worker.proto.StartAck;
import com.jmeternext.worker.proto.StartMessage;
import com.jmeternext.worker.proto.StopAck;
import com.jmeternext.worker.proto.StopMessage;
import com.jmeternext.worker.proto.TestPlanMessage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * WebSocket server endpoint for the worker node.
 *
 * <p>Listens on the configured TCP port and accepts incoming WebSocket connections from
 * the controller. Each connection undergoes a standard RFC-6455 HTTP upgrade handshake;
 * connections that do not advertise the {@value #SUBPROTOCOL} subprotocol are rejected
 * with HTTP 400.
 *
 * <p>Wire protocol (binary frames):
 * <pre>
 *   bytes 0–3  : big-endian uint32 — total message length (including the 8-byte header)
 *   bytes 4–7  : big-endian uint32 — message type (see {@link MessageType})
 *   bytes 8+   : protobuf-serialised message body (same .proto types as the gRPC service)
 * </pre>
 *
 * <p>Message processing is delegated to the shared {@link WorkerServiceImpl} instance so
 * that both gRPC and WebSocket paths execute exactly the same logic.
 *
 * <p>Reconnection and exponential back-off are the responsibility of the connecting
 * controller client ({@link com.jmeternext.distributed.controller.WebSocketWorkerTransport}).
 *
 * @see com.jmeternext.distributed.controller.WorkerTransport
 */
public class WebSocketWorkerTransport {

    private static final Logger LOG = Logger.getLogger(WebSocketWorkerTransport.class.getName());

    /** WebSocket subprotocol that every connecting client must negotiate. */
    public static final String SUBPROTOCOL = "jmeter-worker/v1";

    /** Magic GUID used in the RFC-6455 Sec-WebSocket-Accept derivation. */
    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    /** WebSocket opcode for binary frames (RFC 6455 §5.2). */
    private static final int OPCODE_BINARY = 0x2;

    /** WebSocket opcode for connection-close frames (RFC 6455 §5.5.1). */
    private static final int OPCODE_CLOSE = 0x8;

    /** WebSocket close status code 1002 — Protocol Error. */
    private static final byte[] CLOSE_PROTOCOL_ERROR = {0x03, (byte) 0xEA};

    /** WebSocket close status code 1000 — Normal Closure. */
    private static final byte[] CLOSE_NORMAL = {0x03, (byte) 0xE8};

    // -------------------------------------------------------------------------
    // Message type constants (per contracts/websocket-transport.md)
    // -------------------------------------------------------------------------

    /**
     * Numeric message type identifiers as defined in {@code contracts/websocket-transport.md}.
     */
    public static final class MessageType {
        private MessageType() {}

        public static final int CONFIGURE_REQUEST  = 1;
        public static final int CONFIGURE_ACK      = 2;
        public static final int START_REQUEST      = 3;
        public static final int START_ACK          = 4;
        public static final int STOP_REQUEST       = 5;
        public static final int STOP_ACK           = 6;
        public static final int SAMPLE_RESULT_BATCH = 7;
        public static final int HEALTH_REQUEST     = 8;
        public static final int HEALTH_STATUS      = 9;
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final WorkerServiceImpl delegate;
    private final ExecutorService acceptorPool;
    private final CopyOnWriteArrayList<Connection> connections = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile ServerSocket serverSocket;
    private volatile Thread acceptorThread;

    /**
     * Creates a new WebSocket transport that delegates message handling to the given
     * {@link WorkerServiceImpl}.
     *
     * @param delegate the shared worker service implementation; must not be {@code null}
     */
    public WebSocketWorkerTransport(WorkerServiceImpl delegate) {
        if (delegate == null) throw new IllegalArgumentException("delegate must not be null");
        this.delegate = delegate;
        this.acceptorPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "ws-worker-connection");
            t.setDaemon(true);
            return t;
        });
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Starts the WebSocket server and begins accepting connections on {@code port}.
     *
     * @param port TCP port to bind; must be in [1, 65535]
     * @throws IOException              if the socket cannot be opened
     * @throws IllegalArgumentException if {@code port} is out of range
     * @throws IllegalStateException    if already started
     */
    public void start(int port) throws IOException {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port must be in [0, 65535], got: " + port);
        }
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("WebSocketWorkerTransport is already running");
        }

        serverSocket = new ServerSocket(port);
        acceptorThread = new Thread(this::acceptLoop, "ws-worker-acceptor");
        acceptorThread.setDaemon(true);
        acceptorThread.start();

        LOG.log(Level.INFO, "WebSocketWorkerTransport listening on port {0}", port);
    }

    /**
     * Stops the server: closes the listening socket and all active connections.
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Error closing server socket", ex);
        }
        for (Connection conn : connections) {
            conn.close(CLOSE_NORMAL);
        }
        acceptorPool.shutdownNow();
        LOG.info("WebSocketWorkerTransport stopped");
    }

    /**
     * Returns the port the server is bound to, or {@code -1} if not started.
     *
     * @return bound port, or -1
     */
    public int getPort() {
        ServerSocket s = serverSocket;
        return (s != null && !s.isClosed()) ? s.getLocalPort() : -1;
    }

    // -------------------------------------------------------------------------
    // Accept loop
    // -------------------------------------------------------------------------

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                acceptorPool.submit(() -> handleConnection(socket));
            } catch (IOException ex) {
                if (running.get()) {
                    LOG.log(Level.WARNING, "Accept error", ex);
                }
                // If !running, the socket was deliberately closed — exit silently.
            }
        }
    }

    // -------------------------------------------------------------------------
    // Connection handling
    // -------------------------------------------------------------------------

    private void handleConnection(Socket socket) {
        try (socket) {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            // --- HTTP upgrade handshake ---
            if (!performHandshake(in, out)) {
                return; // rejected — response already sent
            }

            Connection conn = new Connection(socket, in, out);
            connections.add(conn);
            try {
                conn.readLoop();
            } finally {
                connections.remove(conn);
            }
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "WebSocket connection error", ex);
        }
    }

    /**
     * Reads the HTTP upgrade request and either accepts or rejects the connection.
     *
     * @return {@code true} when the upgrade was successful; {@code false} when the request
     *         was rejected (HTTP 400 already written to {@code out})
     */
    private boolean performHandshake(InputStream in, OutputStream out) throws IOException {
        // Read the HTTP request line-by-line until the blank line
        StringBuilder sb = new StringBuilder();
        int b, prev = -1;
        // Simple line-reader: collect until we see \r\n\r\n
        byte[] buf = new byte[4096];
        int total = 0;
        while (total < buf.length) {
            int r = in.read(buf, total, 1);
            if (r < 0) return false;
            total++;
            if (total >= 4
                    && buf[total - 4] == '\r'
                    && buf[total - 3] == '\n'
                    && buf[total - 2] == '\r'
                    && buf[total - 1] == '\n') {
                break;
            }
        }
        String request = new String(buf, 0, total, StandardCharsets.ISO_8859_1);

        // Extract headers
        String wsKey = extractHeader(request, "Sec-WebSocket-Key");
        String wsProto = extractHeader(request, "Sec-WebSocket-Protocol");

        if (wsKey == null || wsKey.isBlank()) {
            send400(out, "Missing Sec-WebSocket-Key");
            return false;
        }

        if (wsProto == null || !containsSubprotocol(wsProto, SUBPROTOCOL)) {
            send400(out, "Missing or unsupported Sec-WebSocket-Protocol; expected: " + SUBPROTOCOL);
            return false;
        }

        // Compute Sec-WebSocket-Accept
        String accept = computeAccept(wsKey);

        String response = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + accept + "\r\n"
                + "Sec-WebSocket-Protocol: " + SUBPROTOCOL + "\r\n"
                + "\r\n";
        out.write(response.getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
        return true;
    }

    // -------------------------------------------------------------------------
    // Per-connection message loop
    // -------------------------------------------------------------------------

    /**
     * Encapsulates a single accepted WebSocket connection and drives its read loop.
     */
    final class Connection {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        Connection(Socket socket, InputStream in, OutputStream out) {
            this.socket = socket;
            this.in = in;
            this.out = out;
        }

        void readLoop() {
            while (!closed.get() && !socket.isClosed()) {
                try {
                    byte[] frame = readFrame();
                    if (frame == null) break; // connection closed by peer
                    dispatch(frame);
                } catch (IOException ex) {
                    if (!closed.get()) {
                        LOG.log(Level.FINE, "WebSocket read error (connection closing)", ex);
                    }
                    break;
                } catch (ProtocolException ex) {
                    LOG.log(Level.WARNING, "WebSocket protocol error: {0}", ex.getMessage());
                    close(CLOSE_PROTOCOL_ERROR);
                    break;
                }
            }
        }

        /**
         * Reads one complete WebSocket frame (binary or close) from the stream.
         *
         * @return the unmasked payload bytes, or {@code null} on clean close
         * @throws IOException       on I/O error
         * @throws ProtocolException on frame length mismatch
         */
        byte[] readFrame() throws IOException, ProtocolException {
            // First two bytes: FIN/opcode and MASK/payload-len
            int b0 = in.read();
            int b1 = in.read();
            if (b0 < 0 || b1 < 0) return null;

            int opcode = b0 & 0x0F;
            boolean masked = (b1 & 0x80) != 0;
            long payloadLen = b1 & 0x7F;

            if (payloadLen == 126) {
                // 16-bit extended length
                byte[] ext = readExact(2);
                payloadLen = ((ext[0] & 0xFFL) << 8) | (ext[1] & 0xFFL);
            } else if (payloadLen == 127) {
                // 64-bit extended length
                byte[] ext = readExact(8);
                payloadLen = 0;
                for (int i = 0; i < 8; i++) {
                    payloadLen = (payloadLen << 8) | (ext[i] & 0xFFL);
                }
            }

            byte[] maskKey = masked ? readExact(4) : null;
            byte[] payload = readExact((int) payloadLen);

            if (masked && maskKey != null) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] ^= maskKey[i % 4];
                }
            }

            if (opcode == OPCODE_CLOSE) {
                close(CLOSE_NORMAL);
                return null;
            }

            if (opcode != OPCODE_BINARY) {
                // Silently skip non-binary frames (ping/pong/text)
                return new byte[0];
            }

            // Validate declared length against actual payload length
            // The wire contract declares total message length in bytes 0–3 of the payload.
            // If payload is too short to contain the header, skip silently.
            if (payload.length > 0 && payload.length >= 4) {
                long declaredTotal = readUint32(payload, 0);
                if (declaredTotal != payload.length) {
                    throw new ProtocolException(
                            "Frame length mismatch: declared=" + declaredTotal
                                    + ", actual=" + payload.length);
                }
            }

            return payload;
        }

        /**
         * Dispatches a fully-received binary payload to the correct handler.
         *
         * <p>Frames with fewer than 8 bytes (no room for the 8-byte header) are silently
         * discarded. Unknown message types are silently discarded per the wire contract spec.
         */
        void dispatch(byte[] payload) {
            if (payload == null || payload.length < 8) return;

            int msgType = (int) readUint32(payload, 4);
            byte[] body = new byte[Math.max(0, payload.length - 8)];
            if (body.length > 0) {
                System.arraycopy(payload, 8, body, 0, body.length);
            }

            try {
                switch (msgType) {
                    case MessageType.CONFIGURE_REQUEST -> handleConfigure(body);
                    case MessageType.START_REQUEST     -> handleStart(body);
                    case MessageType.STOP_REQUEST      -> handleStop(body);
                    case MessageType.HEALTH_REQUEST    -> handleHealth(body);
                    case MessageType.SAMPLE_RESULT_BATCH, MessageType.CONFIGURE_ACK,
                         MessageType.START_ACK, MessageType.STOP_ACK,
                         MessageType.HEALTH_STATUS -> {
                        // These are controller-bound messages; a worker should not receive them.
                        // Silently discard per spec.
                        LOG.log(Level.FINE,
                                "Received unexpected message type={0} on worker; discarding", msgType);
                    }
                    default -> LOG.log(Level.FINE,
                            "Unknown message type={0}; silently discarding", msgType);
                }
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "Error dispatching message type=" + msgType, ex);
            }
        }

        // ----- Message handlers -----

        private void handleConfigure(byte[] body) throws Exception {
            TestPlanMessage plan = TestPlanMessage.parseFrom(body);
            CollectingObserver<ConfigureAck> obs = new CollectingObserver<>();
            delegate.configure(plan, obs);
            ConfigureAck ack = obs.getValue();
            if (ack != null) {
                sendMessage(MessageType.CONFIGURE_ACK, ack.toByteArray());
            }
        }

        private void handleStart(byte[] body) throws Exception {
            StartMessage msg = StartMessage.parseFrom(body);
            CollectingObserver<StartAck> obs = new CollectingObserver<>();
            delegate.start(msg, obs);
            StartAck ack = obs.getValue();
            if (ack != null) {
                sendMessage(MessageType.START_ACK, ack.toByteArray());
            }
        }

        private void handleStop(byte[] body) throws Exception {
            StopMessage msg = StopMessage.parseFrom(body);
            CollectingObserver<StopAck> obs = new CollectingObserver<>();
            delegate.stop(msg, obs);
            StopAck ack = obs.getValue();
            if (ack != null) {
                sendMessage(MessageType.STOP_ACK, ack.toByteArray());
            }
        }

        private void handleHealth(byte[] body) throws Exception {
            HealthRequest req = body.length > 0
                    ? HealthRequest.parseFrom(body)
                    : HealthRequest.getDefaultInstance();
            CollectingObserver<HealthStatus> obs = new CollectingObserver<>();
            delegate.getHealth(req, obs);
            HealthStatus status = obs.getValue();
            if (status != null) {
                sendMessage(MessageType.HEALTH_STATUS, status.toByteArray());
            }
        }

        // ----- Frame writing -----

        /**
         * Sends a binary message using the wire-contract framing:
         * {@code [4-byte total length][4-byte message type][protobuf body]}.
         *
         * @param msgType the message type constant from {@link MessageType}
         * @param protoBody  serialised protobuf bytes
         */
        synchronized void sendMessage(int msgType, byte[] protoBody) {
            if (closed.get() || socket.isClosed()) return;
            try {
                int total = 8 + protoBody.length;
                byte[] payload = new byte[total];
                writeUint32(payload, 0, total);
                writeUint32(payload, 4, msgType);
                if (protoBody.length > 0) {
                    System.arraycopy(protoBody, 0, payload, 8, protoBody.length);
                }
                writeWsFrame(payload);
            } catch (IOException ex) {
                LOG.log(Level.WARNING, "Error sending WebSocket frame", ex);
                closed.set(true);
            }
        }

        /**
         * Writes a binary WebSocket frame (FIN=1, opcode=2) for the given payload.
         * The server never masks outgoing frames (RFC 6455 §5.1).
         */
        private void writeWsFrame(byte[] payload) throws IOException {
            ByteArrayOutputStream buf = new ByteArrayOutputStream(10 + payload.length);
            // FIN=1, opcode=2 (binary)
            buf.write(0x82);
            // MASK=0, payload length
            int len = payload.length;
            if (len < 126) {
                buf.write(len);
            } else if (len < 65536) {
                buf.write(126);
                buf.write((len >> 8) & 0xFF);
                buf.write(len & 0xFF);
            } else {
                buf.write(127);
                for (int i = 7; i >= 0; i--) {
                    buf.write((len >> (8 * i)) & 0xFF);
                }
            }
            buf.write(payload);
            out.write(buf.toByteArray());
            out.flush();
        }

        /**
         * Sends a WebSocket close frame with the given status bytes and marks this
         * connection as closed.
         */
        synchronized void close(byte[] statusBytes) {
            if (closed.compareAndSet(false, true)) {
                try {
                    ByteArrayOutputStream buf = new ByteArrayOutputStream(4);
                    buf.write(0x88); // FIN=1, opcode=8 (close)
                    buf.write(statusBytes.length);
                    buf.write(statusBytes);
                    out.write(buf.toByteArray());
                    out.flush();
                } catch (IOException ignored) {
                    // Best-effort — peer may have already gone away.
                }
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }

        // ----- I/O helpers -----

        private byte[] readExact(int length) throws IOException {
            byte[] buf = new byte[length];
            int offset = 0;
            while (offset < length) {
                int read = in.read(buf, offset, length - offset);
                if (read < 0) throw new IOException("Unexpected end of stream");
                offset += read;
            }
            return buf;
        }
    }

    // -------------------------------------------------------------------------
    // Encoding helpers (package-private for testing)
    // -------------------------------------------------------------------------

    /**
     * Reads a big-endian unsigned 32-bit integer from {@code buf} at {@code offset}.
     *
     * @param buf    source byte array; must have at least {@code offset + 4} bytes
     * @param offset byte offset to read from
     * @return the value as a {@code long} (avoids sign-extension issues)
     */
    static long readUint32(byte[] buf, int offset) {
        return ((buf[offset]     & 0xFFL) << 24)
             | ((buf[offset + 1] & 0xFFL) << 16)
             | ((buf[offset + 2] & 0xFFL) <<  8)
             |  (buf[offset + 3] & 0xFFL);
    }

    /**
     * Writes a big-endian unsigned 32-bit integer to {@code buf} at {@code offset}.
     *
     * @param buf    target byte array; must have at least {@code offset + 4} bytes
     * @param offset byte offset to write to
     * @param value  value to write (only the lower 32 bits are used)
     */
    static void writeUint32(byte[] buf, int offset, long value) {
        buf[offset]     = (byte) ((value >> 24) & 0xFF);
        buf[offset + 1] = (byte) ((value >> 16) & 0xFF);
        buf[offset + 2] = (byte) ((value >>  8) & 0xFF);
        buf[offset + 3] = (byte)  (value         & 0xFF);
    }

    /**
     * Builds the {@code Sec-WebSocket-Accept} header value for a given
     * {@code Sec-WebSocket-Key} (RFC 6455 §4.2.2 step 5.4).
     */
    static String computeAccept(String key) {
        try {
            String combined = key.trim() + WS_GUID;
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest(combined.getBytes(StandardCharsets.ISO_8859_1));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 not available", ex);
        }
    }

    // -------------------------------------------------------------------------
    // Header parsing helpers
    // -------------------------------------------------------------------------

    private static String extractHeader(String request, String name) {
        String lowerRequest = request.toLowerCase(java.util.Locale.ROOT);
        String lowerName = name.toLowerCase(java.util.Locale.ROOT) + ":";
        int idx = lowerRequest.indexOf(lowerName);
        if (idx < 0) return null;
        int start = idx + lowerName.length();
        int end = request.indexOf('\r', start);
        if (end < 0) end = request.indexOf('\n', start);
        if (end < 0) end = request.length();
        return request.substring(start, end).trim();
    }

    private static boolean containsSubprotocol(String headerValue, String subprotocol) {
        for (String token : headerValue.split(",")) {
            if (token.trim().equalsIgnoreCase(subprotocol)) return true;
        }
        return false;
    }

    private static void send400(OutputStream out, String reason) throws IOException {
        String response = "HTTP/1.1 400 Bad Request\r\n"
                + "Content-Type: text/plain\r\n"
                + "Content-Length: " + reason.getBytes(StandardCharsets.UTF_8).length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n"
                + reason;
        out.write(response.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    // -------------------------------------------------------------------------
    // Internal helper: synchronous StreamObserver collector
    // -------------------------------------------------------------------------

    /**
     * Simple {@link io.grpc.stub.StreamObserver} implementation that captures the first
     * {@code onNext} value synchronously. Used to bridge the gRPC-style delegate interface
     * into a one-shot request/response pattern for the WebSocket handler.
     *
     * @param <T> the protobuf message type
     */
    static final class CollectingObserver<T> implements io.grpc.stub.StreamObserver<T> {
        private volatile T value;

        @Override
        public void onNext(T v) { this.value = v; }

        @Override
        public void onError(Throwable t) {
            LOG.log(Level.WARNING, "StreamObserver error in WebSocket handler", t);
        }

        @Override
        public void onCompleted() {}

        /** Returns the captured value, or {@code null} if {@code onNext} was never called. */
        T getValue() { return value; }
    }

    // -------------------------------------------------------------------------
    // Internal: Protocol-level exception
    // -------------------------------------------------------------------------

    /** Thrown when the received frame violates the wire protocol contract. */
    static final class ProtocolException extends Exception {
        private static final long serialVersionUID = 1L;
        ProtocolException(String message) { super(message); }
    }
}
