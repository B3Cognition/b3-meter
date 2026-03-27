# Phase 6: Remaining Protocol Gaps — Implementation Specification

**Author**: INVESTIGATOR agent
**Date**: 2026-03-26
**Scope**: 7 protocol gaps — gRPC (mock+JMX), MQTT (JMX+E2E), WebRTC (expansion), DASH (mock+tests), QUIC/HTTP3 (feasibility), FTP (new sampler), LDAP (new sampler)

---

## Table of Contents

1. [gRPC — Mock Server + JMX Plans](#1-grpc--mock-server--jmx-plans)
2. [MQTT — JMX Plans + E2E Tests](#2-mqtt--jmx-plans--e2e-tests)
3. [WebRTC — Expanded Beyond Signaling](#3-webrtc--expanded-beyond-signaling)
4. [DASH (MPEG-DASH) — Mock Server + Tests](#4-dash-mpeg-dash--mock-server--tests)
5. [QUIC/HTTP3 — Feasibility Analysis](#5-quichttp3--feasibility-analysis)
6. [FTP — Sampler + Mock Server](#6-ftp--sampler--mock-server)
7. [LDAP — Sampler + Mock Server](#7-ldap--sampler--mock-server)

---

## Architecture Conventions (from existing codebase)

Before detailing each protocol, these are the patterns extracted from the existing implementation that all new work MUST follow:

- **Constitution Principle I**: Framework-free. Only JDK types allowed in `engine-service`. No Spring, no external libraries.
- **Sampler executor pattern**: Static utility class with `public static void execute(PlanNode node, SampleResult result, Map<String,String> variables [, HttpClientFactory])`. Private constructor. Uses `VariableResolver.resolve()` for `${varName}` substitution.
- **SampleResult contract**: Executors populate `connectTimeMs`, `latencyMs`, `totalTimeMs`, `statusCode`, `responseBody`. Call `setFailureMessage()` on error (auto-sets `success=false`).
- **NodeInterpreter dispatch**: New sampler types require a new `case` branch in the `dispatchNode()` switch expression. Pattern: create `SampleResult`, call static executor, call `applyPostProcessors()`, yield `List.of(r)`.
- **Mock server pattern**: Node.js, single-file, `http.createServer` or `net.createServer`. Port via `process.env.PORT || <default>`. Health endpoint at `GET /health`. Deterministic test data using crypto hashing where applicable.
- **Property naming**: `<SamplerType>.<propertyName>` (e.g., `MQTTSampler.broker`, `GrpcSampler.host`).

---

## 1. gRPC — Mock Server + JMX Plans

### 1.1 Status

- **GrpcSamplerExecutor**: EXISTS at `modules/engine-service/src/main/java/.../interpreter/GrpcSamplerExecutor.java`
- **GrpcSamplerExecutorTest**: EXISTS
- **Mock server**: MISSING — no `test-servers/grpc-mock/`
- **JMX plan**: MISSING — no `grpc-smoke.jmx`
- **Current implementation**: Sends HTTP/2 POST with gRPC length-prefixed frames. Supports `GrpcSampler.host`, `.port`, `.service`, `.method`, `.requestBody`, `.useTls`. Reads `grpc-status` from response headers.

### 1.2 Implementation Approach

No changes needed to `GrpcSamplerExecutor.java` — it already handles unary gRPC calls via HTTP/2. The gaps are:

1. A mock gRPC server to test against
2. JMX test plans that exercise the sampler
3. Streaming support (future — requires executor changes)

For streaming (ServerStream, ClientStream, Bidi), the current executor model is request-response only. Streaming would require a new `GrpcStreamSamplerExecutor` that reads multiple length-prefixed frames from the response body. This is documented but deferred to a separate spec.

### 1.3 Mock Server Design

**Location**: `test-servers/grpc-mock/`

**Language**: Node.js
**Package**: `@grpc/grpc-js` + `@grpc/proto-loader`
**Port**: `process.env.PORT || 50051`

**Proto definition** (`greeter.proto`):

```protobuf
syntax = "proto3";

package greeter;

service Greeter {
  // Unary
  rpc SayHello (HelloRequest) returns (HelloReply);
  // Server streaming
  rpc ListFeatures (AreaRequest) returns (stream Feature);
  // Client streaming
  rpc RecordRoute (stream Point) returns (RouteSummary);
  // Bidirectional streaming
  rpc RouteChat (stream RouteNote) returns (stream RouteNote);
}

service Health {
  rpc Check (HealthCheckRequest) returns (HealthCheckResponse);
}

message HelloRequest {
  string name = 1;
}

message HelloReply {
  string message = 1;
  int64 timestamp_ms = 2;
}

message AreaRequest {
  int32 lo_latitude = 1;
  int32 hi_latitude = 2;
  int32 lo_longitude = 3;
  int32 hi_longitude = 4;
}

message Feature {
  string name = 1;
  int32 latitude = 2;
  int32 longitude = 3;
}

message Point {
  int32 latitude = 1;
  int32 longitude = 2;
}

message RouteSummary {
  int32 point_count = 1;
  int32 distance = 2;
  int32 elapsed_time = 3;
}

message RouteNote {
  Point location = 1;
  string message = 2;
}

message HealthCheckRequest {
  string service = 1;
}

message HealthCheckResponse {
  enum ServingStatus {
    UNKNOWN = 0;
    SERVING = 1;
    NOT_SERVING = 2;
  }
  ServingStatus status = 1;
}
```

**Server implementation** (`server.js`):

```javascript
const grpc = require('@grpc/grpc-js');
const protoLoader = require('@grpc/proto-loader');
const path = require('path');

const PORT = process.env.PORT || 50051;

const PROTO_PATH = path.join(__dirname, 'greeter.proto');
const packageDef = protoLoader.loadSync(PROTO_PATH, {
  keepCase: true,
  longs: String,
  enums: String,
  defaults: true,
  oneofs: true,
});
const proto = grpc.loadPackageDefinition(packageDef).greeter;

// Deterministic feature database
const FEATURES = [
  { name: 'Feature Alpha',   latitude: 409146138,  longitude: -746188906 },
  { name: 'Feature Beta',    latitude: 404318328,  longitude: -740835638 },
  { name: 'Feature Gamma',   latitude: 419999544,  longitude: -740371136 },
  { name: 'Feature Delta',   latitude: 414395978,  longitude: -746268012 },
  { name: 'Feature Epsilon', latitude: 408122808,  longitude: -743999179 },
];

function sayHello(call, callback) {
  const name = call.request.name || 'World';
  callback(null, {
    message: `Hello, ${name}!`,
    timestamp_ms: Date.now().toString(),
  });
}

function listFeatures(call) {
  const area = call.request;
  for (const f of FEATURES) {
    if (f.latitude >= area.lo_latitude && f.latitude <= area.hi_latitude
        && f.longitude >= area.lo_longitude && f.longitude <= area.hi_longitude) {
      call.write(f);
    }
  }
  // If no area filter, send all
  if (!area.lo_latitude && !area.hi_latitude) {
    for (const f of FEATURES) {
      call.write(f);
    }
  }
  call.end();
}

function recordRoute(call, callback) {
  let pointCount = 0;
  let distance = 0;
  let lastPoint = null;
  const startTime = Date.now();

  call.on('data', (point) => {
    pointCount++;
    if (lastPoint) {
      distance += Math.abs(point.latitude - lastPoint.latitude)
                + Math.abs(point.longitude - lastPoint.longitude);
    }
    lastPoint = point;
  });

  call.on('end', () => {
    callback(null, {
      point_count: pointCount,
      distance: distance,
      elapsed_time: Math.floor((Date.now() - startTime) / 1000),
    });
  });
}

function routeChat(call) {
  call.on('data', (note) => {
    // Echo back with prefix
    call.write({
      location: note.location,
      message: `[echo] ${note.message}`,
    });
  });
  call.on('end', () => call.end());
}

function healthCheck(call, callback) {
  callback(null, { status: 'SERVING' });
}

const server = new grpc.Server();
server.addService(proto.Greeter.service, {
  SayHello: sayHello,
  ListFeatures: listFeatures,
  RecordRoute: recordRoute,
  RouteChat: routeChat,
});
// Health service would use grpc.health.v1 package in production;
// simplified here for testing
server.addService(proto.Health.service, {
  Check: healthCheck,
});

server.bindAsync(
  `0.0.0.0:${PORT}`,
  grpc.ServerCredentials.createInsecure(),
  (err, port) => {
    if (err) { console.error(err); process.exit(1); }
    console.log(`gRPC mock server listening on port ${port}`);
  }
);
```

**`package.json`**:
```json
{
  "name": "grpc-mock",
  "version": "1.0.0",
  "private": true,
  "scripts": { "start": "node server.js" },
  "dependencies": {
    "@grpc/grpc-js": "^1.10.0",
    "@grpc/proto-loader": "^0.7.0"
  }
}
```

### 1.4 JMX Schema

```xml
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="gRPC Smoke Test">
      <boolProp name="TestPlan.serialize_threadgroups">false</boolProp>
    </TestPlan>
    <hashTree>
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup"
                   testname="gRPC Thread Group">
        <intProp name="ThreadGroup.num_threads">2</intProp>
        <elementProp name="ThreadGroup.main_controller" elementType="LoopController">
          <intProp name="LoopController.loops">5</intProp>
        </elementProp>
      </ThreadGroup>
      <hashTree>

        <!-- Unary: SayHello -->
        <GenericSampler guiclass="GenericSamplerGui" testclass="GrpcSampler"
                        testname="gRPC SayHello">
          <stringProp name="GrpcSampler.host">${GRPC_HOST}</stringProp>
          <intProp name="GrpcSampler.port">${GRPC_PORT}</intProp>
          <stringProp name="GrpcSampler.service">greeter.Greeter</stringProp>
          <stringProp name="GrpcSampler.method">SayHello</stringProp>
          <stringProp name="GrpcSampler.requestBody">{"name": "jMeter Next"}</stringProp>
          <boolProp name="GrpcSampler.useTls">false</boolProp>
        </GenericSampler>
        <hashTree>
          <!-- Assert response contains greeting -->
          <ResponseAssertion guiclass="AssertionGui" testclass="ResponseAssertion"
                             testname="Assert Hello Response">
            <stringProp name="Assertion.test_field">Assertion.response_data</stringProp>
            <stringProp name="Assertion.test_type">2</stringProp>
            <collectionProp name="Asserion.test_strings">
              <stringProp>Hello, jMeter Next!</stringProp>
            </collectionProp>
          </ResponseAssertion>
        </hashTree>

        <!-- Health Check -->
        <GenericSampler guiclass="GenericSamplerGui" testclass="GrpcSampler"
                        testname="gRPC Health Check">
          <stringProp name="GrpcSampler.host">${GRPC_HOST}</stringProp>
          <intProp name="GrpcSampler.port">${GRPC_PORT}</intProp>
          <stringProp name="GrpcSampler.service">greeter.Health</stringProp>
          <stringProp name="GrpcSampler.method">Check</stringProp>
          <stringProp name="GrpcSampler.requestBody">{"service": ""}</stringProp>
          <boolProp name="GrpcSampler.useTls">false</boolProp>
        </GenericSampler>
        <hashTree/>

      </hashTree>
    </hashTree>
  </hashTree>
</jmeterTestPlan>
```

### 1.5 Test Scenarios

| Scenario | Type | Description |
|----------|------|-------------|
| Unary happy path | Smoke | `SayHello("World")` returns `"Hello, World!"` |
| Unary with variable | Functional | `SayHello("${username}")` after extractor sets username |
| Health check | Smoke | `Health.Check` returns `SERVING` |
| Invalid method | Error | Call non-existent method, expect gRPC status 12 (UNIMPLEMENTED) |
| Connection refused | Error | Target port closed, expect `setFailureMessage` with IOException |
| Load test | Performance | 10 VUs, 100 loops, measure p95 < 50ms against localhost mock |

### 1.6 Metrics

| Metric | Source | Description |
|--------|--------|-------------|
| `connectTimeMs` | `HttpResponse.connectTimeMs()` | TCP+TLS handshake time |
| `latencyMs` | `HttpResponse.latencyMs()` | Time-to-first-byte (gRPC headers received) |
| `totalTimeMs` | `System.currentTimeMillis()` delta | Full round-trip including response body |
| `statusCode` | `grpc-status` header or HTTP status mapping | gRPC status code (0=OK) |
| `streamMessageCount` | Future: count length-prefixed frames in response | Number of messages in streaming response |

### 1.7 Priority: **P1** — do now

**Justification**: GrpcSamplerExecutor already exists and passes tests. Adding a mock server + JMX plan is the smallest delta to make gRPC end-to-end testable. gRPC is the #2 protocol for modern microservice load testing after HTTP.

### 1.8 Effort: **S** (Small)

Mock server is ~100 lines. JMX plan is boilerplate. No Java changes needed for unary support.

---

## 2. MQTT — JMX Plans + E2E Tests

### 2.1 Status

- **MQTTSamplerExecutor**: EXISTS — full MQTT 3.1.1 wire protocol implementation (CONNECT, PUBLISH, SUBSCRIBE, DISCONNECT) using raw `java.net.Socket`.
- **MQTTSamplerExecutorTest**: EXISTS
- **MQTT mock broker**: EXISTS at `test-servers/mqtt-mock/broker.js` — TCP server handling CONNECT/PUBLISH/SUBSCRIBE/UNSUBSCRIBE/PINGREQ/DISCONNECT. Supports retained messages and topic-based fan-out.
- **JMX plan**: MISSING
- **E2E integration tests**: MISSING

### 2.2 Implementation Approach

The executor and mock broker both exist. The gap is:

1. JMX plans that the NodeInterpreter can execute
2. Integration tests that start the mock broker, run the JMX plan, and verify results

**Key constraint**: `MQTTSampler` uses `testclass="MQTTSampler"` in JMX, NOT `HTTPSamplerProxy`. The NodeInterpreter already has a `case "MQTTSampler"` branch that dispatches to `MQTTSamplerExecutor.execute()`.

### 2.3 Mock Server Design

Already exists at `test-servers/mqtt-mock/broker.js`. No changes needed for basic scenarios. Enhancements for advanced E2E tests:

- **QoS 1 acknowledgment**: Already implemented (sends PUBACK for QoS 1 PUBLISH)
- **Retained messages**: Already implemented (stores last message per topic)
- **Wildcard topics**: NOT implemented. Add `+` (single-level) and `#` (multi-level) matching if needed for test scenarios. **Recommendation**: defer wildcards — not needed for smoke tests.

### 2.4 JMX Schema

**`mqtt-smoke.jmx`**:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="MQTT Smoke Test">
      <boolProp name="TestPlan.serialize_threadgroups">true</boolProp>
    </TestPlan>
    <hashTree>

      <!-- Publisher Thread Group -->
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup"
                   testname="MQTT Publishers">
        <intProp name="ThreadGroup.num_threads">1</intProp>
        <elementProp name="ThreadGroup.main_controller" elementType="LoopController">
          <intProp name="LoopController.loops">10</intProp>
        </elementProp>
      </ThreadGroup>
      <hashTree>

        <!-- Publish QoS 0 -->
        <GenericSampler guiclass="GenericSamplerGui" testclass="MQTTSampler"
                        testname="Publish QoS 0">
          <stringProp name="MQTTSampler.broker">tcp://${MQTT_HOST}:${MQTT_PORT}</stringProp>
          <stringProp name="MQTTSampler.topic">test/metrics</stringProp>
          <stringProp name="MQTTSampler.message">{"cpu": 42, "ts": ${__time()}}</stringProp>
          <intProp name="MQTTSampler.qos">0</intProp>
          <stringProp name="MQTTSampler.action">publish</stringProp>
          <intProp name="MQTTSampler.timeout">5000</intProp>
        </GenericSampler>
        <hashTree/>

        <!-- Publish QoS 1 -->
        <GenericSampler guiclass="GenericSamplerGui" testclass="MQTTSampler"
                        testname="Publish QoS 1">
          <stringProp name="MQTTSampler.broker">tcp://${MQTT_HOST}:${MQTT_PORT}</stringProp>
          <stringProp name="MQTTSampler.topic">test/reliable</stringProp>
          <stringProp name="MQTTSampler.message">{"event": "order_placed", "id": 1}</stringProp>
          <intProp name="MQTTSampler.qos">1</intProp>
          <stringProp name="MQTTSampler.action">publish</stringProp>
          <intProp name="MQTTSampler.timeout">5000</intProp>
        </GenericSampler>
        <hashTree/>

        <!-- Publish retained message -->
        <GenericSampler guiclass="GenericSamplerGui" testclass="MQTTSampler"
                        testname="Publish Retained">
          <stringProp name="MQTTSampler.broker">tcp://${MQTT_HOST}:${MQTT_PORT}</stringProp>
          <stringProp name="MQTTSampler.topic">test/status</stringProp>
          <stringProp name="MQTTSampler.message">{"status": "online"}</stringProp>
          <intProp name="MQTTSampler.qos">0</intProp>
          <stringProp name="MQTTSampler.action">publish</stringProp>
          <intProp name="MQTTSampler.timeout">5000</intProp>
        </GenericSampler>
        <hashTree/>

      </hashTree>

      <!-- Subscriber Thread Group -->
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup"
                   testname="MQTT Subscribers">
        <intProp name="ThreadGroup.num_threads">1</intProp>
        <elementProp name="ThreadGroup.main_controller" elementType="LoopController">
          <intProp name="LoopController.loops">1</intProp>
        </elementProp>
      </ThreadGroup>
      <hashTree>

        <GenericSampler guiclass="GenericSamplerGui" testclass="MQTTSampler"
                        testname="Subscribe test/metrics">
          <stringProp name="MQTTSampler.broker">tcp://${MQTT_HOST}:${MQTT_PORT}</stringProp>
          <stringProp name="MQTTSampler.topic">test/metrics</stringProp>
          <stringProp name="MQTTSampler.message"></stringProp>
          <intProp name="MQTTSampler.qos">0</intProp>
          <stringProp name="MQTTSampler.action">subscribe</stringProp>
          <intProp name="MQTTSampler.timeout">10000</intProp>
        </GenericSampler>
        <hashTree/>

      </hashTree>

    </hashTree>
  </hashTree>
</jmeterTestPlan>
```

**Important JMX notes**:
- `TestPlan.serialize_threadgroups=true` ensures the publisher runs BEFORE the subscriber starts. This is needed because the subscriber waits for a PUBLISH message that must already be in-flight or retained.
- The subscriber with `action=subscribe` will CONNECT, SUBSCRIBE, wait for one PUBLISH message (up to timeout), then DISCONNECT.
- For pub/sub E2E testing where publisher and subscriber must overlap, use a separate JMX where both thread groups run concurrently with a timer to stagger starts.

### 2.5 Test Scenarios

| Scenario | Type | Description |
|----------|------|-------------|
| Publish QoS 0 | Smoke | Publish to topic, verify `success=true`, `statusCode=0` (CONNACK accepted) |
| Publish QoS 1 | Functional | Publish with QoS 1, verify PUBACK received (no IOException) |
| Subscribe receives | Functional | Publisher sends to topic X, subscriber on topic X receives the message |
| Retained message | Functional | Publish retained, then subscribe — subscriber gets retained msg immediately |
| Connection refused | Error | Invalid broker address, verify `failureMessage` set |
| Empty topic | Error | Empty `MQTTSampler.topic`, verify early exit with error |
| Timeout on subscribe | Error | Subscribe to quiet topic, verify IOException after timeout |
| Load: pub throughput | Performance | 10 VUs, 1000 loops, publish QoS 0 — measure msg/sec |

### 2.6 Metrics

| Metric | Source | Description |
|--------|--------|-------------|
| `connectTimeMs` | Socket.connect() delta | TCP connection establishment time |
| `latencyMs` | CONNACK receipt time minus connect time | Broker handshake latency |
| `totalTimeMs` | Full operation time | CONNECT + PUBLISH/SUBSCRIBE + DISCONNECT |
| `statusCode` | CONNACK return code | 0 = accepted, 1-5 = various refusal codes |
| `responseBody` | Published message or received message | For extractors/assertions |

### 2.7 Priority: **P1** — do now

**Justification**: Both executor and mock broker exist. Only JMX plans and E2E tests are missing. This is the lowest-effort gap to close and validates the full MQTT pipeline end-to-end.

### 2.8 Effort: **S** (Small)

JMX plan creation + 1 integration test file (~200 lines Java).

---

## 3. WebRTC — Expanded Beyond Signaling

### 3.1 Status

- **WebRTCSamplerExecutor**: EXISTS — signaling-only. POSTs SDP offer to a URL, reads SDP answer, measures round-trip.
- **WebRTCSamplerExecutorTest**: EXISTS
- **Mock server**: MISSING
- **Properties**: `WebRTCSampler.signalingUrl`, `.offerSdp` (or "generate" for minimal SDP), `.connectTimeout`

### 3.2 Implementation Approach

#### What CAN be done with pure JDK (no native libs)

| Capability | JDK API | Description |
|------------|---------|-------------|
| SDP offer/answer exchange | `java.net.http.HttpClient` | Already implemented. POST SDP to signaling endpoint, measure RTT. |
| ICE candidate gathering simulation | `java.net.DatagramSocket` | Send STUN Binding Request (RFC 5389) to a STUN server via UDP. Parse Binding Response to get server-reflexive candidate. Measures NAT traversal latency. |
| DTLS handshake timing | `javax.net.ssl.SSLEngine` with `DatagramSocket` | JDK 21 includes DTLS 1.2 support via `SSLEngine` in datagram mode. Create DTLS client handshake, measure time to handshake completion. |
| TURN allocation | `java.net.DatagramSocket` | Send TURN Allocate request (RFC 5766), measure allocation time. Requires credential pair. |

#### What REQUIRES native libs (OUT OF SCOPE)

| Capability | Native Lib | Why JDK cannot do it |
|------------|-----------|----------------------|
| SRTP media decryption | libsrtp2 | JDK has no SRTP cipher suite. Would need JNI. |
| Actual media reception/decode | libwebrtc / GStreamer | Media pipeline is C++ with codec acceleration. |
| Full ICE agent (connectivity checks) | libnice | ICE state machine with STUN binding on all candidate pairs. Too complex for pure Java in test context. |
| Jitter buffer / packet loss stats | libwebrtc | Requires RTP session management. |

#### Recommended new properties for expanded executor

```
WebRTCSampler.signalingUrl    — (existing) SDP exchange URL
WebRTCSampler.offerSdp        — (existing) SDP body or "generate"
WebRTCSampler.connectTimeout  — (existing) ms timeout
WebRTCSampler.stunServer      — (NEW) STUN server as "host:port" (e.g., "stun.l.google.com:19302")
WebRTCSampler.dtlsEnabled     — (NEW) boolean, attempt DTLS handshake to remote (default false)
WebRTCSampler.mode            — (NEW) "signaling" | "ice" | "full" (default "signaling")
```

**Mode behavior**:
- `signaling`: Current behavior. SDP POST only.
- `ice`: SDP POST + STUN Binding Request to `stunServer`. Measures signaling RTT + ICE gathering time.
- `full`: SDP POST + STUN + DTLS handshake (if `dtlsEnabled=true`). Measures all three phases.

### 3.3 Mock Server Design

**Location**: `test-servers/webrtc-mock/`

Two components:

#### 3.3.1 HTTP Signaling Server (`signaling.js`)

```javascript
// Port: process.env.PORT || 8086
// POST /offer — accepts SDP offer, returns SDP answer
// GET /health — health check
//
// SDP answer is a deterministic transformation:
//   - Copies media lines from offer
//   - Replaces o= line with server identity
//   - Sets a=setup:active (server is DTLS client)
//   - Adds ICE candidates from STUN mock
```

Responds to `POST /offer` with `Content-Type: application/sdp`. Returns a valid SDP answer that mirrors the offer's media section with server-side ICE credentials.

#### 3.3.2 STUN Server (`stun.js`)

```javascript
// Port: process.env.STUN_PORT || 3478 (UDP)
// Handles STUN Binding Request (0x0001)
// Returns Binding Response (0x0101) with:
//   - XOR-MAPPED-ADDRESS: reflects sender's IP:port
//   - Deterministic transaction ID echo
```

Implementation: `dgram.createSocket('udp4')`. Parse 20-byte STUN header, extract transaction ID, build Binding Response with XOR-MAPPED-ADDRESS attribute. ~80 lines.

### 3.4 JMX Schema

```xml
<GenericSampler guiclass="GenericSamplerGui" testclass="WebRTCSampler"
                testname="WebRTC Signaling + ICE">
  <stringProp name="WebRTCSampler.signalingUrl">http://${WEBRTC_HOST}:8086/offer</stringProp>
  <stringProp name="WebRTCSampler.offerSdp">generate</stringProp>
  <intProp name="WebRTCSampler.connectTimeout">10000</intProp>
  <stringProp name="WebRTCSampler.stunServer">${WEBRTC_HOST}:3478</stringProp>
  <boolProp name="WebRTCSampler.dtlsEnabled">false</boolProp>
  <stringProp name="WebRTCSampler.mode">ice</stringProp>
</GenericSampler>
```

### 3.5 Test Scenarios

| Scenario | Type | Description |
|----------|------|-------------|
| Signaling happy path | Smoke | POST SDP offer, get SDP answer, verify 200 |
| Signaling with custom SDP | Functional | Custom SDP with audio+video m= lines |
| ICE gathering | Functional | STUN Binding Request, verify reflexive candidate in response |
| STUN timeout | Error | Non-responsive STUN server, verify timeout handling |
| Load: signaling throughput | Performance | 50 VUs signaling-only, measure p95 |

### 3.6 Metrics

| Metric | Source | Description |
|--------|--------|-------------|
| Signaling RTT | `totalTimeMs` (existing) | SDP offer-to-answer round trip |
| ICE gathering time | New: time for STUN Binding Response | Candidate gathering duration |
| DTLS handshake time | New: `SSLEngine` handshake completion delta | Secure transport setup |
| Total setup time | Sum of above | End-to-end connection establishment |

### 3.7 Priority: **P2** — next sprint

**Justification**: The signaling-only mode already works. ICE/STUN expansion is valuable for WebRTC load testing realism but requires new Java code (DatagramSocket STUN client, DTLS SSLEngine integration). The STUN mock is simple but the executor changes are medium complexity.

### 3.8 Effort: **M** (Medium)

STUN client in Java: ~150 lines. DTLS handshake: ~100 lines. Mock servers: ~150 lines total. Executor refactor for modes: ~100 lines.

---

## 4. DASH (MPEG-DASH) — Mock Server + Tests

### 4.1 Status

- **DASHSamplerExecutor**: EXISTS — full implementation. Fetches MPD manifest, parses XML with `javax.xml.parsers`, selects representation by quality/bandwidth, constructs segment URLs from `<SegmentTemplate>`, downloads init + media segments. Uses `java.net.http.HttpClient`.
- **DASHSamplerExecutorTest**: EXISTS
- **Mock server**: MISSING — no `test-servers/dash-mock/`
- **JMX plan**: MISSING

### 4.2 Implementation Approach

Create a DASH mock server that serves:
1. MPD manifest (XML)
2. Initialization segments (.mp4 header)
3. Media segments (.m4s)

The server is structurally similar to `test-servers/hls-mock/server.js` but uses DASH's MPD XML format instead of M3U8 playlists.

### 4.3 Mock Server Design

**Location**: `test-servers/dash-mock/`

**Language**: Node.js (plain `http` module, no dependencies)
**Port**: `process.env.PORT || 8085`

**Endpoints**:

| Method | Path | Content-Type | Description |
|--------|------|-------------|-------------|
| GET | `/health` | `application/json` | Health check |
| GET | `/live/manifest.mpd` | `application/dash+xml` | MPD manifest |
| GET | `/live/:repId/init.mp4` | `video/mp4` | Init segment (per representation) |
| GET | `/live/:repId/segment-:n.m4s` | `video/iso.segment` | Media segment |

**MPD manifest structure**:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<MPD xmlns="urn:mpeg:dash:schema:mpd:2011"
     type="dynamic"
     minimumUpdatePeriod="PT2S"
     availabilityStartTime="2026-01-01T00:00:00Z"
     publishTime="2026-03-26T00:00:00Z"
     minBufferTime="PT2S">
  <Period id="1" start="PT0S">
    <AdaptationSet mimeType="video/mp4" segmentAlignment="true">
      <SegmentTemplate
          media="$RepresentationID$/segment-$Number$.m4s"
          initialization="$RepresentationID$/init.mp4"
          startNumber="1"
          timescale="1000"
          duration="2000"/>
      <Representation id="720p" bandwidth="2800000" width="1280" height="720" codecs="avc1.4d401f"/>
      <Representation id="480p" bandwidth="1400000" width="854"  height="480" codecs="avc1.4d401e"/>
      <Representation id="360p" bandwidth="800000"  width="640"  height="360" codecs="avc1.4d4015"/>
    </AdaptationSet>
  </Period>
</MPD>
```

**Segment generation**: Same deterministic pattern as HLS mock — `crypto.createHash('sha256').update(repId + '-' + segNum)` to produce pseudo-random bytes of configurable size (`SEGMENT_SIZE` env var, default 1024).

**Comparison with HLS mock**:

| Aspect | HLS (`test-servers/hls-mock/`) | DASH (`test-servers/dash-mock/`) |
|--------|------|------|
| Manifest format | M3U8 (text, line-based) | MPD (XML) |
| Manifest URL | `/live/master.m3u8` | `/live/manifest.mpd` |
| Segment naming | `segment-N.ts` (.ts MPEG-TS) | `segment-N.m4s` (.m4s fragmented MP4) |
| Init segment | Not needed (TS is self-contained) | Required (`init.mp4` per representation) |
| Quality playlist | Per-quality `.m3u8` sub-playlist | All in single MPD with `<Representation>` elements |
| Live window | `EXT-X-MEDIA-SEQUENCE` sliding window | `availabilityStartTime` + `duration` calculation |
| Content-Type | `application/vnd.apple.mpegurl` | `application/dash+xml` |

### 4.4 JMX Schema

```xml
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="DASH Smoke Test">
      <boolProp name="TestPlan.serialize_threadgroups">false</boolProp>
    </TestPlan>
    <hashTree>
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup"
                   testname="DASH Viewers">
        <intProp name="ThreadGroup.num_threads">5</intProp>
        <elementProp name="ThreadGroup.main_controller" elementType="LoopController">
          <intProp name="LoopController.loops">3</intProp>
        </elementProp>
      </ThreadGroup>
      <hashTree>

        <!-- Best quality -->
        <GenericSampler guiclass="GenericSamplerGui" testclass="DASHSampler"
                        testname="DASH Best Quality">
          <stringProp name="DASHSampler.url">http://${DASH_HOST}:${DASH_PORT}/live/manifest.mpd</stringProp>
          <stringProp name="DASHSampler.quality">best</stringProp>
          <intProp name="DASHSampler.segmentCount">3</intProp>
          <intProp name="DASHSampler.connectTimeout">5000</intProp>
        </GenericSampler>
        <hashTree>
          <ResponseAssertion guiclass="AssertionGui" testclass="ResponseAssertion"
                             testname="Assert segments downloaded">
            <stringProp name="Assertion.test_field">Assertion.response_data</stringProp>
            <stringProp name="Assertion.test_type">2</stringProp>
            <collectionProp name="Asserion.test_strings">
              <stringProp>segments: 3/3 downloaded</stringProp>
            </collectionProp>
          </ResponseAssertion>
        </hashTree>

        <!-- Specific quality: 480p -->
        <GenericSampler guiclass="GenericSamplerGui" testclass="DASHSampler"
                        testname="DASH 480p">
          <stringProp name="DASHSampler.url">http://${DASH_HOST}:${DASH_PORT}/live/manifest.mpd</stringProp>
          <stringProp name="DASHSampler.quality">480p</stringProp>
          <intProp name="DASHSampler.segmentCount">5</intProp>
          <intProp name="DASHSampler.connectTimeout">5000</intProp>
        </GenericSampler>
        <hashTree/>

        <!-- Worst quality (bandwidth test) -->
        <GenericSampler guiclass="GenericSamplerGui" testclass="DASHSampler"
                        testname="DASH Worst Quality">
          <stringProp name="DASHSampler.url">http://${DASH_HOST}:${DASH_PORT}/live/manifest.mpd</stringProp>
          <stringProp name="DASHSampler.quality">worst</stringProp>
          <intProp name="DASHSampler.segmentCount">3</intProp>
          <intProp name="DASHSampler.connectTimeout">5000</intProp>
        </GenericSampler>
        <hashTree/>

      </hashTree>
    </hashTree>
  </hashTree>
</jmeterTestPlan>
```

### 4.5 Test Scenarios

| Scenario | Type | Description |
|----------|------|-------------|
| Fetch MPD + best quality | Smoke | Parse MPD, select highest bandwidth, download 3 segments |
| Specific quality (480p) | Functional | Verify correct representation selected by height |
| Worst quality | Functional | Verify lowest bandwidth selected |
| Init segment download | Functional | Verify init.mp4 is fetched before media segments |
| Invalid MPD | Error | Malformed XML, verify `setFailureMessage` |
| 404 MPD | Error | Non-existent manifest URL, verify HTTP 404 handling |
| ABR switching simulation | Performance | Multiple DASHSampler nodes with different qualities in sequence |
| Load: concurrent viewers | Performance | 50 VUs, 5 segments each, measure throughput |

### 4.6 Metrics

| Metric | Source | Description |
|--------|--------|-------------|
| `connectTimeMs` | First HTTP GET timing | TCP connection time |
| `latencyMs` | MPD parsing completion | Time to parse and select representation |
| `totalTimeMs` | All segments downloaded | End-to-end viewing session |
| `statusCode` | 200/206/error | 200 = all segments OK, 206 = partial, 0 = all failed |
| `responseBody` | Summary string | Contains quality, segment count, total bytes |
| Segment download rate | `totalBytes / totalTimeMs` | Effective throughput (derived metric) |

### 4.7 Priority: **P1** — do now

**Justification**: DASHSamplerExecutor is fully implemented with MPD parsing and segment downloading. Adding a mock server mirrors what was already done for HLS. DASH + HLS together cover >95% of streaming load testing needs.

### 4.8 Effort: **S** (Small)

Server is ~120 lines (structurally identical to HLS mock with XML instead of M3U8). JMX plan is boilerplate.

---

## 5. QUIC/HTTP3 — Feasibility Analysis

### 5.1 Status

- **Sampler executor**: NOT IMPLEMENTED
- **Mock server**: NOT IMPLEMENTED
- **JDK support**: JDK 21 has **no native QUIC** support. There is no `java.net.quic` package.

### 5.2 Implementation Approach — Options Analysis

| Option | Library | Maturity | License | Pure Java? | Notes |
|--------|---------|----------|---------|------------|-------|
| **kwik** | `tech.kwik:kwik-core` | Active (v0.8+) | LGPL-3.0 | Yes | Pure Java QUIC + HTTP/3. Most aligned with Constitution Principle I. |
| **Netty QUIC** | `io.netty.incubator:netty-incubator-codec-quic` | Incubator | Apache-2.0 | No (JNI to BoringSSL) | Mature but uses native crypto lib. Violates pure-JDK principle. |
| **Quiche JNI** | Cloudflare quiche + JNI wrapper | Experimental | BSD-2 | No (Rust FFI) | Best performance but hardest to integrate and maintain. |
| **JDK future** | OpenJDK Project Panama + Loom | JEP draft only | N/A | Eventually | JDK 24+ may get QUIC. Not available today. |

### 5.3 kwik Analysis (Recommended if IMPLEMENT)

```java
// Hypothetical QUIC3SamplerExecutor using kwik
import tech.kwik.core.QuicClientConnection;
import tech.kwik.http3.Http3Client;

// Connection:
QuicClientConnection.Builder builder = QuicClientConnection.newBuilder();
builder.uri(URI.create("https://target:443"));
builder.connectTimeout(Duration.ofSeconds(5));
QuicClientConnection conn = builder.build();
conn.connect();

// HTTP/3 request:
Http3Client http3 = new Http3Client(conn);
HttpResponse response = http3.send(request);
```

**kwik limitations**:
- LGPL-3.0 license — requires careful dependency isolation (separate module, not bundled in engine-service core)
- No QPACK dynamic table (static encoding only) — adequate for load testing
- Performance is ~60% of native QUIC implementations — acceptable for client-side load generation
- Does not support 0-RTT resumption yet — key metric cannot be measured

### 5.4 HTTP/3-Unique Metrics

| Metric | Description | Why it matters for load testing |
|--------|-------------|-------------------------------|
| 0-RTT handshake time | QUIC can send data in the first flight | Measures real-world latency savings over TCP+TLS |
| Stream multiplexing efficiency | N streams over 1 connection, no head-of-line blocking | Differentiator vs HTTP/2 |
| Connection migration time | Client IP change without re-handshake | Mobile/roaming scenarios |
| Packet loss impact | Per-stream loss handling vs TCP's connection-wide | QUIC's key advantage |
| Handshake time (1-RTT) | QUIC handshake = TLS 1.3 in transport layer | Baseline comparison with TCP+TLS |

### 5.5 Mock Server Feasibility

Node.js HTTP/3 support is experimental (`--experimental-http3` flag, removed in Node 22). Options:

- **quiche** (Cloudflare): C library with Python bindings. Could write a Python mock server. Adds Python as a dependency.
- **Caddy**: Go-based server with built-in HTTP/3 support. Could run as a binary.
- **aioquic** (Python): Pure Python QUIC implementation. Simplest mock server option.

**Recommendation**: Use aioquic for mock server if implementing QUIC support:

```python
# test-servers/quic-mock/server.py
from aioquic.asyncio import serve
from aioquic.h3.connection import H3Connection
# ~100 lines for basic HTTP/3 echo server
```

### 5.6 Verdict: **DEFER**

**Reasons**:
1. **No JDK-native QUIC**: Violates Constitution Principle I. kwik exists but is LGPL and adds the first external dependency to engine-service.
2. **Mock server complexity**: Cannot use Node.js. Requires Python or Go mock, breaking the existing test-server pattern.
3. **Low demand**: HTTP/3 adoption in load testing is nascent. Most CDNs fall back to HTTP/2 gracefully.
4. **Incomplete kwik**: Cannot measure 0-RTT (the most interesting HTTP/3 metric) with kwik today.

**Revisit when**: JDK 24+ ships native QUIC (expected 2027), OR kwik reaches 1.0 with 0-RTT support.

### 5.7 Priority: **P3** — backlog

### 5.8 Effort: **L** (Large)

New executor + kwik integration + Python/Go mock server + new JMX sampler type. Cross-language test infrastructure.

---

## 6. FTP — Sampler + Mock Server

### 6.1 Status

- **Sampler executor**: NOT IMPLEMENTED
- **Mock server**: NOT IMPLEMENTED
- **JDK support**: `java.net.URL` supports `ftp://` protocol natively. For more control, raw socket FTP commands via `java.net.Socket` are straightforward (FTP is a text-line protocol).

### 6.2 Implementation Approach

**Recommended**: Use raw socket FTP commands via `java.net.Socket` rather than `java.net.URL` for FTP. Reasons:
- `java.net.URL` FTP support is limited (no PASV mode control, no LIST parsing)
- Raw sockets give timing granularity (connect time vs login time vs transfer time)
- Matches the MQTT pattern (raw TCP protocol implementation)

**Class**: `FTPSamplerExecutor.java`

**Properties**:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `FTPSampler.host` | String | (required) | FTP server hostname |
| `FTPSampler.port` | int | 21 | FTP control port |
| `FTPSampler.username` | String | `anonymous` | Login username |
| `FTPSampler.password` | String | `jmeternext@` | Login password |
| `FTPSampler.path` | String | `/` | Remote file/directory path |
| `FTPSampler.action` | String | `list` | `list`, `download`, `upload` |
| `FTPSampler.localContent` | String | (empty) | Content to upload (for action=upload) |
| `FTPSampler.passive` | boolean | `true` | Use PASV mode |
| `FTPSampler.timeout` | int | 10000 | Timeout in ms |

**Executor design** (pseudocode):

```java
public final class FTPSamplerExecutor {
    // FTP reply codes
    static final int READY = 220;
    static final int LOGIN_OK = 230;
    static final int NEED_PASSWORD = 331;
    static final int PASV_OK = 227;
    static final int TRANSFER_START = 150;
    static final int TRANSFER_COMPLETE = 226;

    public static void execute(PlanNode node, SampleResult result,
                                Map<String, String> variables) {
        // 1. TCP connect to host:port
        // 2. Read 220 welcome banner
        // 3. Send USER <username>, read 331
        // 4. Send PASS <password>, read 230
        // 5. Send PASV, read 227, parse data port
        // 6. Based on action:
        //    - list:     Send LIST <path>, read data connection, parse filenames
        //    - download: Send RETR <path>, read data connection, capture bytes
        //    - upload:   Send STOR <path>, write data to data connection
        // 7. Read 226 transfer complete
        // 8. Send QUIT
    }

    // FTP command helper: send line + read response line
    static String sendCommand(DataOutputStream out, DataInputStream in, String cmd);

    // Parse PASV response: "227 Entering Passive Mode (h1,h2,h3,h4,p1,p2)"
    static InetSocketAddress parsePasvResponse(String response);
}
```

**Key FTP protocol details**:
- Control connection: text lines terminated by `\r\n`
- Data connection: separate TCP socket (PASV mode: server provides port; ACTIVE mode: client provides port)
- Response format: `NNN <text>\r\n` where NNN is a 3-digit reply code
- Multi-line responses: `NNN-<text>\r\n` ... `NNN <text>\r\n`

### 6.3 Mock Server Design

**Location**: `test-servers/ftp-mock/`

**Language**: Node.js
**Package**: `ftp-srv` (npm)
**Port**: `process.env.PORT || 2121` (non-privileged alternative to 21)

```javascript
const FtpSrv = require('ftp-srv');
const path = require('path');
const fs = require('fs');

const PORT = process.env.PORT || 2121;
const PASV_MIN = 30000;
const PASV_MAX = 30100;

// Create a temp directory for FTP root
const FTP_ROOT = path.join(__dirname, 'ftp-root');
if (!fs.existsSync(FTP_ROOT)) fs.mkdirSync(FTP_ROOT, { recursive: true });

// Seed test files
fs.writeFileSync(path.join(FTP_ROOT, 'test.txt'), 'Hello from jMeter Next FTP mock\n');
fs.writeFileSync(path.join(FTP_ROOT, 'data.csv'), 'id,name,value\n1,alpha,100\n2,beta,200\n');
fs.mkdirSync(path.join(FTP_ROOT, 'uploads'), { recursive: true });

const server = new FtpSrv({
  url: `ftp://0.0.0.0:${PORT}`,
  pasv_url: `0.0.0.0`,
  pasv_min: PASV_MIN,
  pasv_max: PASV_MAX,
  anonymous: true,
});

server.on('login', ({ connection, username, password }, resolve, reject) => {
  // Accept any credentials for testing
  console.log(`FTP login: ${username}`);
  resolve({ root: FTP_ROOT });
});

server.listen().then(() => {
  console.log(`FTP mock server listening on port ${PORT}`);
  console.log(`PASV range: ${PASV_MIN}-${PASV_MAX}`);
  console.log(`FTP root: ${FTP_ROOT}`);
});
```

**`package.json`**:
```json
{
  "name": "ftp-mock",
  "version": "1.0.0",
  "private": true,
  "scripts": { "start": "node server.js" },
  "dependencies": { "ftp-srv": "^4.7.0" }
}
```

### 6.4 JMX Schema

```xml
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="FTP Smoke Test">
      <boolProp name="TestPlan.serialize_threadgroups">false</boolProp>
    </TestPlan>
    <hashTree>
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup"
                   testname="FTP Operations">
        <intProp name="ThreadGroup.num_threads">2</intProp>
        <elementProp name="ThreadGroup.main_controller" elementType="LoopController">
          <intProp name="LoopController.loops">5</intProp>
        </elementProp>
      </ThreadGroup>
      <hashTree>

        <!-- LIST directory -->
        <GenericSampler guiclass="GenericSamplerGui" testclass="FTPSampler"
                        testname="FTP List Root">
          <stringProp name="FTPSampler.host">${FTP_HOST}</stringProp>
          <intProp name="FTPSampler.port">${FTP_PORT}</intProp>
          <stringProp name="FTPSampler.username">anonymous</stringProp>
          <stringProp name="FTPSampler.password">jmeternext@</stringProp>
          <stringProp name="FTPSampler.path">/</stringProp>
          <stringProp name="FTPSampler.action">list</stringProp>
          <boolProp name="FTPSampler.passive">true</boolProp>
          <intProp name="FTPSampler.timeout">10000</intProp>
        </GenericSampler>
        <hashTree>
          <ResponseAssertion guiclass="AssertionGui" testclass="ResponseAssertion"
                             testname="Assert test.txt in listing">
            <stringProp name="Assertion.test_field">Assertion.response_data</stringProp>
            <stringProp name="Assertion.test_type">2</stringProp>
            <collectionProp name="Asserion.test_strings">
              <stringProp>test.txt</stringProp>
            </collectionProp>
          </ResponseAssertion>
        </hashTree>

        <!-- RETR download -->
        <GenericSampler guiclass="GenericSamplerGui" testclass="FTPSampler"
                        testname="FTP Download test.txt">
          <stringProp name="FTPSampler.host">${FTP_HOST}</stringProp>
          <intProp name="FTPSampler.port">${FTP_PORT}</intProp>
          <stringProp name="FTPSampler.username">anonymous</stringProp>
          <stringProp name="FTPSampler.password">jmeternext@</stringProp>
          <stringProp name="FTPSampler.path">/test.txt</stringProp>
          <stringProp name="FTPSampler.action">download</stringProp>
          <boolProp name="FTPSampler.passive">true</boolProp>
          <intProp name="FTPSampler.timeout">10000</intProp>
        </GenericSampler>
        <hashTree>
          <ResponseAssertion guiclass="AssertionGui" testclass="ResponseAssertion"
                             testname="Assert file content">
            <stringProp name="Assertion.test_field">Assertion.response_data</stringProp>
            <stringProp name="Assertion.test_type">2</stringProp>
            <collectionProp name="Asserion.test_strings">
              <stringProp>Hello from jMeter Next</stringProp>
            </collectionProp>
          </ResponseAssertion>
        </hashTree>

        <!-- STOR upload -->
        <GenericSampler guiclass="GenericSamplerGui" testclass="FTPSampler"
                        testname="FTP Upload result.txt">
          <stringProp name="FTPSampler.host">${FTP_HOST}</stringProp>
          <intProp name="FTPSampler.port">${FTP_PORT}</intProp>
          <stringProp name="FTPSampler.username">anonymous</stringProp>
          <stringProp name="FTPSampler.password">jmeternext@</stringProp>
          <stringProp name="FTPSampler.path">/uploads/result.txt</stringProp>
          <stringProp name="FTPSampler.action">upload</stringProp>
          <stringProp name="FTPSampler.localContent">test result: PASS</stringProp>
          <boolProp name="FTPSampler.passive">true</boolProp>
          <intProp name="FTPSampler.timeout">10000</intProp>
        </GenericSampler>
        <hashTree/>

      </hashTree>
    </hashTree>
  </hashTree>
</jmeterTestPlan>
```

### 6.5 Test Scenarios

| Scenario | Type | Description |
|----------|------|-------------|
| LIST directory | Smoke | Connect, login, LIST /, verify file listing |
| RETR download | Smoke | Download test.txt, verify content |
| STOR upload | Smoke | Upload file, verify 226 transfer complete |
| Anonymous login | Functional | Login with anonymous, verify acceptance |
| Bad credentials | Error | Wrong password, verify 530 Login incorrect |
| Connection refused | Error | Invalid host, verify IOException |
| Download non-existent | Error | RETR missing file, verify 550 response |
| PASV mode | Functional | Verify passive data connection works |
| Large file transfer | Performance | Upload/download 1MB file, measure transfer rate |

### 6.6 Metrics

| Metric | Source | Description |
|--------|--------|-------------|
| `connectTimeMs` | TCP connect delta | Control connection establishment |
| `latencyMs` | Time from CONNECT to 230 LOGIN_OK | Authentication handshake time |
| `totalTimeMs` | Full operation time | LOGIN + command + data transfer + QUIT |
| `statusCode` | FTP reply code | 226 = transfer complete, 530 = auth fail, etc. |
| `responseBody` | LIST output or downloaded content | Available for assertions/extractors |
| Transfer rate (derived) | `bytes / transferTimeMs` | Effective throughput |
| Bytes transferred (derived) | Data connection byte count | File size or listing size |

### 6.7 Priority: **P2** — next sprint

**Justification**: FTP is a legacy protocol but still widely used in enterprise environments (financial file transfers, batch processing). The implementation is straightforward — FTP is a simple text-line protocol over TCP, similar in complexity to the existing MQTT executor. However, modern load testing rarely targets FTP, making it lower priority than gRPC/MQTT/DASH.

### 6.8 Effort: **M** (Medium)

New Java executor (~250 lines for FTP command handling + PASV parsing). Mock server ~40 lines (ftp-srv does the heavy lifting). NodeInterpreter dispatch case. Unit tests.

---

## 7. LDAP — Sampler + Mock Server

### 7.1 Status

- **Sampler executor**: NOT IMPLEMENTED
- **Mock server**: NOT IMPLEMENTED
- **JDK support**: `javax.naming.ldap.LdapContext` and `javax.naming.directory.DirContext` are part of the JDK (JNDI). Full LDAP client support without any external libraries.

### 7.2 Implementation Approach

Use `javax.naming.ldap.InitialLdapContext` for LDAP operations. This is the standard JDK LDAP client, fully compatible with Constitution Principle I.

**Class**: `LDAPSamplerExecutor.java`

**Properties**:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `LDAPSampler.host` | String | (required) | LDAP server hostname |
| `LDAPSampler.port` | int | 389 | LDAP port (636 for LDAPS) |
| `LDAPSampler.baseDN` | String | (required) | Base distinguished name for searches |
| `LDAPSampler.bindDN` | String | (empty) | Bind DN for authentication (empty = anonymous bind) |
| `LDAPSampler.bindPassword` | String | (empty) | Bind password |
| `LDAPSampler.action` | String | `search` | `bind`, `search`, `add`, `modify`, `delete` |
| `LDAPSampler.searchFilter` | String | `(objectClass=*)` | LDAP search filter |
| `LDAPSampler.searchScope` | String | `subtree` | `base`, `onelevel`, `subtree` |
| `LDAPSampler.attributes` | String | (empty) | Comma-separated list of attributes to return |
| `LDAPSampler.useTls` | boolean | false | Use LDAPS (TLS) |
| `LDAPSampler.timeout` | int | 10000 | Timeout in ms |
| `LDAPSampler.entryDN` | String | (empty) | DN for add/modify/delete operations |
| `LDAPSampler.entryAttributes` | String | (empty) | JSON string of attributes for add/modify |

**Executor design** (pseudocode):

```java
public final class LDAPSamplerExecutor {

    public static void execute(PlanNode node, SampleResult result,
                                Map<String, String> variables) {
        // 1. Build JNDI environment
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, "ldap://" + host + ":" + port);
        env.put(Context.SECURITY_AUTHENTICATION, bindDN.isEmpty() ? "none" : "simple");
        env.put(Context.SECURITY_PRINCIPAL, bindDN);
        env.put(Context.SECURITY_CREDENTIALS, bindPassword);
        env.put("com.sun.jndi.ldap.connect.timeout", String.valueOf(timeout));
        env.put("com.sun.jndi.ldap.read.timeout", String.valueOf(timeout));

        // 2. Create context (this performs the BIND)
        long start = System.currentTimeMillis();
        InitialLdapContext ctx = new InitialLdapContext(env, null);
        result.setConnectTimeMs(System.currentTimeMillis() - start);

        // 3. Execute action
        switch (action) {
            case "bind":
                // Bind-only: connection establishment was the test
                result.setStatusCode(0); // LDAP success
                break;

            case "search":
                SearchControls controls = new SearchControls();
                controls.setSearchScope(parseScope(searchScope));
                controls.setTimeLimit(timeout);
                if (!attributes.isEmpty()) {
                    controls.setReturningAttributes(attributes.split(","));
                }
                NamingEnumeration<SearchResult> results = ctx.search(baseDN, searchFilter, controls);
                int count = 0;
                StringBuilder sb = new StringBuilder();
                while (results.hasMore()) {
                    SearchResult sr = results.next();
                    sb.append(sr.getNameInNamespace()).append("\n");
                    count++;
                }
                result.setResponseBody(sb.toString());
                result.setStatusCode(count); // number of results as status
                break;

            case "add":
                Attributes attrs = parseAttributes(entryAttributes);
                ctx.createSubcontext(entryDN, attrs);
                result.setStatusCode(0);
                break;

            case "modify":
                ModificationItem[] mods = parseModifications(entryAttributes);
                ctx.modifyAttributes(entryDN, mods);
                result.setStatusCode(0);
                break;

            case "delete":
                ctx.destroySubcontext(entryDN);
                result.setStatusCode(0);
                break;
        }

        // 4. Close context
        ctx.close();
        result.setTotalTimeMs(System.currentTimeMillis() - start);
    }
}
```

### 7.3 Mock Server Design

**Location**: `test-servers/ldap-mock/`

**Language**: Node.js
**Package**: `ldapjs` (npm)
**Port**: `process.env.PORT || 1389` (non-privileged alternative to 389)

```javascript
const ldap = require('ldapjs');

const PORT = process.env.PORT || 1389;
const BASE_DN = 'dc=jmeternext,dc=test';

// In-memory directory tree
const directory = {
  'dc=jmeternext,dc=test': {
    objectClass: ['top', 'domain'],
    dc: 'jmeternext',
  },
  'ou=users,dc=jmeternext,dc=test': {
    objectClass: ['top', 'organizationalUnit'],
    ou: 'users',
  },
  'cn=admin,ou=users,dc=jmeternext,dc=test': {
    objectClass: ['top', 'person', 'organizationalPerson'],
    cn: 'admin',
    sn: 'Administrator',
    userPassword: 'admin123',
    mail: 'admin@jmeternext.test',
  },
  'cn=alice,ou=users,dc=jmeternext,dc=test': {
    objectClass: ['top', 'person', 'organizationalPerson'],
    cn: 'alice',
    sn: 'Smith',
    mail: 'alice@jmeternext.test',
    telephoneNumber: '+1-555-0101',
  },
  'cn=bob,ou=users,dc=jmeternext,dc=test': {
    objectClass: ['top', 'person', 'organizationalPerson'],
    cn: 'bob',
    sn: 'Jones',
    mail: 'bob@jmeternext.test',
    telephoneNumber: '+1-555-0102',
  },
  'ou=groups,dc=jmeternext,dc=test': {
    objectClass: ['top', 'organizationalUnit'],
    ou: 'groups',
  },
  'cn=developers,ou=groups,dc=jmeternext,dc=test': {
    objectClass: ['top', 'groupOfNames'],
    cn: 'developers',
    member: [
      'cn=alice,ou=users,dc=jmeternext,dc=test',
      'cn=bob,ou=users,dc=jmeternext,dc=test',
    ],
  },
};

const server = ldap.createServer();

// BIND
server.bind(BASE_DN, (req, res, next) => {
  const dn = req.dn.toString();
  const entry = directory[dn];
  if (!entry) return next(new ldap.NoSuchObjectError(dn));
  if (entry.userPassword && req.credentials !== entry.userPassword) {
    return next(new ldap.InvalidCredentialsError());
  }
  console.log(`BIND: ${dn}`);
  res.end();
  return next();
});

// SEARCH
server.search(BASE_DN, (req, res, next) => {
  console.log(`SEARCH base=${req.dn} filter=${req.filter} scope=${req.scope}`);
  for (const [dn, attrs] of Object.entries(directory)) {
    if (req.filter.matches(attrs)) {
      res.send({ dn, attributes: attrs });
    }
  }
  res.end();
  return next();
});

// ADD
server.add(BASE_DN, (req, res, next) => {
  const dn = req.dn.toString();
  if (directory[dn]) return next(new ldap.EntryAlreadyExistsError(dn));
  directory[dn] = req.toObject().attributes || {};
  console.log(`ADD: ${dn}`);
  res.end();
  return next();
});

// MODIFY
server.modify(BASE_DN, (req, res, next) => {
  const dn = req.dn.toString();
  if (!directory[dn]) return next(new ldap.NoSuchObjectError(dn));
  // Apply modifications
  for (const change of req.changes) {
    const attr = change.modification.type;
    const vals = change.modification.vals;
    if (change.operation === 'replace') directory[dn][attr] = vals.length === 1 ? vals[0] : vals;
    if (change.operation === 'add') directory[dn][attr] = vals.length === 1 ? vals[0] : vals;
    if (change.operation === 'delete') delete directory[dn][attr];
  }
  console.log(`MODIFY: ${dn}`);
  res.end();
  return next();
});

// DELETE
server.del(BASE_DN, (req, res, next) => {
  const dn = req.dn.toString();
  if (!directory[dn]) return next(new ldap.NoSuchObjectError(dn));
  delete directory[dn];
  console.log(`DELETE: ${dn}`);
  res.end();
  return next();
});

server.listen(PORT, () => {
  console.log(`LDAP mock server listening on port ${PORT}`);
  console.log(`Base DN: ${BASE_DN}`);
  console.log(`Entries: ${Object.keys(directory).length}`);
});
```

**`package.json`**:
```json
{
  "name": "ldap-mock",
  "version": "1.0.0",
  "private": true,
  "scripts": { "start": "node server.js" },
  "dependencies": { "ldapjs": "^3.0.0" }
}
```

### 7.4 JMX Schema

```xml
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="LDAP Smoke Test">
      <boolProp name="TestPlan.serialize_threadgroups">false</boolProp>
    </TestPlan>
    <hashTree>
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup"
                   testname="LDAP Operations">
        <intProp name="ThreadGroup.num_threads">2</intProp>
        <elementProp name="ThreadGroup.main_controller" elementType="LoopController">
          <intProp name="LoopController.loops">5</intProp>
        </elementProp>
      </ThreadGroup>
      <hashTree>

        <!-- Bind test -->
        <GenericSampler guiclass="GenericSamplerGui" testclass="LDAPSampler"
                        testname="LDAP Bind">
          <stringProp name="LDAPSampler.host">${LDAP_HOST}</stringProp>
          <intProp name="LDAPSampler.port">${LDAP_PORT}</intProp>
          <stringProp name="LDAPSampler.baseDN">dc=jmeternext,dc=test</stringProp>
          <stringProp name="LDAPSampler.bindDN">cn=admin,ou=users,dc=jmeternext,dc=test</stringProp>
          <stringProp name="LDAPSampler.bindPassword">admin123</stringProp>
          <stringProp name="LDAPSampler.action">bind</stringProp>
          <intProp name="LDAPSampler.timeout">10000</intProp>
        </GenericSampler>
        <hashTree/>

        <!-- Search all users -->
        <GenericSampler guiclass="GenericSamplerGui" testclass="LDAPSampler"
                        testname="LDAP Search Users">
          <stringProp name="LDAPSampler.host">${LDAP_HOST}</stringProp>
          <intProp name="LDAPSampler.port">${LDAP_PORT}</intProp>
          <stringProp name="LDAPSampler.baseDN">ou=users,dc=jmeternext,dc=test</stringProp>
          <stringProp name="LDAPSampler.bindDN"></stringProp>
          <stringProp name="LDAPSampler.bindPassword"></stringProp>
          <stringProp name="LDAPSampler.action">search</stringProp>
          <stringProp name="LDAPSampler.searchFilter">(objectClass=person)</stringProp>
          <stringProp name="LDAPSampler.searchScope">subtree</stringProp>
          <stringProp name="LDAPSampler.attributes">cn,mail,sn</stringProp>
          <intProp name="LDAPSampler.timeout">10000</intProp>
        </GenericSampler>
        <hashTree>
          <ResponseAssertion guiclass="AssertionGui" testclass="ResponseAssertion"
                             testname="Assert alice found">
            <stringProp name="Assertion.test_field">Assertion.response_data</stringProp>
            <stringProp name="Assertion.test_type">2</stringProp>
            <collectionProp name="Asserion.test_strings">
              <stringProp>cn=alice</stringProp>
            </collectionProp>
          </ResponseAssertion>
        </hashTree>

        <!-- Search by filter -->
        <GenericSampler guiclass="GenericSamplerGui" testclass="LDAPSampler"
                        testname="LDAP Search by Mail">
          <stringProp name="LDAPSampler.host">${LDAP_HOST}</stringProp>
          <intProp name="LDAPSampler.port">${LDAP_PORT}</intProp>
          <stringProp name="LDAPSampler.baseDN">dc=jmeternext,dc=test</stringProp>
          <stringProp name="LDAPSampler.action">search</stringProp>
          <stringProp name="LDAPSampler.searchFilter">(mail=bob@jmeternext.test)</stringProp>
          <stringProp name="LDAPSampler.searchScope">subtree</stringProp>
          <intProp name="LDAPSampler.timeout">10000</intProp>
        </GenericSampler>
        <hashTree/>

        <!-- Add entry -->
        <GenericSampler guiclass="GenericSamplerGui" testclass="LDAPSampler"
                        testname="LDAP Add User">
          <stringProp name="LDAPSampler.host">${LDAP_HOST}</stringProp>
          <intProp name="LDAPSampler.port">${LDAP_PORT}</intProp>
          <stringProp name="LDAPSampler.baseDN">dc=jmeternext,dc=test</stringProp>
          <stringProp name="LDAPSampler.bindDN">cn=admin,ou=users,dc=jmeternext,dc=test</stringProp>
          <stringProp name="LDAPSampler.bindPassword">admin123</stringProp>
          <stringProp name="LDAPSampler.action">add</stringProp>
          <stringProp name="LDAPSampler.entryDN">cn=charlie,ou=users,dc=jmeternext,dc=test</stringProp>
          <stringProp name="LDAPSampler.entryAttributes">{"objectClass":["top","person"],"cn":"charlie","sn":"Wilson","mail":"charlie@jmeternext.test"}</stringProp>
          <intProp name="LDAPSampler.timeout">10000</intProp>
        </GenericSampler>
        <hashTree/>

        <!-- Delete entry -->
        <GenericSampler guiclass="GenericSamplerGui" testclass="LDAPSampler"
                        testname="LDAP Delete User">
          <stringProp name="LDAPSampler.host">${LDAP_HOST}</stringProp>
          <intProp name="LDAPSampler.port">${LDAP_PORT}</intProp>
          <stringProp name="LDAPSampler.baseDN">dc=jmeternext,dc=test</stringProp>
          <stringProp name="LDAPSampler.bindDN">cn=admin,ou=users,dc=jmeternext,dc=test</stringProp>
          <stringProp name="LDAPSampler.bindPassword">admin123</stringProp>
          <stringProp name="LDAPSampler.action">delete</stringProp>
          <stringProp name="LDAPSampler.entryDN">cn=charlie,ou=users,dc=jmeternext,dc=test</stringProp>
          <intProp name="LDAPSampler.timeout">10000</intProp>
        </GenericSampler>
        <hashTree/>

      </hashTree>
    </hashTree>
  </hashTree>
</jmeterTestPlan>
```

### 7.5 Test Scenarios

| Scenario | Type | Description |
|----------|------|-------------|
| Anonymous bind | Smoke | Connect without credentials, verify success |
| Authenticated bind | Smoke | Bind as admin, verify 0 (success) |
| Search all persons | Smoke | `(objectClass=person)`, expect 3 results (admin, alice, bob) |
| Search by attribute | Functional | `(mail=alice@jmeternext.test)`, expect 1 result |
| Search with scope | Functional | base vs onelevel vs subtree, verify result count differences |
| Add entry | Functional | Create new user, search to confirm |
| Modify entry | Functional | Change alice's phone number, search to confirm |
| Delete entry | Functional | Delete entry, search to confirm gone |
| Bad bind credentials | Error | Wrong password, verify `InvalidCredentialsError` |
| Search non-existent base | Error | Invalid baseDN, verify `NoSuchObjectError` |
| Connection refused | Error | Invalid host/port, verify IOException |
| Load: search throughput | Performance | 20 VUs, 100 loops searching, measure p95 |

### 7.6 Metrics

| Metric | Source | Description |
|--------|--------|-------------|
| `connectTimeMs` | `InitialLdapContext` constructor time | LDAP connection + BIND time |
| `latencyMs` | Time from context creation to first search result | Query execution latency |
| `totalTimeMs` | Full operation including context close | End-to-end operation time |
| `statusCode` | LDAP result code or search result count | 0 = success for operations, N = result count for searches |
| `responseBody` | DNs found (search) or operation confirmation | Available for assertions |
| Search result count | Number of entries returned | Key metric for directory performance |

### 7.7 Priority: **P2** — next sprint

**Justification**: LDAP is widely used in enterprise authentication and authorization. The JDK has full LDAP support via JNDI, making this a pure-JDK implementation. Lower priority than gRPC/MQTT/DASH because LDAP load testing is a niche requirement compared to HTTP/gRPC/streaming.

### 7.8 Effort: **M** (Medium)

New Java executor (~200 lines, JNDI is verbose). Mock server ~80 lines. NodeInterpreter dispatch case. Unit tests. JSON attribute parsing for add/modify operations.

---

## Summary: Priority Matrix

| # | Protocol | Status | Priority | Effort | Java Changes | Mock Server | JMX Plan |
|---|----------|--------|----------|--------|-------------|-------------|----------|
| 1 | gRPC | Executor exists | **P1** | **S** | None | New (Node.js + @grpc/grpc-js) | New |
| 2 | MQTT | Executor + mock exist | **P1** | **S** | None | None (exists) | New |
| 3 | WebRTC | Executor exists (signaling) | **P2** | **M** | Expand executor (STUN/DTLS) | New (HTTP + UDP) | Update |
| 4 | DASH | Executor exists | **P1** | **S** | None | New (Node.js, no deps) | New |
| 5 | QUIC/HTTP3 | Nothing exists | **P3** | **L** | New executor + kwik dep | New (Python/Go) | New |
| 6 | FTP | Nothing exists | **P2** | **M** | New executor (raw TCP) | New (Node.js + ftp-srv) | New |
| 7 | LDAP | Nothing exists | **P2** | **M** | New executor (JNDI) | New (Node.js + ldapjs) | New |

### Recommended Execution Order

**Sprint 1 (P1 — immediate)**:
1. MQTT JMX plan + E2E tests (smallest delta, validates pipeline)
2. gRPC mock server + JMX plan (high-value protocol, small effort)
3. DASH mock server + JMX plan (completes streaming coverage with HLS)

**Sprint 2 (P2 — next)**:
4. FTP sampler + mock server (new executor, simple protocol)
5. LDAP sampler + mock server (new executor, JDK JNDI)
6. WebRTC ICE/STUN expansion (extends existing executor)

**Backlog (P3 — defer)**:
7. QUIC/HTTP3 (blocked on JDK support or external dependency decision)

### NodeInterpreter Changes Required

For protocols 6 and 7, add dispatch cases to `NodeInterpreter.dispatchNode()`:

```java
case "FTPSampler" -> {
    SampleResult r = new SampleResult(node.getTestName());
    FTPSamplerExecutor.execute(node, r, variables);
    applyPostProcessors(node.getChildren(), r, variables);
    yield List.of(r);
}

case "LDAPSampler" -> {
    SampleResult r = new SampleResult(node.getTestName());
    LDAPSamplerExecutor.execute(node, r, variables);
    applyPostProcessors(node.getChildren(), r, variables);
    yield List.of(r);
}
```

No changes needed for protocols 1-4 (already dispatched by NodeInterpreter).

---

## Appendix A: File Locations for New Artifacts

```
test-servers/
  grpc-mock/
    server.js
    greeter.proto
    package.json
  dash-mock/
    server.js
  webrtc-mock/
    signaling.js
    stun.js
    package.json
  ftp-mock/
    server.js
    package.json
  ldap-mock/
    server.js
    package.json

plans/
  grpc-smoke.jmx
  mqtt-smoke.jmx
  dash-smoke.jmx
  ftp-smoke.jmx
  ldap-smoke.jmx

modules/engine-service/src/main/java/com/jmeternext/engine/service/interpreter/
  FTPSamplerExecutor.java     (new)
  LDAPSamplerExecutor.java    (new)
  WebRTCSamplerExecutor.java  (modify — add STUN/DTLS modes)
  NodeInterpreter.java        (modify — add FTPSampler + LDAPSampler cases)

modules/engine-service/src/test/java/com/jmeternext/engine/service/interpreter/
  FTPSamplerExecutorTest.java   (new)
  LDAPSamplerExecutorTest.java  (new)
```

## Appendix B: Dependency Impact

| Mock Server | npm Dependency | Why | Alternative |
|------------|---------------|-----|-------------|
| grpc-mock | `@grpc/grpc-js`, `@grpc/proto-loader` | Official gRPC Node.js implementation | Could use raw HTTP/2 frames but loses proto validation |
| dash-mock | None | Plain `http.createServer`, XML string templates | N/A |
| webrtc-mock | None | Plain `http` + `dgram` modules | N/A |
| ftp-mock | `ftp-srv` | Full FTP server with PASV, auth, filesystem | Could implement raw but 500+ lines vs 40 |
| ldap-mock | `ldapjs` | Standard LDAP server for Node.js | No viable pure-Node alternative |

**Java side**: Zero new dependencies for protocols 1-4, 6-7. All use JDK-only APIs. Protocol 5 (QUIC) would require kwik if implemented.
