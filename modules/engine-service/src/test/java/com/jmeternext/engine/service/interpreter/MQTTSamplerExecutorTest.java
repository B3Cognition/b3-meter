package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MQTTSamplerExecutor}.
 *
 * <p>Tests MQTT packet encoding/decoding at the wire level and error handling.
 * No live MQTT broker is required — packet construction and parsing are tested
 * directly via the package-visible helper methods.
 */
class MQTTSamplerExecutorTest {

    // =========================================================================
    // Null guards
    // =========================================================================

    @Test
    void execute_throwsOnNullNode() {
        SampleResult result = new SampleResult("mqtt-test");
        assertThrows(NullPointerException.class,
                () -> MQTTSamplerExecutor.execute(null, result, Map.of()));
    }

    @Test
    void execute_throwsOnNullResult() {
        PlanNode node = mqttNode("tcp://localhost:1883", "test/topic", "hello");
        assertThrows(NullPointerException.class,
                () -> MQTTSamplerExecutor.execute(node, null, Map.of()));
    }

    @Test
    void execute_throwsOnNullVariables() {
        PlanNode node = mqttNode("tcp://localhost:1883", "test/topic", "hello");
        SampleResult result = new SampleResult("mqtt-test");
        assertThrows(NullPointerException.class,
                () -> MQTTSamplerExecutor.execute(node, result, null));
    }

    // =========================================================================
    // Validation
    // =========================================================================

    @Test
    void execute_failsOnEmptyBroker() {
        PlanNode node = mqttNode("", "test/topic", "hello");
        SampleResult result = new SampleResult("mqtt-test");

        MQTTSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("broker is empty"));
    }

    @Test
    void execute_failsOnEmptyTopic() {
        PlanNode node = mqttNode("tcp://localhost:1883", "", "hello");
        SampleResult result = new SampleResult("mqtt-test");

        MQTTSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("topic is empty"));
    }

    @Test
    void execute_failsOnInvalidBrokerAddress() {
        PlanNode node = mqttNode("tcp://localhost:notaport", "test/topic", "hello");
        SampleResult result = new SampleResult("mqtt-test");

        MQTTSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("Invalid broker address"));
    }

    // =========================================================================
    // Connection error handling
    // =========================================================================

    @Test
    void execute_handlesConnectionRefused() {
        PlanNode node = PlanNode.builder("MQTTSampler", "mqtt-refused")
                .property("MQTTSampler.broker", "tcp://127.0.0.1:1")
                .property("MQTTSampler.topic", "test/topic")
                .property("MQTTSampler.message", "hello")
                .property("MQTTSampler.timeout", 1000)
                .build();
        SampleResult result = new SampleResult("mqtt-refused");

        MQTTSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("MQTT error"));
        assertTrue(result.getTotalTimeMs() >= 0);
    }

    // =========================================================================
    // CONNECT packet encoding
    // =========================================================================

    @Test
    void buildConnectPacket_hasCorrectProtocolName() {
        byte[] packet = MQTTSamplerExecutor.buildConnectPacket("test-client");

        // First byte should be CONNECT (0x10)
        assertEquals(0x10, packet[0] & 0xFF);

        // After remaining length, protocol name should be "MQTT"
        // Skip fixed header (1 byte type + 1-4 bytes remaining length)
        int rlBytes = remainingLengthBytes(packet, 1);
        int offset = 1 + rlBytes;

        // Protocol name length (2 bytes)
        int nameLen = ((packet[offset] & 0xFF) << 8) | (packet[offset + 1] & 0xFF);
        assertEquals(4, nameLen, "Protocol name should be 4 bytes");

        // Protocol name "MQTT"
        String name = new String(packet, offset + 2, 4, StandardCharsets.UTF_8);
        assertEquals("MQTT", name);
    }

    @Test
    void buildConnectPacket_hasProtocolLevel4() {
        byte[] packet = MQTTSamplerExecutor.buildConnectPacket("test-client");

        // Protocol level is at offset: 1 + RL_bytes + 2 + 4 (name) = ... + 6
        int rlBytes = remainingLengthBytes(packet, 1);
        int offset = 1 + rlBytes + 6; // skip type + RL + name length (2) + "MQTT" (4)

        assertEquals(0x04, packet[offset] & 0xFF, "Protocol level should be 4 (MQTT 3.1.1)");
    }

    @Test
    void buildConnectPacket_hasCleanSession() {
        byte[] packet = MQTTSamplerExecutor.buildConnectPacket("test-client");

        int rlBytes = remainingLengthBytes(packet, 1);
        int offset = 1 + rlBytes + 7; // skip to connect flags

        assertEquals(0x02, packet[offset] & 0xFF, "Connect flags should have Clean Session set");
    }

    @Test
    void buildConnectPacket_containsClientId() {
        String clientId = "my-client-123";
        byte[] packet = MQTTSamplerExecutor.buildConnectPacket(clientId);

        String packetStr = new String(packet, StandardCharsets.UTF_8);
        assertTrue(packetStr.contains(clientId), "Packet should contain the client ID");
    }

    // =========================================================================
    // PUBLISH packet encoding
    // =========================================================================

    @Test
    void buildPublishPacket_qos0_hasCorrectType() {
        byte[] packet = MQTTSamplerExecutor.buildPublishPacket("test/topic", "hello", 0);

        // First byte: PUBLISH (0x30) + QoS 0 flags (no additional bits)
        assertEquals(0x30, packet[0] & 0xFF, "QoS 0 PUBLISH should be 0x30");
    }

    @Test
    void buildPublishPacket_qos1_hasCorrectType() {
        byte[] packet = MQTTSamplerExecutor.buildPublishPacket("test/topic", "hello", 1);

        // First byte: PUBLISH (0x30) + QoS 1 (bit 1 set = 0x02)
        assertEquals(0x32, packet[0] & 0xFF, "QoS 1 PUBLISH should be 0x32");
    }

    @Test
    void buildPublishPacket_containsTopicAndMessage() {
        byte[] packet = MQTTSamplerExecutor.buildPublishPacket("my/topic", "test-payload", 0);
        String packetStr = new String(packet, StandardCharsets.UTF_8);

        assertTrue(packetStr.contains("my/topic"), "Packet should contain topic");
        assertTrue(packetStr.contains("test-payload"), "Packet should contain message");
    }

    @Test
    void buildPublishPacket_qos0_noPacketId() {
        byte[] packet0 = MQTTSamplerExecutor.buildPublishPacket("t", "m", 0);
        byte[] packet1 = MQTTSamplerExecutor.buildPublishPacket("t", "m", 1);

        // QoS 1 packet should be 2 bytes longer (packet identifier)
        assertEquals(packet0.length + 2, packet1.length,
                "QoS 1 should be 2 bytes longer than QoS 0 (packet ID)");
    }

    // =========================================================================
    // SUBSCRIBE packet encoding
    // =========================================================================

    @Test
    void buildSubscribePacket_hasCorrectType() {
        byte[] packet = MQTTSamplerExecutor.buildSubscribePacket("test/topic", 0);

        // First byte: SUBSCRIBE with fixed flags (0x82)
        assertEquals(0x82, packet[0] & 0xFF, "SUBSCRIBE should be 0x82");
    }

    @Test
    void buildSubscribePacket_containsTopic() {
        byte[] packet = MQTTSamplerExecutor.buildSubscribePacket("my/subscribe/topic", 1);
        String packetStr = new String(packet, StandardCharsets.UTF_8);

        assertTrue(packetStr.contains("my/subscribe/topic"), "Packet should contain topic");
    }

    @Test
    void buildSubscribePacket_containsQos() {
        byte[] packet = MQTTSamplerExecutor.buildSubscribePacket("t", 2);

        // Last byte should be QoS value
        assertEquals(2, packet[packet.length - 1] & 0xFF,
                "Last byte should be requested QoS");
    }

    // =========================================================================
    // CONNACK reading
    // =========================================================================

    @Test
    void readConnack_accepted() throws IOException {
        // CONNACK: type=0x20, remaining=2, flags=0x00, returnCode=0x00
        byte[] connack = {0x20, 0x02, 0x00, 0x00};
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(connack));

        int returnCode = MQTTSamplerExecutor.readConnack(in);
        assertEquals(0, returnCode, "Return code should be 0 (accepted)");
    }

    @Test
    void readConnack_refused() throws IOException {
        // CONNACK: returnCode=5 (not authorized)
        byte[] connack = {0x20, 0x02, 0x00, 0x05};
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(connack));

        int returnCode = MQTTSamplerExecutor.readConnack(in);
        assertEquals(5, returnCode, "Return code should be 5 (not authorized)");
    }

    @Test
    void readConnack_throwsOnWrongPacketType() {
        // Send a PUBLISH packet instead of CONNACK
        byte[] wrong = {0x30, 0x02, 0x00, 0x00};
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(wrong));

        assertThrows(IOException.class, () -> MQTTSamplerExecutor.readConnack(in));
    }

    @Test
    void readConnack_throwsOnInvalidLength() {
        // CONNACK with wrong remaining length
        byte[] wrong = {0x20, 0x03, 0x00, 0x00, 0x00};
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(wrong));

        assertThrows(IOException.class, () -> MQTTSamplerExecutor.readConnack(in));
    }

    // =========================================================================
    // Remaining length encoding/decoding
    // =========================================================================

    @Test
    void remainingLength_singleByte() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MQTTSamplerExecutor.writeRemainingLength(out, 127);
        byte[] encoded = out.toByteArray();

        assertEquals(1, encoded.length);
        assertEquals(127, encoded[0] & 0xFF);

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(encoded));
        assertEquals(127, MQTTSamplerExecutor.readRemainingLength(in));
    }

    @Test
    void remainingLength_twoByte() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MQTTSamplerExecutor.writeRemainingLength(out, 128);
        byte[] encoded = out.toByteArray();

        assertEquals(2, encoded.length);

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(encoded));
        assertEquals(128, MQTTSamplerExecutor.readRemainingLength(in));
    }

    @Test
    void remainingLength_largeValue() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MQTTSamplerExecutor.writeRemainingLength(out, 16384);
        byte[] encoded = out.toByteArray();

        assertEquals(3, encoded.length);

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(encoded));
        assertEquals(16384, MQTTSamplerExecutor.readRemainingLength(in));
    }

    @Test
    void remainingLength_roundTrip() throws IOException {
        int[] values = {0, 1, 127, 128, 255, 256, 16383, 16384, 2097151, 268435455};
        for (int value : values) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MQTTSamplerExecutor.writeRemainingLength(out, value);
            byte[] encoded = out.toByteArray();

            DataInputStream in = new DataInputStream(new ByteArrayInputStream(encoded));
            int decoded = MQTTSamplerExecutor.readRemainingLength(in);

            assertEquals(value, decoded, "Round-trip failed for value " + value);
        }
    }

    // =========================================================================
    // UTF-8 string encoding
    // =========================================================================

    @Test
    void writeUtf8String_encodesLengthPrefix() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MQTTSamplerExecutor.writeUtf8String(out, "hello");
        byte[] encoded = out.toByteArray();

        // 2 bytes length + 5 bytes "hello"
        assertEquals(7, encoded.length);
        assertEquals(0, encoded[0] & 0xFF);  // MSB of length
        assertEquals(5, encoded[1] & 0xFF);  // LSB of length
        assertEquals("hello", new String(encoded, 2, 5, StandardCharsets.UTF_8));
    }

    @Test
    void writeUtf8String_encodesEmptyString() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MQTTSamplerExecutor.writeUtf8String(out, "");
        byte[] encoded = out.toByteArray();

        assertEquals(2, encoded.length);
        assertEquals(0, encoded[0] & 0xFF);
        assertEquals(0, encoded[1] & 0xFF);
    }

    // =========================================================================
    // PUBLISH message reading
    // =========================================================================

    @Test
    void readPublishMessage_decodesQos0() throws IOException {
        // Build a PUBLISH packet: QoS 0, topic "t", payload "hello"
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        // Fixed header: PUBLISH QoS 0 = 0x30
        // Variable header: topic length (2 bytes) + topic + payload
        byte[] topic = "t".getBytes(StandardCharsets.UTF_8);
        byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
        int remainingLength = 2 + topic.length + payload.length;

        packet.write(0x30); // PUBLISH QoS 0
        MQTTSamplerExecutor.writeRemainingLength(packet, remainingLength);
        // Topic length
        packet.write(0x00);
        packet.write(topic.length);
        packet.write(topic);
        packet.write(payload);

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(packet.toByteArray()));
        String received = MQTTSamplerExecutor.readPublishMessage(in, 5000, System.currentTimeMillis());

        assertEquals("hello", received);
    }

    @Test
    void readPublishMessage_decodesQos1() throws IOException {
        // Build a PUBLISH packet: QoS 1, topic "t", payload "world"
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        byte[] topic = "t".getBytes(StandardCharsets.UTF_8);
        byte[] payload = "world".getBytes(StandardCharsets.UTF_8);
        int remainingLength = 2 + topic.length + 2 /* packet id */ + payload.length;

        packet.write(0x32); // PUBLISH QoS 1
        MQTTSamplerExecutor.writeRemainingLength(packet, remainingLength);
        // Topic length
        packet.write(0x00);
        packet.write(topic.length);
        packet.write(topic);
        // Packet identifier
        packet.write(0x00);
        packet.write(0x01);
        packet.write(payload);

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(packet.toByteArray()));
        String received = MQTTSamplerExecutor.readPublishMessage(in, 5000, System.currentTimeMillis());

        assertEquals("world", received);
    }

    // =========================================================================
    // Variable substitution
    // =========================================================================

    @Test
    void execute_resolvesVariables() {
        PlanNode node = PlanNode.builder("MQTTSampler", "mqtt-vars")
                .property("MQTTSampler.broker", "tcp://${mqttHost}:${mqttPort}")
                .property("MQTTSampler.topic", "${topic}")
                .property("MQTTSampler.message", "${msg}")
                .property("MQTTSampler.timeout", 500)
                .build();

        SampleResult result = new SampleResult("mqtt-vars");
        Map<String, String> vars = Map.of(
                "mqttHost", "127.0.0.1",
                "mqttPort", "1",
                "topic", "test/topic",
                "msg", "hello"
        );

        MQTTSamplerExecutor.execute(node, result, vars);

        // Connection will fail but variables were resolved (no "empty" error)
        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("MQTT error"),
                "Should fail with MQTT error, not validation error");
    }

    // =========================================================================
    // Broker address parsing
    // =========================================================================

    @Test
    void execute_parsesBrokerWithoutScheme() {
        PlanNode node = PlanNode.builder("MQTTSampler", "mqtt-noscheme")
                .property("MQTTSampler.broker", "127.0.0.1:1")
                .property("MQTTSampler.topic", "test")
                .property("MQTTSampler.message", "hi")
                .property("MQTTSampler.timeout", 500)
                .build();

        SampleResult result = new SampleResult("mqtt-noscheme");
        MQTTSamplerExecutor.execute(node, result, Map.of());

        // Should attempt connection, not fail with validation error
        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("MQTT error"));
    }

    @Test
    void execute_parsesMqttScheme() {
        PlanNode node = PlanNode.builder("MQTTSampler", "mqtt-scheme")
                .property("MQTTSampler.broker", "mqtt://127.0.0.1:1")
                .property("MQTTSampler.topic", "test")
                .property("MQTTSampler.message", "hi")
                .property("MQTTSampler.timeout", 500)
                .build();

        SampleResult result = new SampleResult("mqtt-scheme");
        MQTTSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("MQTT error"));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static PlanNode mqttNode(String broker, String topic, String message) {
        return PlanNode.builder("MQTTSampler", "mqtt-test")
                .property("MQTTSampler.broker", broker)
                .property("MQTTSampler.topic", topic)
                .property("MQTTSampler.message", message)
                .property("MQTTSampler.timeout", 500)
                .build();
    }

    /** Returns the number of bytes used by the remaining length field. */
    private static int remainingLengthBytes(byte[] packet, int offset) {
        int count = 0;
        while (offset + count < packet.length) {
            int b = packet[offset + count] & 0xFF;
            count++;
            if ((b & 0x80) == 0) break;
        }
        return count;
    }
}
