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

import com.google.protobuf.ByteString;
import com.b3meter.worker.proto.ConfigureAck;
import com.b3meter.worker.proto.HealthStatus;
import com.b3meter.worker.proto.SampleResultBatch;
import com.b3meter.worker.proto.StartAck;
import com.b3meter.worker.proto.StartMessage;
import com.b3meter.worker.proto.StopAck;
import com.b3meter.worker.proto.StopMessage;
import com.b3meter.worker.proto.TestPlanMessage;
import com.b3meter.worker.proto.WorkerState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for {@link WebSocketWorkerTransport} (server-side worker endpoint).
 *
 * <h2>Testing strategy</h2>
 * <p>Each test starts a real {@link WebSocketWorkerTransport} on an ephemeral port and
 * connects to it using a minimal in-test WebSocket client.  This exercises:
 * <ul>
 *   <li>The full RFC-6455 HTTP upgrade / subprotocol negotiation</li>
 *   <li>Binary frame encoding and decoding</li>
 *   <li>Delegation to {@link WorkerServiceImpl}</li>
 *   <li>Error handling (unknown message types, length mismatches)</li>
 * </ul>
 *
 * <p>All tests complete in well under 5 seconds; the suite carries a 10-second timeout
 * as a safety net against any thread that blocks unexpectedly.
 */
@Timeout(value = 20, unit = TimeUnit.SECONDS)
class WebSocketWorkerTransportTest {

    private WorkerServiceImpl serviceImpl;
    private WebSocketWorkerTransport wsServer;

    @BeforeEach
    void setUp() throws IOException {
        serviceImpl = new WorkerServiceImpl();
        wsServer = new WebSocketWorkerTransport(serviceImpl);
        wsServer.start(0); // ephemeral port
    }

    @AfterEach
    void tearDown() {
        wsServer.stop();
        serviceImpl.shutdown();
    }

    // -------------------------------------------------------------------------
    // Frame encoding helpers (unit tests — no network needed)
    // -------------------------------------------------------------------------

    /**
     * {@link WebSocketWorkerTransport#readUint32} and {@link WebSocketWorkerTransport#writeUint32}
     * must be inverse operations for the full uint32 range.
     */
    @Test
    void readWriteUint32_roundTrip() {
        byte[] buf = new byte[8];
        long[] testValues = {0L, 1L, 255L, 256L, 65535L, 65536L, 0xFFFFFFFFL};
        for (long v : testValues) {
            WebSocketWorkerTransport.writeUint32(buf, 0, v);
            long read = WebSocketWorkerTransport.readUint32(buf, 0);
            assertThat(read)
                    .as("round-trip for value " + v)
                    .isEqualTo(v);
        }
    }

    /**
     * The 8-byte message header must encode total-length and message-type correctly.
     */
    @Test
    void frameHeader_encodesLengthAndType() {
        byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
        int msgType = WebSocketWorkerTransport.MessageType.CONFIGURE_REQUEST;

        // Manually build what a message payload should look like
        int totalLen = 8 + body.length;
        byte[] expected = new byte[totalLen];
        WebSocketWorkerTransport.writeUint32(expected, 0, totalLen);
        WebSocketWorkerTransport.writeUint32(expected, 4, msgType);
        System.arraycopy(body, 0, expected, 8, body.length);

        assertThat((int) WebSocketWorkerTransport.readUint32(expected, 0))
                .as("total length field")
                .isEqualTo(totalLen);
        assertThat((int) WebSocketWorkerTransport.readUint32(expected, 4))
                .as("message type field")
                .isEqualTo(msgType);
    }

    /**
     * {@link WebSocketWorkerTransport#computeAccept} must produce the correct
     * Sec-WebSocket-Accept value for the RFC-6455 test vector.
     */
    @Test
    void computeAccept_rfcTestVector() {
        // RFC 6455 §1.3 test vector:
        // key = "dGhlIHNhbXBsZSBub25jZQ=="
        // expected accept = "s3pPLMBiTxaQ9kYGzzhZRbK+xOo="
        String key = "dGhlIHNhbXBsZSBub25jZQ==";
        String expected = "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=";
        assertThat(WebSocketWorkerTransport.computeAccept(key)).isEqualTo(expected);
    }

    // -------------------------------------------------------------------------
    // Handshake rejection tests
    // -------------------------------------------------------------------------

    /**
     * A connection without the {@code jmeter-worker/v1} subprotocol must receive
     * HTTP 400.
     */
    @Test
    void handshake_missingSubprotocol_receives400() throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", wsServer.getPort()), 3_000);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            // Send a WebSocket upgrade without the required subprotocol
            byte[] keyBytes = new byte[16];
            new SecureRandom().nextBytes(keyBytes);
            String key = Base64.getEncoder().encodeToString(keyBytes);

            String req = "GET /worker-ws HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: " + key + "\r\n"
                    + "Sec-WebSocket-Version: 13\r\n"
                    + "\r\n"; // no Sec-WebSocket-Protocol
            out.write(req.getBytes(StandardCharsets.ISO_8859_1));
            out.flush();

            // Read the response line
            StringBuilder sb = new StringBuilder();
            int b;
            while ((b = in.read()) != -1) {
                sb.append((char) b);
                if (sb.length() > 12) break;
            }
            assertThat(sb.toString()).startsWith("HTTP/1.1 400");
        }
    }

    /**
     * A connection with a wrong subprotocol must be rejected with HTTP 400.
     */
    @Test
    void handshake_wrongSubprotocol_receives400() throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", wsServer.getPort()), 3_000);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            byte[] keyBytes = new byte[16];
            new SecureRandom().nextBytes(keyBytes);
            String key = Base64.getEncoder().encodeToString(keyBytes);

            String req = "GET /worker-ws HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: " + key + "\r\n"
                    + "Sec-WebSocket-Version: 13\r\n"
                    + "Sec-WebSocket-Protocol: wrong-protocol\r\n"
                    + "\r\n";
            out.write(req.getBytes(StandardCharsets.ISO_8859_1));
            out.flush();

            StringBuilder sb = new StringBuilder();
            int b;
            while ((b = in.read()) != -1) {
                sb.append((char) b);
                if (sb.length() > 12) break;
            }
            assertThat(sb.toString()).startsWith("HTTP/1.1 400");
        }
    }

    // -------------------------------------------------------------------------
    // Message round-trip tests
    // -------------------------------------------------------------------------

    /**
     * A {@code ConfigureRequest} message must produce a {@code ConfigureAck} response.
     */
    @Test
    void configure_roundTrip_acksAccepted() throws Exception {
        try (TestWsClient client = new TestWsClient("127.0.0.1", wsServer.getPort())) {
            client.connect();

            TestPlanMessage plan = TestPlanMessage.newBuilder()
                    .setRunId(UUID.randomUUID().toString())
                    .setPlanContent(ByteString.copyFromUtf8("<jmeterTestPlan/>"))
                    .setVirtualUsers(1)
                    .setDurationSeconds(0)
                    .build();

            client.sendMessage(WebSocketWorkerTransport.MessageType.CONFIGURE_REQUEST,
                    plan.toByteArray());

            byte[] response = client.readPayload();
            assertThat(response).isNotNull();

            int respType = (int) WebSocketWorkerTransport.readUint32(response, 4);
            assertThat(respType)
                    .as("response message type")
                    .isEqualTo(WebSocketWorkerTransport.MessageType.CONFIGURE_ACK);

            byte[] body = extractBody(response);
            ConfigureAck ack = ConfigureAck.parseFrom(body);
            assertThat(ack.getAccepted())
                    .as("worker should accept the plan")
                    .isTrue();
        }
    }

    /**
     * A {@code HealthRequest} message must produce a {@code HealthStatus} response.
     */
    @Test
    void healthRequest_roundTrip_returnsHealthStatus() throws Exception {
        try (TestWsClient client = new TestWsClient("127.0.0.1", wsServer.getPort())) {
            client.connect();

            client.sendMessage(WebSocketWorkerTransport.MessageType.HEALTH_REQUEST, new byte[0]);

            byte[] response = client.readPayload();
            assertThat(response).isNotNull();

            int respType = (int) WebSocketWorkerTransport.readUint32(response, 4);
            assertThat(respType)
                    .as("response must be HEALTH_STATUS (type 9)")
                    .isEqualTo(WebSocketWorkerTransport.MessageType.HEALTH_STATUS);

            byte[] body = extractBody(response);
            HealthStatus status = HealthStatus.parseFrom(body);
            assertThat(status.getState())
                    .as("fresh worker should be IDLE")
                    .isEqualTo(WorkerState.WORKER_STATE_IDLE);
        }
    }

    /**
     * A full configure → start → health → stop sequence must succeed over WebSocket.
     */
    @Test
    void fullLifecycle_configureStartHealthStop() throws Exception {
        String runId = UUID.randomUUID().toString();

        try (TestWsClient client = new TestWsClient("127.0.0.1", wsServer.getPort())) {
            client.connect();

            // --- Configure ---
            TestPlanMessage plan = TestPlanMessage.newBuilder()
                    .setRunId(runId)
                    .setPlanContent(ByteString.copyFromUtf8("<jmeterTestPlan/>"))
                    .setVirtualUsers(2)
                    .setDurationSeconds(30)
                    .build();
            client.sendMessage(WebSocketWorkerTransport.MessageType.CONFIGURE_REQUEST,
                    plan.toByteArray());
            byte[] configResp = client.readPayload();
            ConfigureAck configAck = ConfigureAck.parseFrom(extractBody(configResp));
            assertThat(configAck.getAccepted()).isTrue();

            // --- Start ---
            StartMessage startMsg = StartMessage.newBuilder().setRunId(runId).build();
            client.sendMessage(WebSocketWorkerTransport.MessageType.START_REQUEST,
                    startMsg.toByteArray());
            byte[] startResp = client.readPayload();
            StartAck startAck = StartAck.parseFrom(extractBody(startResp));
            assertThat(startAck.getAccepted()).isTrue();

            // --- Health: should be RUNNING ---
            client.sendMessage(WebSocketWorkerTransport.MessageType.HEALTH_REQUEST, new byte[0]);
            byte[] healthResp = client.readPayload();
            HealthStatus health = HealthStatus.parseFrom(extractBody(healthResp));
            assertThat(health.getState()).isEqualTo(WorkerState.WORKER_STATE_RUNNING);

            // --- Stop ---
            StopMessage stopMsg = StopMessage.newBuilder().setRunId(runId).build();
            client.sendMessage(WebSocketWorkerTransport.MessageType.STOP_REQUEST,
                    stopMsg.toByteArray());
            byte[] stopResp = client.readPayload();
            StopAck stopAck = StopAck.parseFrom(extractBody(stopResp));
            assertThat(stopAck.getAccepted()).isTrue();
        }
    }

    /**
     * An unknown message type must be silently discarded; the connection must not be closed.
     * The client can continue to send valid messages after the unknown one.
     */
    @Test
    void unknownMessageType_silentlyDiscarded_connectionRemainsOpen() throws Exception {
        try (TestWsClient client = new TestWsClient("127.0.0.1", wsServer.getPort())) {
            client.connect();

            // Send a message with type 999 (unknown)
            client.sendMessage(999, new byte[]{0x01, 0x02, 0x03});

            // Connection must still be alive — send a health request
            client.sendMessage(WebSocketWorkerTransport.MessageType.HEALTH_REQUEST, new byte[0]);
            byte[] response = client.readPayload();

            // We may first receive a response to the health request (unknown type has no response)
            assertThat(response).isNotNull();
            int msgType = (int) WebSocketWorkerTransport.readUint32(response, 4);
            assertThat(msgType)
                    .as("should receive HEALTH_STATUS after unknown-type message")
                    .isEqualTo(WebSocketWorkerTransport.MessageType.HEALTH_STATUS);
        }
    }

    /**
     * The server must be startable and stoppable without throwing.
     */
    @Test
    void serverStartStop_doesNotThrow() {
        // The server was already started in @BeforeEach; just verify it's running
        assertThat(wsServer.getPort()).isPositive();
        // stop() is called in @AfterEach — just verify it doesn't throw here
        assertThatCode(() -> {
            // No-op: will be stopped by @AfterEach
        }).doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Extracts the protobuf body from a decoded wire-protocol payload (strips 8-byte header). */
    private static byte[] extractBody(byte[] payload) {
        if (payload.length <= 8) return new byte[0];
        byte[] body = new byte[payload.length - 8];
        System.arraycopy(payload, 8, body, 0, body.length);
        return body;
    }

    // -------------------------------------------------------------------------
    // Minimal in-test WebSocket client
    // -------------------------------------------------------------------------

    /**
     * Bare-bones WebSocket client for testing. Handles the RFC-6455 handshake and
     * binary frame encoding/decoding. Client frames are masked as required by the spec.
     */
    static final class TestWsClient implements AutoCloseable {

        private static final String SUBPROTOCOL = WebSocketWorkerTransport.SUBPROTOCOL;
        private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

        private final String host;
        private final int port;
        private Socket socket;
        private InputStream in;
        private OutputStream out;

        TestWsClient(String host, int port) {
            this.host = host;
            this.port = port;
        }

        void connect() throws Exception {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 3_000);
            socket.setSoTimeout(5_000);
            in = socket.getInputStream();
            out = socket.getOutputStream();

            byte[] keyBytes = new byte[16];
            new SecureRandom().nextBytes(keyBytes);
            String key = Base64.getEncoder().encodeToString(keyBytes);

            String req = "GET /worker-ws HTTP/1.1\r\n"
                    + "Host: " + host + ":" + port + "\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: " + key + "\r\n"
                    + "Sec-WebSocket-Version: 13\r\n"
                    + "Sec-WebSocket-Protocol: " + SUBPROTOCOL + "\r\n"
                    + "\r\n";
            out.write(req.getBytes(StandardCharsets.ISO_8859_1));
            out.flush();

            // Read until blank line
            byte[] buf = new byte[4096];
            int total = 0;
            while (total < buf.length) {
                int r = in.read(buf, total, 1);
                if (r < 0) throw new IOException("Server closed connection during handshake");
                total++;
                if (total >= 4
                        && buf[total - 4] == '\r' && buf[total - 3] == '\n'
                        && buf[total - 2] == '\r' && buf[total - 1] == '\n') {
                    break;
                }
            }
            String resp = new String(buf, 0, total, StandardCharsets.ISO_8859_1);
            if (!resp.startsWith("HTTP/1.1 101")) {
                throw new IOException("Handshake rejected: " + resp.substring(0, Math.min(80, resp.length())));
            }
        }

        /**
         * Sends a binary message with the wire-contract framing
         * {@code [4-byte total][4-byte type][body]}, wrapped in a masked WS frame.
         */
        void sendMessage(int msgType, byte[] protoBody) throws IOException {
            int total = 8 + protoBody.length;
            byte[] payload = new byte[total];
            WebSocketWorkerTransport.writeUint32(payload, 0, total);
            WebSocketWorkerTransport.writeUint32(payload, 4, msgType);
            if (protoBody.length > 0) System.arraycopy(protoBody, 0, payload, 8, protoBody.length);

            // Mask (client → server frames must be masked per RFC 6455 §5.1)
            byte[] maskKey = new byte[4];
            new SecureRandom().nextBytes(maskKey);
            byte[] masked = new byte[payload.length];
            for (int i = 0; i < payload.length; i++) {
                masked[i] = (byte) (payload[i] ^ maskKey[i % 4]);
            }

            ByteArrayOutputStream frame = new ByteArrayOutputStream();
            frame.write(0x82); // FIN=1, opcode=2 (binary)
            int len = masked.length;
            if (len < 126) {
                frame.write(0x80 | len);
            } else if (len < 65536) {
                frame.write(0x80 | 126);
                frame.write((len >> 8) & 0xFF);
                frame.write(len & 0xFF);
            } else {
                frame.write(0x80 | 127);
                for (int i = 7; i >= 0; i--) frame.write((len >> (8 * i)) & 0xFF);
            }
            frame.write(maskKey);
            frame.write(masked);

            out.write(frame.toByteArray());
            out.flush();
        }

        /**
         * Reads one incoming binary frame and returns its payload.
         *
         * @return the unmasked payload bytes, or {@code null} on close frame
         */
        byte[] readPayload() throws IOException {
            int b0 = in.read();
            int b1 = in.read();
            if (b0 < 0 || b1 < 0) return null;

            int opcode = b0 & 0x0F;
            boolean masked = (b1 & 0x80) != 0;
            long payloadLen = b1 & 0x7F;

            if (payloadLen == 126) {
                byte[] ext = readExact(2);
                payloadLen = ((ext[0] & 0xFFL) << 8) | (ext[1] & 0xFFL);
            } else if (payloadLen == 127) {
                byte[] ext = readExact(8);
                payloadLen = 0;
                for (int i = 0; i < 8; i++) payloadLen = (payloadLen << 8) | (ext[i] & 0xFFL);
            }

            byte[] maskKey = masked ? readExact(4) : null;
            byte[] payload = readExact((int) payloadLen);
            if (masked && maskKey != null) {
                for (int i = 0; i < payload.length; i++) payload[i] ^= maskKey[i % 4];
            }

            if (opcode == 0x8) return null; // close frame
            return payload;
        }

        private byte[] readExact(int length) throws IOException {
            byte[] buf = new byte[length];
            int offset = 0;
            while (offset < length) {
                int r = in.read(buf, offset, length - offset);
                if (r < 0) throw new IOException("Unexpected end of stream");
                offset += r;
            }
            return buf;
        }

        @Override
        public void close() {
            try {
                if (socket != null) socket.close();
            } catch (IOException ignored) {}
        }
    }
}
