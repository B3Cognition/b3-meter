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
package com.b3meter.engine.service.interpreter;

import com.b3meter.engine.service.plan.PlanNode;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes an {@code MQTTSampler} {@link PlanNode} using raw TCP sockets.
 *
 * <p>Reads the following sampler properties:
 * <ul>
 *   <li>{@code MQTTSampler.broker} — broker address as {@code tcp://host:port}</li>
 *   <li>{@code MQTTSampler.topic} — MQTT topic string</li>
 *   <li>{@code MQTTSampler.message} — payload to publish</li>
 *   <li>{@code MQTTSampler.qos} — quality of service (0, 1, or 2; default 0)</li>
 *   <li>{@code MQTTSampler.action} — {@code publish} or {@code subscribe} (default publish)</li>
 *   <li>{@code MQTTSampler.timeout} — timeout in ms (default 5000)</li>
 * </ul>
 *
 * <p>Implements the MQTT 3.1.1 binary wire protocol for CONNECT, PUBLISH, SUBSCRIBE,
 * and DISCONNECT packets. No external MQTT library is used (Constitution Principle I).
 */
public final class MQTTSamplerExecutor {

    private static final Logger LOG = Logger.getLogger(MQTTSamplerExecutor.class.getName());

    private static final int DEFAULT_TIMEOUT_MS = 5000;

    // MQTT packet types (upper nibble of byte 1)
    static final byte CONNECT     = 0x10;
    static final byte CONNACK     = 0x20;
    static final byte PUBLISH     = 0x30;
    static final byte PUBACK      = 0x40;
    static final byte SUBSCRIBE   = (byte) 0x82; // 0x80 | 0x02 (fixed flags for SUBSCRIBE)
    static final byte SUBACK      = (byte) 0x90;
    static final byte DISCONNECT  = (byte) 0xE0;

    // CONNACK return codes
    static final int CONNACK_ACCEPTED = 0;

    private MQTTSamplerExecutor() {
        // utility class — not instantiable
    }

    /**
     * Executes the MQTT operation described by {@code node}.
     *
     * @param node      the MQTTSampler node; must not be {@code null}
     * @param result    the sample result to populate; must not be {@code null}
     * @param variables current VU variable scope for {@code ${varName}} substitution
     */
    public static void execute(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String broker = resolve(node.getStringProp("MQTTSampler.broker", ""), variables);
        String topic = resolve(node.getStringProp("MQTTSampler.topic", ""), variables);
        String message = resolve(node.getStringProp("MQTTSampler.message", ""), variables);
        int qos = node.getIntProp("MQTTSampler.qos", 0);
        String action = resolve(node.getStringProp("MQTTSampler.action", "publish"), variables);
        int timeout = node.getIntProp("MQTTSampler.timeout", DEFAULT_TIMEOUT_MS);

        if (broker.isBlank()) {
            result.setFailureMessage("MQTTSampler.broker is empty");
            return;
        }

        if (topic.isBlank()) {
            result.setFailureMessage("MQTTSampler.topic is empty");
            return;
        }

        // Parse broker address: tcp://host:port
        String host;
        int port;
        try {
            String addr = broker;
            if (addr.startsWith("tcp://")) {
                addr = addr.substring(6);
            } else if (addr.startsWith("mqtt://")) {
                addr = addr.substring(7);
            }
            int colonIdx = addr.lastIndexOf(':');
            if (colonIdx > 0) {
                host = addr.substring(0, colonIdx);
                port = Integer.parseInt(addr.substring(colonIdx + 1));
            } else {
                host = addr;
                port = 1883; // default MQTT port
            }
        } catch (NumberFormatException e) {
            result.setFailureMessage("Invalid broker address: " + broker);
            return;
        }

        LOG.log(Level.FINE, "MQTTSamplerExecutor: {0} to {1}:{2} topic={3}",
                new Object[]{action, host, port, topic});

        long start = System.currentTimeMillis();

        try (Socket socket = new Socket()) {
            socket.setSoTimeout(timeout);
            socket.connect(new InetSocketAddress(host, port), timeout);

            long connectTime = System.currentTimeMillis() - start;
            result.setConnectTimeMs(connectTime);

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // CONNECT
            String clientId = "jmn-" + UUID.randomUUID().toString().substring(0, 8);
            byte[] connectPacket = buildConnectPacket(clientId);
            out.write(connectPacket);
            out.flush();

            // Read CONNACK
            int connackReturnCode = readConnack(in);
            result.setLatencyMs(System.currentTimeMillis() - start - connectTime);

            if (connackReturnCode != CONNACK_ACCEPTED) {
                result.setStatusCode(connackReturnCode);
                result.setFailureMessage("MQTT CONNACK refused: return code " + connackReturnCode);
                return;
            }

            result.setStatusCode(CONNACK_ACCEPTED);

            if ("subscribe".equalsIgnoreCase(action)) {
                // SUBSCRIBE + wait for message
                byte[] subscribePacket = buildSubscribePacket(topic, qos);
                out.write(subscribePacket);
                out.flush();

                // Read SUBACK
                readSuback(in);

                // Wait for a PUBLISH from broker
                String received = readPublishMessage(in, timeout, start);
                result.setResponseBody(received);
            } else {
                // PUBLISH
                byte[] publishPacket = buildPublishPacket(topic, message, qos);
                out.write(publishPacket);
                out.flush();

                // For QoS 1, wait for PUBACK
                if (qos >= 1) {
                    readPuback(in);
                }

                result.setResponseBody(message);
            }

            // DISCONNECT
            out.write(new byte[]{DISCONNECT, 0x00});
            out.flush();

            long total = System.currentTimeMillis() - start;
            result.setTotalTimeMs(total);

        } catch (IOException e) {
            long total = System.currentTimeMillis() - start;
            result.setTotalTimeMs(total);
            result.setFailureMessage("MQTT error: " + e.getMessage());
            LOG.log(Level.WARNING, "MQTTSamplerExecutor: error for " + broker, e);
        }
    }

    // =========================================================================
    // MQTT Packet Builders (package-visible for testing)
    // =========================================================================

    /**
     * Builds an MQTT 3.1.1 CONNECT packet.
     *
     * @param clientId the client identifier
     * @return the complete CONNECT packet bytes
     */
    static byte[] buildConnectPacket(String clientId) {
        try {
            ByteArrayOutputStream variableHeader = new ByteArrayOutputStream();
            // Protocol Name: "MQTT"
            writeUtf8String(variableHeader, "MQTT");
            // Protocol Level: 4 (MQTT 3.1.1)
            variableHeader.write(0x04);
            // Connect Flags: Clean Session = 1, no will/user/pass
            variableHeader.write(0x02);
            // Keep Alive: 60 seconds
            variableHeader.write(0x00);
            variableHeader.write(0x3C);

            // Payload: Client ID
            ByteArrayOutputStream payload = new ByteArrayOutputStream();
            writeUtf8String(payload, clientId);

            byte[] vh = variableHeader.toByteArray();
            byte[] pl = payload.toByteArray();
            int remainingLength = vh.length + pl.length;

            ByteArrayOutputStream packet = new ByteArrayOutputStream();
            packet.write(CONNECT);
            writeRemainingLength(packet, remainingLength);
            packet.write(vh);
            packet.write(pl);

            return packet.toByteArray();
        } catch (IOException e) {
            // ByteArrayOutputStream never throws
            throw new IllegalStateException("Failed to build CONNECT packet", e);
        }
    }

    /**
     * Builds an MQTT PUBLISH packet.
     *
     * @param topic   the topic to publish to
     * @param message the message payload
     * @param qos     quality of service (0, 1, or 2)
     * @return the complete PUBLISH packet bytes
     */
    static byte[] buildPublishPacket(String topic, String message, int qos) {
        try {
            ByteArrayOutputStream variableHeader = new ByteArrayOutputStream();
            writeUtf8String(variableHeader, topic);

            // Packet Identifier (required for QoS 1 and 2)
            if (qos > 0) {
                variableHeader.write(0x00);
                variableHeader.write(0x01); // packet id = 1
            }

            byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
            byte[] vh = variableHeader.toByteArray();
            int remainingLength = vh.length + messageBytes.length;

            ByteArrayOutputStream packet = new ByteArrayOutputStream();
            // Fixed header: PUBLISH + QoS flags
            byte fixedByte = (byte) (PUBLISH | ((qos & 0x03) << 1));
            packet.write(fixedByte);
            writeRemainingLength(packet, remainingLength);
            packet.write(vh);
            packet.write(messageBytes);

            return packet.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build PUBLISH packet", e);
        }
    }

    /**
     * Builds an MQTT SUBSCRIBE packet.
     *
     * @param topic the topic to subscribe to
     * @param qos   requested quality of service
     * @return the complete SUBSCRIBE packet bytes
     */
    static byte[] buildSubscribePacket(String topic, int qos) {
        try {
            ByteArrayOutputStream variableHeader = new ByteArrayOutputStream();
            // Packet Identifier
            variableHeader.write(0x00);
            variableHeader.write(0x01); // packet id = 1

            ByteArrayOutputStream payload = new ByteArrayOutputStream();
            writeUtf8String(payload, topic);
            payload.write(qos & 0x03); // Requested QoS

            byte[] vh = variableHeader.toByteArray();
            byte[] pl = payload.toByteArray();
            int remainingLength = vh.length + pl.length;

            ByteArrayOutputStream packet = new ByteArrayOutputStream();
            packet.write(SUBSCRIBE);
            writeRemainingLength(packet, remainingLength);
            packet.write(vh);
            packet.write(pl);

            return packet.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build SUBSCRIBE packet", e);
        }
    }

    // =========================================================================
    // MQTT Packet Readers
    // =========================================================================

    /**
     * Reads and validates a CONNACK packet, returning the return code.
     *
     * @param in the input stream
     * @return CONNACK return code (0 = accepted)
     * @throws IOException if the packet is malformed or I/O error occurs
     */
    static int readConnack(DataInputStream in) throws IOException {
        int packetType = in.readUnsignedByte();
        if ((packetType & 0xF0) != (CONNACK & 0xFF)) {
            throw new IOException("Expected CONNACK (0x20), got 0x" +
                    Integer.toHexString(packetType));
        }
        int remainingLength = readRemainingLength(in);
        if (remainingLength != 2) {
            throw new IOException("Invalid CONNACK remaining length: " + remainingLength);
        }
        int flags = in.readUnsignedByte();    // Connect Acknowledge Flags
        int returnCode = in.readUnsignedByte(); // Connect Return Code
        return returnCode;
    }

    /**
     * Reads and validates a SUBACK packet.
     *
     * @param in the input stream
     * @throws IOException if the packet is malformed or I/O error occurs
     */
    static void readSuback(DataInputStream in) throws IOException {
        int packetType = in.readUnsignedByte();
        if ((packetType & 0xF0) != (SUBACK & 0xFF)) {
            throw new IOException("Expected SUBACK (0x90), got 0x" +
                    Integer.toHexString(packetType));
        }
        int remainingLength = readRemainingLength(in);
        // Skip packet identifier (2 bytes) + return codes
        byte[] rest = new byte[remainingLength];
        in.readFully(rest);
    }

    /**
     * Reads and validates a PUBACK packet.
     *
     * @param in the input stream
     * @throws IOException if the packet is malformed or I/O error occurs
     */
    static void readPuback(DataInputStream in) throws IOException {
        int packetType = in.readUnsignedByte();
        if ((packetType & 0xF0) != (PUBACK & 0xFF)) {
            throw new IOException("Expected PUBACK (0x40), got 0x" +
                    Integer.toHexString(packetType));
        }
        int remainingLength = readRemainingLength(in);
        byte[] rest = new byte[remainingLength];
        in.readFully(rest);
    }

    /**
     * Reads a PUBLISH packet from the broker (used in subscribe mode).
     *
     * @param in      the input stream
     * @param timeout overall timeout in ms
     * @param start   start timestamp for timeout calculation
     * @return the message payload as a string
     * @throws IOException if timeout or I/O error occurs
     */
    static String readPublishMessage(DataInputStream in, int timeout, long start)
            throws IOException {
        while (System.currentTimeMillis() - start < timeout) {
            int firstByte = in.readUnsignedByte();
            int packetType = firstByte & 0xF0;
            int remainingLength = readRemainingLength(in);

            if (packetType == (PUBLISH & 0xFF)) {
                // Read topic length
                int topicLen = in.readUnsignedShort();
                byte[] topicBytes = new byte[topicLen];
                in.readFully(topicBytes);

                int qos = (firstByte >> 1) & 0x03;
                int consumed = 2 + topicLen;

                // Skip packet identifier for QoS > 0
                if (qos > 0) {
                    in.readUnsignedShort(); // packet id
                    consumed += 2;
                }

                // Read payload
                int payloadLen = remainingLength - consumed;
                byte[] payload = new byte[payloadLen];
                in.readFully(payload);
                return new String(payload, StandardCharsets.UTF_8);
            } else {
                // Skip unknown packet
                byte[] skip = new byte[remainingLength];
                in.readFully(skip);
            }
        }
        throw new IOException("Timeout waiting for PUBLISH message");
    }

    // =========================================================================
    // Wire-level helpers (package-visible for testing)
    // =========================================================================

    /**
     * Writes a UTF-8 string with a 2-byte length prefix (MQTT string encoding).
     */
    static void writeUtf8String(ByteArrayOutputStream out, String str) throws IOException {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        out.write((bytes.length >> 8) & 0xFF);
        out.write(bytes.length & 0xFF);
        out.write(bytes);
    }

    /**
     * Writes the MQTT variable-length remaining-length encoding.
     *
     * <p>MQTT uses a variable-length encoding where each byte encodes 7 bits of the value
     * and the MSB indicates whether more bytes follow.
     */
    static void writeRemainingLength(ByteArrayOutputStream out, int length) {
        do {
            int digit = length % 128;
            length = length / 128;
            if (length > 0) {
                digit |= 0x80;
            }
            out.write(digit);
        } while (length > 0);
    }

    /**
     * Reads the MQTT variable-length remaining-length encoding.
     */
    static int readRemainingLength(DataInputStream in) throws IOException {
        int multiplier = 1;
        int value = 0;
        int encoded;
        do {
            encoded = in.readUnsignedByte();
            value += (encoded & 0x7F) * multiplier;
            multiplier *= 128;
            if (multiplier > 128 * 128 * 128 * 128) {
                throw new IOException("Malformed remaining length");
            }
        } while ((encoded & 0x80) != 0);
        return value;
    }

    private static String resolve(String value, Map<String, String> variables) {
        if (value == null) return "";
        return VariableResolver.resolve(value, variables);
    }
}
