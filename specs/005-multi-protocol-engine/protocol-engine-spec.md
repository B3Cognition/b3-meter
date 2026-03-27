# Multi-Protocol Engine Specification

**Version**: 1.0
**Date**: 2026-03-25
**Status**: Draft

---

## 1. Architecture Overview

The multi-protocol engine extends jMeter Next's existing `NodeInterpreter` dispatch
mechanism with protocol-specific sampler executors. Each new protocol sampler follows
the same pattern as the existing `HttpSamplerExecutor`: it receives a `PlanNode`, a
mutable variable map, and returns a `SampleResult`.

### Integration Point

The `NodeInterpreter.dispatchNode()` switch expression currently handles:
- `HTTPSamplerProxy` / `HTTPSampler` via `HttpSamplerExecutor`
- `LoopController`, `IfController`, `TransactionController`
- `ResponseAssertion`, timers, extractors

Each new protocol sampler adds a new case branch in this switch. The sampler executor
classes live in `engine-service` under `com.jmeternext.engine.service.interpreter`
(framework-free, JDK-only). Protocol client libraries are injected via factory
interfaces defined in `engine-service` and implemented in `engine-adapter`.

### Results Pipeline (unchanged)

```
VU Thread
  -> SamplerExecutor.execute(PlanNode, variables) -> SampleResult
  -> NodeInterpreter collects List<SampleResult>
  -> publishBuckets() aggregates into SampleBucket (per label, per second)
  -> SampleStreamBroker.publish(runId, bucket)
  -> consumers: SSE endpoint, SLA evaluator, Prometheus exporter, JSON reporter
```

---

## 2. Protocol Samplers

### 2.1 WebSocketSampler

#### JMX Element Name
`WebSocketSampler`

#### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `WebSocketSampler.url` | string | `ws://localhost:8080/ws` | WebSocket endpoint URL (ws:// or wss://) |
| `WebSocketSampler.subprotocol` | string | _(empty)_ | Sec-WebSocket-Protocol header value |
| `WebSocketSampler.connect_timeout` | int | 5000 | Connection timeout in ms |
| `WebSocketSampler.read_timeout` | int | 30000 | Max wait time for a response message in ms |
| `WebSocketSampler.message` | string | _(empty)_ | Message payload to send after connect |
| `WebSocketSampler.message_type` | enum | `TEXT` | `TEXT` or `BINARY` |
| `WebSocketSampler.expected_messages` | int | 1 | Number of response messages to wait for |
| `WebSocketSampler.close_connection` | bool | false | Close after receiving expected messages |
| `WebSocketSampler.connection_id` | string | _(empty)_ | Reuse named connection across samplers |
| `WebSocketSampler.headers` | elementProp | _(empty)_ | Custom HTTP headers for upgrade request |

#### Connection Lifecycle

1. **Connect**: If `connection_id` is set and a connection with that name exists in
   the VU's variable scope, reuse it. Otherwise, perform WebSocket handshake
   (HTTP Upgrade) with optional subprotocol negotiation.
2. **Send**: If `message` is non-empty, send it as TEXT or BINARY frame.
3. **Receive**: Wait for `expected_messages` response frames, up to `read_timeout`.
   Each received frame is appended to `SampleResult.responseBody` (newline-delimited).
4. **Close**: If `close_connection` is true, send a Close frame (status 1000) and
   await the server's Close response. Otherwise, store the connection in VU variables
   under `connection_id` for reuse.

#### Metrics Collected

| Metric | Granularity | How Measured |
|--------|-------------|--------------|
| Connect latency | Per sample | Time from TCP SYN to WebSocket upgrade complete |
| First message latency | Per sample | Time from send to first frame received |
| Total time | Per sample | Connect + send + wait for all expected messages |
| Message count | Per sample | Number of frames received |
| Bytes received | Per sample | Sum of all received frame payloads |
| Error rate | Per bucket | Timeout, connection refused, unexpected close |

#### Error Handling

- **Connection refused / DNS failure**: SampleResult.success = false, statusCode = 0,
  failureMessage = exception message.
- **Upgrade rejected (non-101)**: SampleResult.success = false, statusCode = HTTP status.
- **Read timeout**: SampleResult.success = false if fewer than `expected_messages` received.
- **Unexpected close**: SampleResult.success = false, close code/reason in failureMessage.

#### Thread Safety

Each VU maintains its own WebSocket connection(s) in its variable map. Connections are
never shared between VUs. The `connection_id` mechanism allows a single VU to reuse one
connection across multiple sampler iterations.

#### Engine Integration

```java
// In NodeInterpreter.dispatchNode():
case "WebSocketSampler" -> {
    SampleResult r = webSocketExecutor.execute(node, variables);
    applyPostProcessors(node.getChildren(), r, variables);
    yield List.of(r);
}
```

`WebSocketSamplerExecutor` depends on a `WebSocketClientFactory` interface (in
`engine-service`), implemented by `Jdk11WebSocketClientFactory` in `engine-adapter`
using `java.net.http.WebSocket`.

---

### 2.2 HTTPSampler (existing, extended)

The existing `HttpSamplerExecutor` is already implemented. Extensions needed:

#### Additional Properties for HTTP/2 and HTTP/3

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `HTTPSampler.http_protocol` | enum | `HTTP/1.1` | `HTTP/1.1`, `HTTP/2`, `HTTP/3` |
| `HTTPSampler.follow_redirects` | bool | true | Follow 3xx redirects |
| `HTTPSampler.max_redirects` | int | 5 | Maximum redirect chain length |
| `HTTPSampler.content_encoding` | string | `UTF-8` | Request body encoding |
| `HTTPSampler.embedded_resources` | bool | false | Download embedded resources (images, CSS, JS) |

The existing `HttpClientFactory` and `SampleResult` fields already cover connect time,
latency, total time, status code, and response body. The `Hc5HttpClientFactory`
supports HTTP/2 natively; HTTP/3 requires a future adapter (flagged as stretch goal).

---

### 2.3 HLSSampler

#### JMX Element Name
`HLSSampler`

#### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `HLSSampler.master_url` | string | _(required)_ | URL of the master M3U8 playlist |
| `HLSSampler.variant_selection` | enum | `HIGHEST_BANDWIDTH` | `HIGHEST_BANDWIDTH`, `LOWEST_BANDWIDTH`, `RANDOM`, `SPECIFIC` |
| `HLSSampler.specific_bandwidth` | int | 0 | Target bandwidth when variant_selection = SPECIFIC |
| `HLSSampler.segment_count` | int | 3 | Number of segments to download per iteration |
| `HLSSampler.segment_timeout` | int | 10000 | Per-segment download timeout in ms |
| `HLSSampler.playlist_refresh_interval` | int | 0 | Media playlist refresh interval in ms (0 = use #EXT-X-TARGETDURATION) |
| `HLSSampler.connect_timeout` | int | 5000 | HTTP connect timeout for playlist/segment requests |

#### Connection Lifecycle

1. **Fetch master playlist**: HTTP GET on `master_url`, parse M3U8 to extract variant streams.
2. **Select variant**: Choose a media playlist URL based on `variant_selection` strategy.
3. **Fetch media playlist**: HTTP GET on the selected variant URL, parse for segment URLs.
4. **Download segments**: Fetch up to `segment_count` TS segments sequentially. For live
   streams, wait `playlist_refresh_interval` between playlist refreshes.
5. **Repeat**: If the stream is live (no `#EXT-X-ENDLIST`), refresh the media playlist
   and download new segments until the iteration completes.

#### Metrics Collected

| Metric | Granularity | How Measured |
|--------|-------------|--------------|
| Master playlist latency | Per sample | HTTP response time for master M3U8 |
| Media playlist latency | Per sample | HTTP response time for variant M3U8 |
| Segment download time | Per segment | HTTP response time per TS segment |
| Segment throughput | Per segment | Bytes / download time (bytes/s) |
| Total bitrate consumed | Per sample | Sum of segment sizes / total time |
| Buffer stall count | Per sample | Segments that took longer than target duration |
| Error rate | Per bucket | 4xx/5xx on playlist or segment fetches |

#### Error Handling

- **Playlist parse failure**: Mark sample as failed, include parse error in failureMessage.
- **Variant not found**: Fall back to first available variant; log warning.
- **Segment 404**: Mark individual sub-result as failed; continue with next segment.
- **Timeout**: Mark segment as failed; increment stall count.

#### Thread Safety

Each VU has an independent HLS client. No connection reuse across VUs. Segment
downloads within a single VU are sequential (models real player behavior).

---

### 2.4 SSESampler (Server-Sent Events)

#### JMX Element Name
`SSESampler`

#### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `SSESampler.url` | string | _(required)_ | SSE endpoint URL |
| `SSESampler.connect_timeout` | int | 5000 | Connection timeout in ms |
| `SSESampler.listen_duration` | int | 30000 | How long to listen for events in ms |
| `SSESampler.expected_events` | int | 0 | Stop after N events (0 = use listen_duration) |
| `SSESampler.last_event_id` | string | _(empty)_ | Last-Event-ID header for reconnection |
| `SSESampler.event_filter` | string | _(empty)_ | Only count events matching this event type |
| `SSESampler.headers` | elementProp | _(empty)_ | Custom HTTP headers |

#### Connection Lifecycle

1. **Connect**: HTTP GET with `Accept: text/event-stream`. Include `Last-Event-ID`
   header if set.
2. **Listen**: Parse the SSE stream (field-by-field: `event:`, `data:`, `id:`,
   `retry:`). Accumulate events matching `event_filter` (or all events if filter is empty).
3. **Complete**: Close connection after `listen_duration` elapses or `expected_events`
   events are received (whichever comes first).
4. **Reconnect**: If the server closes the connection before completion, reconnect
   with the last received event ID. Respect the server's `retry:` field.

#### Metrics Collected

| Metric | Granularity | How Measured |
|--------|-------------|--------------|
| Connect latency | Per sample | Time to establish HTTP connection |
| First event latency | Per sample | Time from connect to first event received |
| Events received | Per sample | Count of SSE events |
| Event rate | Per sample | Events / listen_duration (events/s) |
| Bytes received | Per sample | Total bytes of event data fields |
| Inter-event gap (max) | Per sample | Maximum gap between consecutive events |
| Reconnect count | Per sample | Number of reconnections during listen_duration |
| Error rate | Per bucket | Connection failures, non-200 responses |

#### Error Handling

- **Non-200 response**: Mark failed; include status code.
- **Connection drop mid-stream**: Attempt reconnect up to 3 times with exponential backoff.
  If all retries fail, mark sample as failed.
- **Malformed SSE data**: Log warning, skip malformed event, continue listening.

#### Thread Safety

Each VU has its own SSE connection. The SSE connection is not shared. The sampler
maintains state (last event ID) in the VU's variable map for subsequent iterations.

---

### 2.5 GrpcSampler

#### JMX Element Name
`GrpcSampler`

#### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `GrpcSampler.host` | string | `localhost` | gRPC server hostname |
| `GrpcSampler.port` | int | 50051 | gRPC server port |
| `GrpcSampler.service` | string | _(required)_ | Fully qualified service name (e.g., `com.example.UserService`) |
| `GrpcSampler.method` | string | _(required)_ | RPC method name (e.g., `GetUser`) |
| `GrpcSampler.proto_file` | string | _(empty)_ | Path to .proto file (for reflection fallback) |
| `GrpcSampler.use_reflection` | bool | true | Use gRPC server reflection to discover schema |
| `GrpcSampler.request_json` | string | `{}` | Request message as JSON (mapped to Protobuf) |
| `GrpcSampler.metadata` | elementProp | _(empty)_ | gRPC metadata key-value pairs (headers) |
| `GrpcSampler.tls` | bool | false | Use TLS (grpcs://) |
| `GrpcSampler.deadline_ms` | int | 5000 | RPC deadline in ms |
| `GrpcSampler.call_type` | enum | `UNARY` | `UNARY`, `SERVER_STREAMING`, `CLIENT_STREAMING`, `BIDI_STREAMING` |
| `GrpcSampler.stream_messages` | int | 10 | For streaming: number of messages to send/receive |

#### Connection Lifecycle

1. **Channel setup**: Create a `ManagedChannel` to `host:port`. Channels are cached
   per VU per target (host:port) to amortize connection cost.
2. **Schema resolution**: Use gRPC reflection or parse `.proto` file to get service
   descriptor. Cache descriptors per VU.
3. **Invoke**: Build a `DynamicMessage` from `request_json`, invoke the RPC.
   - **Unary**: Single request, single response.
   - **Server streaming**: Single request, collect up to `stream_messages` responses.
   - **Client streaming**: Send `stream_messages` requests, receive single response.
   - **Bidi streaming**: Send and receive `stream_messages` messages concurrently.
4. **Close**: Channel is reused across iterations. Closed when the VU's test loop ends.

#### Metrics Collected

| Metric | Granularity | How Measured |
|--------|-------------|--------------|
| Channel connect latency | Per sample (first call) | Time to establish HTTP/2 connection |
| RPC latency | Per sample | Time from invoke to final response |
| First message latency | Per sample | Time to first response message (streaming) |
| Messages sent/received | Per sample | Count for streaming RPCs |
| Response size | Per sample | Serialized protobuf bytes |
| gRPC status code | Per sample | OK, UNAVAILABLE, DEADLINE_EXCEEDED, etc. |
| Error rate | Per bucket | Non-OK gRPC status codes |

#### Error Handling

- **UNAVAILABLE**: SampleResult.success = false, failureMessage = "gRPC UNAVAILABLE".
- **DEADLINE_EXCEEDED**: SampleResult.success = false, totalTimeMs = deadline_ms.
- **INVALID_ARGUMENT**: SampleResult.success = false (malformed request JSON).
- **Schema not found**: Fail fast with descriptive error message.

#### Thread Safety

Each VU owns its own `ManagedChannel` instance. Channels are thread-safe by design
(gRPC spec) but we isolate them per VU to avoid contention on shared channels under load.

---

### 2.6 MQTTSampler

#### JMX Element Name
`MQTTSampler`

#### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `MQTTSampler.broker_url` | string | `tcp://localhost:1883` | MQTT broker URL (tcp:// or ssl://) |
| `MQTTSampler.client_id` | string | `jmeter-${__threadNum}` | Client ID (supports variable substitution) |
| `MQTTSampler.username` | string | _(empty)_ | MQTT username |
| `MQTTSampler.password` | string | _(empty)_ | MQTT password |
| `MQTTSampler.action` | enum | `PUBLISH` | `CONNECT`, `PUBLISH`, `SUBSCRIBE`, `DISCONNECT` |
| `MQTTSampler.topic` | string | _(required)_ | MQTT topic |
| `MQTTSampler.message` | string | _(empty)_ | Publish payload |
| `MQTTSampler.qos` | enum | `0` | `0` (at most once), `1` (at least once), `2` (exactly once) |
| `MQTTSampler.retain` | bool | false | Retain flag on published messages |
| `MQTTSampler.subscribe_duration` | int | 10000 | How long to wait for messages when subscribing (ms) |
| `MQTTSampler.expected_messages` | int | 1 | Number of messages to wait for when subscribing |
| `MQTTSampler.connect_timeout` | int | 5000 | Connection timeout in ms |
| `MQTTSampler.keep_alive` | int | 60 | Keep-alive interval in seconds |
| `MQTTSampler.clean_session` | bool | true | Clean session flag |
| `MQTTSampler.connection_id` | string | _(empty)_ | Reuse named connection across samplers |

#### Connection Lifecycle

1. **CONNECT action**: Establish MQTT connection to broker. Store in VU variables
   under `connection_id`. If `connection_id` already exists and is connected, skip.
2. **PUBLISH action**: Publish `message` to `topic` with specified QoS. Requires an
   active connection (auto-connect if `connection_id` is set and no active connection).
3. **SUBSCRIBE action**: Subscribe to `topic`, wait for `expected_messages` or
   `subscribe_duration` timeout. Received messages stored in responseBody.
4. **DISCONNECT action**: Send DISCONNECT packet and close the connection. Remove
   from VU variables.

#### Metrics Collected

| Metric | Granularity | How Measured |
|--------|-------------|--------------|
| Connect latency | Per sample | Time from TCP connect to CONNACK |
| Publish latency | Per sample | Time from PUBLISH to PUBACK (QoS 1) or PUBCOMP (QoS 2) |
| Subscribe latency | Per sample | Time from SUBSCRIBE to first message received |
| Messages received | Per sample | Count of received PUBLISH packets |
| Bytes sent/received | Per sample | Payload sizes |
| QoS completion time | Per sample | Full QoS handshake time (QoS 2: PUBLISH->PUBCOMP) |
| Error rate | Per bucket | Connection refused, subscribe timeout, publish timeout |

#### Error Handling

- **Connection refused**: Return error code from CONNACK (bad credentials, server unavailable).
- **Publish timeout**: QoS 1/2 acknowledgment not received within connect_timeout.
- **Subscribe timeout**: Fewer than `expected_messages` received within `subscribe_duration`.
- **Broker disconnect**: Auto-reconnect with exponential backoff (max 3 attempts).

#### Thread Safety

Each VU has its own MQTT client with a unique `client_id`. The `connection_id` mechanism
allows connection reuse within a single VU's iteration loop. No cross-VU sharing.

---

### 2.7 WebRTCSampler

#### JMX Element Name
`WebRTCSampler`

#### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `WebRTCSampler.signaling_url` | string | _(required)_ | Signaling server URL (HTTP or WebSocket) |
| `WebRTCSampler.signaling_type` | enum | `HTTP_POST` | `HTTP_POST`, `WEBSOCKET` |
| `WebRTCSampler.stream_id` | string | _(required)_ | Stream/channel to subscribe to |
| `WebRTCSampler.listen_duration` | int | 30000 | Duration to receive media in ms |
| `WebRTCSampler.ice_servers` | string | _(empty)_ | JSON array of ICE server configs |
| `WebRTCSampler.sdp_offer_timeout` | int | 5000 | Timeout for SDP offer/answer exchange |
| `WebRTCSampler.dtls_timeout` | int | 10000 | DTLS handshake timeout |
| `WebRTCSampler.media_type` | enum | `AUDIO_VIDEO` | `AUDIO`, `VIDEO`, `AUDIO_VIDEO` |
| `WebRTCSampler.report_rtp_stats` | bool | true | Collect RTP/RTCP packet-level stats |

#### Connection Lifecycle

1. **Signaling**: Send SDP offer to signaling server, receive SDP answer.
2. **ICE negotiation**: Gather local ICE candidates, exchange with remote via signaling.
3. **DTLS handshake**: Establish DTLS-SRTP session.
4. **Media reception**: Receive RTP packets for `listen_duration`. Parse RTCP sender
   reports for media quality metrics.
5. **Teardown**: Send BYE, close DTLS session.

#### Metrics Collected

| Metric | Granularity | How Measured |
|--------|-------------|--------------|
| Signaling latency | Per sample | Time for SDP offer/answer exchange |
| ICE negotiation time | Per sample | Time from first candidate to connectivity check pass |
| DTLS handshake time | Per sample | Time from ClientHello to Finished |
| Time to first frame | Per sample | Time from SDP answer to first RTP packet received |
| Packet loss % | Per sample | (expected - received) / expected from RTCP SR |
| Jitter | Per sample | Inter-arrival jitter from RTCP |
| Round-trip time | Per sample | From RTCP receiver reports |
| E2E latency | Per sample | NTP timestamp delta from RTCP sender reports |
| Bitrate (received) | Per sample | Total RTP payload bytes / listen_duration |
| Frame rate | Per sample | Video frames decoded / listen_duration |

#### Error Handling

- **Signaling failure**: HTTP error or WebSocket close before SDP answer. Mark failed.
- **ICE failure**: No valid candidate pair found within timeout. Mark failed.
- **DTLS failure**: Handshake timeout or certificate error. Mark failed.
- **No media**: Zero RTP packets received after DTLS success. Mark failed with warning.

#### Thread Safety

Each VU runs its own WebRTC peer connection. The underlying RTP/RTCP handling uses
per-VU UDP sockets. No state is shared between VUs.

**Implementation note**: WebRTC is the most complex sampler. Phase 1 implements
signaling + SDP exchange + ICE latency measurement. Phase 2 adds RTP packet reception
and RTCP stats. Phase 3 adds DTLS-SRTP for encrypted streams. The sampler uses
the `jwebrtc` library (or `pion/webrtc` via JNI if Java options are insufficient).

---

### 2.8 DASHSampler (MPEG-DASH)

#### JMX Element Name
`DASHSampler`

#### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `DASHSampler.mpd_url` | string | _(required)_ | URL of the DASH MPD manifest |
| `DASHSampler.adaptation_set` | enum | `HIGHEST_BANDWIDTH` | `HIGHEST_BANDWIDTH`, `LOWEST_BANDWIDTH`, `SPECIFIC` |
| `DASHSampler.specific_bandwidth` | int | 0 | Target bandwidth for SPECIFIC selection |
| `DASHSampler.segment_count` | int | 3 | Number of segments to download |
| `DASHSampler.segment_timeout` | int | 10000 | Per-segment download timeout in ms |
| `DASHSampler.connect_timeout` | int | 5000 | HTTP connect timeout |

#### Connection Lifecycle

Mirrors HLS but parses MPEG-DASH MPD (XML) manifests instead of M3U8. The segment
download loop is identical. Uses the same `HttpClientFactory` for HTTP requests.

#### Metrics Collected

Same as HLSSampler (see section 2.3) with the addition of:

| Metric | Granularity | How Measured |
|--------|-------------|--------------|
| MPD parse time | Per sample | Time to parse the XML MPD manifest |

---

## 3. NodeInterpreter Dispatch Extension

The `NodeInterpreter.dispatchNode()` method gains new case branches:

```java
return switch (testClass) {
    // Existing cases...
    case "HTTPSamplerProxy", "HTTPSampler" -> { ... }

    // New protocol samplers
    case "WebSocketSampler" -> {
        SampleResult r = webSocketExecutor.execute(node, variables);
        applyPostProcessors(node.getChildren(), r, variables);
        yield List.of(r);
    }
    case "HLSSampler" -> {
        SampleResult r = hlsExecutor.execute(node, variables);
        applyPostProcessors(node.getChildren(), r, variables);
        yield List.of(r);
    }
    case "SSESampler" -> {
        SampleResult r = sseExecutor.execute(node, variables);
        applyPostProcessors(node.getChildren(), r, variables);
        yield List.of(r);
    }
    case "GrpcSampler" -> {
        SampleResult r = grpcExecutor.execute(node, variables);
        applyPostProcessors(node.getChildren(), r, variables);
        yield List.of(r);
    }
    case "MQTTSampler" -> {
        SampleResult r = mqttExecutor.execute(node, variables);
        applyPostProcessors(node.getChildren(), r, variables);
        yield List.of(r);
    }
    case "WebRTCSampler" -> {
        SampleResult r = webrtcExecutor.execute(node, variables);
        applyPostProcessors(node.getChildren(), r, variables);
        yield List.of(r);
    }
    case "DASHSampler" -> {
        SampleResult r = dashExecutor.execute(node, variables);
        applyPostProcessors(node.getChildren(), r, variables);
        yield List.of(r);
    }

    // Existing cases continue...
    default -> { ... }
};
```

### Executor Factory Pattern

Each executor depends on a protocol-specific client factory:

```
engine-service (interfaces):
  WebSocketClientFactory
  HlsClientFactory (reuses HttpClientFactory)
  SseClientFactory (reuses HttpClientFactory)
  GrpcClientFactory
  MqttClientFactory
  WebRtcClientFactory

engine-adapter (implementations):
  Jdk11WebSocketClientFactory   (java.net.http.WebSocket)
  Hc5HlsClientFactory           (wraps Hc5HttpClientFactory)
  Hc5SseClientFactory            (wraps Hc5HttpClientFactory, chunked reading)
  GrpcChannelFactory             (io.grpc.ManagedChannelBuilder)
  PahoMqttClientFactory          (Eclipse Paho MQTT v5)
  PionWebRtcClientFactory        (JNI to pion/webrtc or pure-Java fallback)
```

### SampleResult Extension

The existing `SampleResult` class has all required fields: `label`, `success`,
`statusCode`, `connectTimeMs`, `latencyMs`, `totalTimeMs`, `responseBody`,
`failureMessage`. No changes are needed to the core result class.

Protocol-specific metrics (e.g., packet loss, jitter, segment throughput) are stored
as key-value pairs in `responseBody` as structured JSON when the sampler is configured
for detailed reporting. This avoids modifying the core `SampleResult` contract.

### Connection Pooling Strategy

| Protocol | Pool Strategy | Scope |
|----------|---------------|-------|
| HTTP | Connection pool per VU (HC5 manages internally) | Per VU, per host:port |
| WebSocket | Named connections in VU variable map | Per VU, by connection_id |
| HLS/DASH | No pooling (sequential segment requests reuse HTTP pool) | Per VU |
| SSE | Single long-lived connection per sample | Per VU, per sample |
| gRPC | ManagedChannel per VU per host:port | Per VU, per target |
| MQTT | Client per VU (persistent, keyed by connection_id) | Per VU |
| WebRTC | PeerConnection per VU per sample | Per VU, per sample |

---

## 4. Implementation Priority

| Priority | Sampler | Rationale |
|----------|---------|-----------|
| P0 | HTTP (existing) | Already implemented. Extend with HTTP/2 property. |
| P1 | WebSocketSampler | Discovered in StreamLoad, ws-test-server, RMG repos. Most used. |
| P1 | SSESampler | High priority gap protocol; used internally for dashboard streaming. |
| P2 | HLSSampler | Discovered in StreamLoad Phase 2 spec. Media streaming validation. |
| P2 | GrpcSampler | Medium priority gap; used in distributed worker protocol already. |
| P2 | MQTTSampler | Medium priority gap; IoT and event-driven architectures. |
| P3 | WebRTCSampler | Complex. Phased implementation (signaling first, then RTP). |
| P3 | DASHSampler | Low priority; extends HLS architecture with MPD parser. |

---

## 5. Plugin Component Registration

Each new sampler must register with the `ComponentRegistry` (in `engine-service`'s
`plugin` package) so the UI's tree editor can offer it in the "Add Child" context menu:

```java
// In ComponentRegistry initialization:
registry.register("WebSocketSampler", "WebSocket Sampler", "Samplers");
registry.register("HLSSampler",      "HLS Sampler",       "Samplers");
registry.register("SSESampler",      "SSE Sampler",       "Samplers");
registry.register("GrpcSampler",     "gRPC Sampler",      "Samplers");
registry.register("MQTTSampler",     "MQTT Sampler",      "Samplers");
registry.register("WebRTCSampler",   "WebRTC Sampler",    "Samplers");
registry.register("DASHSampler",     "DASH Sampler",      "Samplers");
```

The web-ui `NodeContextMenu` currently hard-codes `HTTPSampler` as the child type.
This must be extended to show all registered sampler types from the component registry,
fetched via a REST endpoint (`GET /api/components`).
