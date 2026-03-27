# JMX Schema Extensions for New Protocol Samplers

**Version**: 1.0
**Date**: 2026-03-25
**Status**: Draft

---

## 1. Overview

Each new protocol sampler is represented as a JMX XML element with the standard
jMeter element structure: `testclass` and `testname` attributes, `enabled` boolean
property, and protocol-specific properties as `<stringProp>`, `<intProp>`,
`<boolProp>`, and `<elementProp>` children.

All new elements follow the same parsing path: `JmxTreeWalker.parse()` produces a
`PlanNode` with `testClass` set to the element tag name and properties extracted from
the typed prop children. The `NodeInterpreter` dispatches to the corresponding executor
based on the `testClass` value.

---

## 2. WebSocketSampler

```xml
<WebSocketSampler guiclass="WebSocketSamplerGui" testclass="WebSocketSampler"
                  testname="WS Subscribe - Sports" enabled="true">
  <stringProp name="WebSocketSampler.url">ws://localhost:8082/ws</stringProp>
  <stringProp name="WebSocketSampler.subprotocol"></stringProp>
  <intProp name="WebSocketSampler.connect_timeout">5000</intProp>
  <intProp name="WebSocketSampler.read_timeout">30000</intProp>
  <stringProp name="WebSocketSampler.message">{"action":"subscribe","channel":"sports"}</stringProp>
  <stringProp name="WebSocketSampler.message_type">TEXT</stringProp>
  <intProp name="WebSocketSampler.expected_messages">3</intProp>
  <boolProp name="WebSocketSampler.close_connection">false</boolProp>
  <stringProp name="WebSocketSampler.connection_id">ws-sports</stringProp>
  <elementProp name="WebSocketSampler.headers" elementType="HeaderManager"
               guiclass="HeaderPanel" testclass="HeaderManager" testname="Headers">
    <collectionProp name="HeaderManager.headers">
      <elementProp name="" elementType="Header">
        <stringProp name="Header.name">Authorization</stringProp>
        <stringProp name="Header.value">Bearer ${auth_token}</stringProp>
      </elementProp>
    </collectionProp>
  </elementProp>
</WebSocketSampler>
```

### Property Reference

| Property Name | Type | Required | Default |
|---------------|------|----------|---------|
| `WebSocketSampler.url` | stringProp | Yes | `ws://localhost:8080/ws` |
| `WebSocketSampler.subprotocol` | stringProp | No | _(empty)_ |
| `WebSocketSampler.connect_timeout` | intProp | No | `5000` |
| `WebSocketSampler.read_timeout` | intProp | No | `30000` |
| `WebSocketSampler.message` | stringProp | No | _(empty)_ |
| `WebSocketSampler.message_type` | stringProp | No | `TEXT` |
| `WebSocketSampler.expected_messages` | intProp | No | `1` |
| `WebSocketSampler.close_connection` | boolProp | No | `false` |
| `WebSocketSampler.connection_id` | stringProp | No | _(empty)_ |
| `WebSocketSampler.headers` | elementProp | No | _(empty)_ |

### UI Schema (Zod)

```typescript
// src/components/PropertyPanel/schemas/webSocketSamplerSchema.ts
export const webSocketSamplerSchema = z.object({
  url: z.string().min(1, 'URL is required').regex(/^wss?:\/\//, 'Must start with ws:// or wss://'),
  subprotocol: z.string().optional(),
  connect_timeout: z.number().int().min(0).max(300000),
  read_timeout: z.number().int().min(0).max(300000),
  message: z.string().optional(),
  message_type: z.enum(['TEXT', 'BINARY']),
  expected_messages: z.number().int().min(0).max(100000),
  close_connection: z.boolean(),
  connection_id: z.string().optional(),
});

export const webSocketSamplerDefaults = {
  url: 'ws://localhost:8080/ws',
  subprotocol: '',
  connect_timeout: 5000,
  read_timeout: 30000,
  message: '',
  message_type: 'TEXT',
  expected_messages: 1,
  close_connection: false,
  connection_id: '',
};
```

---

## 3. HLSSampler

```xml
<HLSSampler guiclass="HLSSamplerGui" testclass="HLSSampler"
            testname="HLS Playback - 720p" enabled="true">
  <stringProp name="HLSSampler.master_url">http://localhost:8083/live/master.m3u8</stringProp>
  <stringProp name="HLSSampler.variant_selection">HIGHEST_BANDWIDTH</stringProp>
  <intProp name="HLSSampler.specific_bandwidth">0</intProp>
  <intProp name="HLSSampler.segment_count">3</intProp>
  <intProp name="HLSSampler.segment_timeout">10000</intProp>
  <intProp name="HLSSampler.playlist_refresh_interval">0</intProp>
  <intProp name="HLSSampler.connect_timeout">5000</intProp>
</HLSSampler>
```

### Property Reference

| Property Name | Type | Required | Default |
|---------------|------|----------|---------|
| `HLSSampler.master_url` | stringProp | Yes | _(none)_ |
| `HLSSampler.variant_selection` | stringProp | No | `HIGHEST_BANDWIDTH` |
| `HLSSampler.specific_bandwidth` | intProp | No | `0` |
| `HLSSampler.segment_count` | intProp | No | `3` |
| `HLSSampler.segment_timeout` | intProp | No | `10000` |
| `HLSSampler.playlist_refresh_interval` | intProp | No | `0` |
| `HLSSampler.connect_timeout` | intProp | No | `5000` |

### UI Schema (Zod)

```typescript
export const hlsSamplerSchema = z.object({
  master_url: z.string().min(1, 'Master URL is required').url('Must be a valid URL'),
  variant_selection: z.enum(['HIGHEST_BANDWIDTH', 'LOWEST_BANDWIDTH', 'RANDOM', 'SPECIFIC']),
  specific_bandwidth: z.number().int().min(0),
  segment_count: z.number().int().min(1).max(1000),
  segment_timeout: z.number().int().min(1000).max(300000),
  playlist_refresh_interval: z.number().int().min(0),
  connect_timeout: z.number().int().min(0).max(300000),
});

export const hlsSamplerDefaults = {
  master_url: '',
  variant_selection: 'HIGHEST_BANDWIDTH',
  specific_bandwidth: 0,
  segment_count: 3,
  segment_timeout: 10000,
  playlist_refresh_interval: 0,
  connect_timeout: 5000,
};
```

---

## 4. SSESampler

```xml
<SSESampler guiclass="SSESamplerGui" testclass="SSESampler"
            testname="SSE Listen - Updates" enabled="true">
  <stringProp name="SSESampler.url">http://localhost:8084/events</stringProp>
  <intProp name="SSESampler.connect_timeout">5000</intProp>
  <intProp name="SSESampler.listen_duration">30000</intProp>
  <intProp name="SSESampler.expected_events">0</intProp>
  <stringProp name="SSESampler.last_event_id"></stringProp>
  <stringProp name="SSESampler.event_filter">update</stringProp>
  <elementProp name="SSESampler.headers" elementType="HeaderManager"
               guiclass="HeaderPanel" testclass="HeaderManager" testname="Headers">
    <collectionProp name="HeaderManager.headers">
      <elementProp name="" elementType="Header">
        <stringProp name="Header.name">Authorization</stringProp>
        <stringProp name="Header.value">Bearer ${token}</stringProp>
      </elementProp>
    </collectionProp>
  </elementProp>
</SSESampler>
```

### Property Reference

| Property Name | Type | Required | Default |
|---------------|------|----------|---------|
| `SSESampler.url` | stringProp | Yes | _(none)_ |
| `SSESampler.connect_timeout` | intProp | No | `5000` |
| `SSESampler.listen_duration` | intProp | No | `30000` |
| `SSESampler.expected_events` | intProp | No | `0` |
| `SSESampler.last_event_id` | stringProp | No | _(empty)_ |
| `SSESampler.event_filter` | stringProp | No | _(empty)_ |
| `SSESampler.headers` | elementProp | No | _(empty)_ |

### UI Schema (Zod)

```typescript
export const sseSamplerSchema = z.object({
  url: z.string().min(1, 'URL is required').url('Must be a valid URL'),
  connect_timeout: z.number().int().min(0).max(300000),
  listen_duration: z.number().int().min(1000).max(3600000),
  expected_events: z.number().int().min(0).max(1000000),
  last_event_id: z.string().optional(),
  event_filter: z.string().optional(),
});

export const sseSamplerDefaults = {
  url: '',
  connect_timeout: 5000,
  listen_duration: 30000,
  expected_events: 0,
  last_event_id: '',
  event_filter: '',
};
```

---

## 5. GrpcSampler

```xml
<GrpcSampler guiclass="GrpcSamplerGui" testclass="GrpcSampler"
             testname="gRPC GetUser" enabled="true">
  <stringProp name="GrpcSampler.host">localhost</stringProp>
  <intProp name="GrpcSampler.port">50051</intProp>
  <stringProp name="GrpcSampler.service">test.TestService</stringProp>
  <stringProp name="GrpcSampler.method">GetUser</stringProp>
  <stringProp name="GrpcSampler.proto_file"></stringProp>
  <boolProp name="GrpcSampler.use_reflection">true</boolProp>
  <stringProp name="GrpcSampler.request_json">{"id": 42}</stringProp>
  <boolProp name="GrpcSampler.tls">false</boolProp>
  <intProp name="GrpcSampler.deadline_ms">5000</intProp>
  <stringProp name="GrpcSampler.call_type">UNARY</stringProp>
  <intProp name="GrpcSampler.stream_messages">10</intProp>
  <elementProp name="GrpcSampler.metadata" elementType="HeaderManager"
               guiclass="HeaderPanel" testclass="HeaderManager" testname="Metadata">
    <collectionProp name="HeaderManager.headers">
      <elementProp name="" elementType="Header">
        <stringProp name="Header.name">x-request-id</stringProp>
        <stringProp name="Header.value">${__UUID}</stringProp>
      </elementProp>
    </collectionProp>
  </elementProp>
</GrpcSampler>
```

### Property Reference

| Property Name | Type | Required | Default |
|---------------|------|----------|---------|
| `GrpcSampler.host` | stringProp | Yes | `localhost` |
| `GrpcSampler.port` | intProp | Yes | `50051` |
| `GrpcSampler.service` | stringProp | Yes | _(none)_ |
| `GrpcSampler.method` | stringProp | Yes | _(none)_ |
| `GrpcSampler.proto_file` | stringProp | No | _(empty)_ |
| `GrpcSampler.use_reflection` | boolProp | No | `true` |
| `GrpcSampler.request_json` | stringProp | No | `{}` |
| `GrpcSampler.tls` | boolProp | No | `false` |
| `GrpcSampler.deadline_ms` | intProp | No | `5000` |
| `GrpcSampler.call_type` | stringProp | No | `UNARY` |
| `GrpcSampler.stream_messages` | intProp | No | `10` |
| `GrpcSampler.metadata` | elementProp | No | _(empty)_ |

### UI Schema (Zod)

```typescript
export const grpcSamplerSchema = z.object({
  host: z.string().min(1, 'Host is required'),
  port: z.number().int().min(1).max(65535),
  service: z.string().min(1, 'Service name is required'),
  method: z.string().min(1, 'Method name is required'),
  proto_file: z.string().optional(),
  use_reflection: z.boolean(),
  request_json: z.string(),
  tls: z.boolean(),
  deadline_ms: z.number().int().min(100).max(300000),
  call_type: z.enum(['UNARY', 'SERVER_STREAMING', 'CLIENT_STREAMING', 'BIDI_STREAMING']),
  stream_messages: z.number().int().min(1).max(100000),
});

export const grpcSamplerDefaults = {
  host: 'localhost',
  port: 50051,
  service: '',
  method: '',
  proto_file: '',
  use_reflection: true,
  request_json: '{}',
  tls: false,
  deadline_ms: 5000,
  call_type: 'UNARY',
  stream_messages: 10,
};
```

---

## 6. MQTTSampler

```xml
<MQTTSampler guiclass="MQTTSamplerGui" testclass="MQTTSampler"
             testname="MQTT Publish - Temperature" enabled="true">
  <stringProp name="MQTTSampler.broker_url">tcp://localhost:1883</stringProp>
  <stringProp name="MQTTSampler.client_id">jmeter-vu-${__threadNum}</stringProp>
  <stringProp name="MQTTSampler.username"></stringProp>
  <stringProp name="MQTTSampler.password"></stringProp>
  <stringProp name="MQTTSampler.action">PUBLISH</stringProp>
  <stringProp name="MQTTSampler.topic">test/temperature</stringProp>
  <stringProp name="MQTTSampler.message">{"value": 22.5, "unit": "C"}</stringProp>
  <stringProp name="MQTTSampler.qos">1</stringProp>
  <boolProp name="MQTTSampler.retain">false</boolProp>
  <intProp name="MQTTSampler.subscribe_duration">10000</intProp>
  <intProp name="MQTTSampler.expected_messages">1</intProp>
  <intProp name="MQTTSampler.connect_timeout">5000</intProp>
  <intProp name="MQTTSampler.keep_alive">60</intProp>
  <boolProp name="MQTTSampler.clean_session">true</boolProp>
  <stringProp name="MQTTSampler.connection_id">mqtt-main</stringProp>
</MQTTSampler>
```

### Property Reference

| Property Name | Type | Required | Default |
|---------------|------|----------|---------|
| `MQTTSampler.broker_url` | stringProp | Yes | `tcp://localhost:1883` |
| `MQTTSampler.client_id` | stringProp | No | `jmeter-${__threadNum}` |
| `MQTTSampler.username` | stringProp | No | _(empty)_ |
| `MQTTSampler.password` | stringProp | No | _(empty)_ |
| `MQTTSampler.action` | stringProp | Yes | `PUBLISH` |
| `MQTTSampler.topic` | stringProp | Yes | _(none)_ |
| `MQTTSampler.message` | stringProp | No | _(empty)_ |
| `MQTTSampler.qos` | stringProp | No | `0` |
| `MQTTSampler.retain` | boolProp | No | `false` |
| `MQTTSampler.subscribe_duration` | intProp | No | `10000` |
| `MQTTSampler.expected_messages` | intProp | No | `1` |
| `MQTTSampler.connect_timeout` | intProp | No | `5000` |
| `MQTTSampler.keep_alive` | intProp | No | `60` |
| `MQTTSampler.clean_session` | boolProp | No | `true` |
| `MQTTSampler.connection_id` | stringProp | No | _(empty)_ |

### UI Schema (Zod)

```typescript
export const mqttSamplerSchema = z.object({
  broker_url: z.string().min(1, 'Broker URL is required')
    .regex(/^(tcp|ssl|ws|wss):\/\//, 'Must start with tcp://, ssl://, ws://, or wss://'),
  client_id: z.string(),
  username: z.string().optional(),
  password: z.string().optional(),
  action: z.enum(['CONNECT', 'PUBLISH', 'SUBSCRIBE', 'DISCONNECT']),
  topic: z.string().min(1, 'Topic is required'),
  message: z.string().optional(),
  qos: z.enum(['0', '1', '2']),
  retain: z.boolean(),
  subscribe_duration: z.number().int().min(0).max(3600000),
  expected_messages: z.number().int().min(0).max(1000000),
  connect_timeout: z.number().int().min(0).max(300000),
  keep_alive: z.number().int().min(0).max(65535),
  clean_session: z.boolean(),
  connection_id: z.string().optional(),
});

export const mqttSamplerDefaults = {
  broker_url: 'tcp://localhost:1883',
  client_id: 'jmeter-${__threadNum}',
  username: '',
  password: '',
  action: 'PUBLISH',
  topic: '',
  message: '',
  qos: '1',
  retain: false,
  subscribe_duration: 10000,
  expected_messages: 1,
  connect_timeout: 5000,
  keep_alive: 60,
  clean_session: true,
  connection_id: '',
};
```

---

## 7. WebRTCSampler

```xml
<WebRTCSampler guiclass="WebRTCSamplerGui" testclass="WebRTCSampler"
               testname="WebRTC Stream - Live" enabled="true">
  <stringProp name="WebRTCSampler.signaling_url">http://localhost:8085/offer</stringProp>
  <stringProp name="WebRTCSampler.signaling_type">HTTP_POST</stringProp>
  <stringProp name="WebRTCSampler.stream_id">live-feed-001</stringProp>
  <intProp name="WebRTCSampler.listen_duration">30000</intProp>
  <stringProp name="WebRTCSampler.ice_servers">[{"urls":"stun:stun.l.google.com:19302"}]</stringProp>
  <intProp name="WebRTCSampler.sdp_offer_timeout">5000</intProp>
  <intProp name="WebRTCSampler.dtls_timeout">10000</intProp>
  <stringProp name="WebRTCSampler.media_type">AUDIO_VIDEO</stringProp>
  <boolProp name="WebRTCSampler.report_rtp_stats">true</boolProp>
</WebRTCSampler>
```

### Property Reference

| Property Name | Type | Required | Default |
|---------------|------|----------|---------|
| `WebRTCSampler.signaling_url` | stringProp | Yes | _(none)_ |
| `WebRTCSampler.signaling_type` | stringProp | No | `HTTP_POST` |
| `WebRTCSampler.stream_id` | stringProp | Yes | _(none)_ |
| `WebRTCSampler.listen_duration` | intProp | No | `30000` |
| `WebRTCSampler.ice_servers` | stringProp | No | _(empty)_ |
| `WebRTCSampler.sdp_offer_timeout` | intProp | No | `5000` |
| `WebRTCSampler.dtls_timeout` | intProp | No | `10000` |
| `WebRTCSampler.media_type` | stringProp | No | `AUDIO_VIDEO` |
| `WebRTCSampler.report_rtp_stats` | boolProp | No | `true` |

### UI Schema (Zod)

```typescript
export const webRtcSamplerSchema = z.object({
  signaling_url: z.string().min(1, 'Signaling URL is required').url(),
  signaling_type: z.enum(['HTTP_POST', 'WEBSOCKET']),
  stream_id: z.string().min(1, 'Stream ID is required'),
  listen_duration: z.number().int().min(1000).max(3600000),
  ice_servers: z.string(),
  sdp_offer_timeout: z.number().int().min(1000).max(60000),
  dtls_timeout: z.number().int().min(1000).max(60000),
  media_type: z.enum(['AUDIO', 'VIDEO', 'AUDIO_VIDEO']),
  report_rtp_stats: z.boolean(),
});

export const webRtcSamplerDefaults = {
  signaling_url: '',
  signaling_type: 'HTTP_POST',
  stream_id: '',
  listen_duration: 30000,
  ice_servers: '',
  sdp_offer_timeout: 5000,
  dtls_timeout: 10000,
  media_type: 'AUDIO_VIDEO',
  report_rtp_stats: true,
};
```

---

## 8. DASHSampler

```xml
<DASHSampler guiclass="DASHSamplerGui" testclass="DASHSampler"
             testname="DASH Playback" enabled="true">
  <stringProp name="DASHSampler.mpd_url">http://localhost:8083/dash/manifest.mpd</stringProp>
  <stringProp name="DASHSampler.adaptation_set">HIGHEST_BANDWIDTH</stringProp>
  <intProp name="DASHSampler.specific_bandwidth">0</intProp>
  <intProp name="DASHSampler.segment_count">3</intProp>
  <intProp name="DASHSampler.segment_timeout">10000</intProp>
  <intProp name="DASHSampler.connect_timeout">5000</intProp>
</DASHSampler>
```

### Property Reference

| Property Name | Type | Required | Default |
|---------------|------|----------|---------|
| `DASHSampler.mpd_url` | stringProp | Yes | _(none)_ |
| `DASHSampler.adaptation_set` | stringProp | No | `HIGHEST_BANDWIDTH` |
| `DASHSampler.specific_bandwidth` | intProp | No | `0` |
| `DASHSampler.segment_count` | intProp | No | `3` |
| `DASHSampler.segment_timeout` | intProp | No | `10000` |
| `DASHSampler.connect_timeout` | intProp | No | `5000` |

### UI Schema (Zod)

```typescript
export const dashSamplerSchema = z.object({
  mpd_url: z.string().min(1, 'MPD URL is required').url(),
  adaptation_set: z.enum(['HIGHEST_BANDWIDTH', 'LOWEST_BANDWIDTH', 'SPECIFIC']),
  specific_bandwidth: z.number().int().min(0),
  segment_count: z.number().int().min(1).max(1000),
  segment_timeout: z.number().int().min(1000).max(300000),
  connect_timeout: z.number().int().min(0).max(300000),
});

export const dashSamplerDefaults = {
  mpd_url: '',
  adaptation_set: 'HIGHEST_BANDWIDTH',
  specific_bandwidth: 0,
  segment_count: 3,
  segment_timeout: 10000,
  connect_timeout: 5000,
};
```

---

## 9. Schema Registry Extension

The web-ui `schemaRegistry` (in `src/components/PropertyPanel/schemas/index.ts`) must
be extended to include all new sampler types:

```typescript
import { webSocketSamplerSchema, webSocketSamplerDefaults } from './webSocketSamplerSchema.js';
import { hlsSamplerSchema, hlsSamplerDefaults } from './hlsSamplerSchema.js';
import { sseSamplerSchema, sseSamplerDefaults } from './sseSamplerSchema.js';
import { grpcSamplerSchema, grpcSamplerDefaults } from './grpcSamplerSchema.js';
import { mqttSamplerSchema, mqttSamplerDefaults } from './mqttSamplerSchema.js';
import { webRtcSamplerSchema, webRtcSamplerDefaults } from './webRtcSamplerSchema.js';
import { dashSamplerSchema, dashSamplerDefaults } from './dashSamplerSchema.js';

export const schemaRegistry: Record<string, SchemaEntry> = {
  // ... existing entries ...

  WebSocketSampler: {
    schema: webSocketSamplerSchema,
    defaults: webSocketSamplerDefaults as Record<string, unknown>,
  },
  HLSSampler: {
    schema: hlsSamplerSchema,
    defaults: hlsSamplerDefaults as Record<string, unknown>,
  },
  SSESampler: {
    schema: sseSamplerSchema,
    defaults: sseSamplerDefaults as Record<string, unknown>,
  },
  GrpcSampler: {
    schema: grpcSamplerSchema,
    defaults: grpcSamplerDefaults as Record<string, unknown>,
  },
  MQTTSampler: {
    schema: mqttSamplerSchema,
    defaults: mqttSamplerDefaults as Record<string, unknown>,
  },
  WebRTCSampler: {
    schema: webRtcSamplerSchema,
    defaults: webRtcSamplerDefaults as Record<string, unknown>,
  },
  DASHSampler: {
    schema: dashSamplerSchema,
    defaults: dashSamplerDefaults as Record<string, unknown>,
  },
};
```

---

## 10. JMX Round-Trip Compatibility

All new sampler elements must survive a JMX parse-serialize round trip. The existing
`JmxTreeWalker.parse()` and `JmxWriter.write()` pipeline handles arbitrary element
tag names and property types without code changes, because it uses a generic parse
strategy based on `testclass` attribute + typed prop child elements.

Validation: the existing `jmxRoundTrip.test.ts` test in the web-ui must be extended
with test plans containing each new sampler type to verify that:

1. Upload of a JMX with `WebSocketSampler` produces the correct `PlanNode.testClass`.
2. The property panel renders the correct form fields from the Zod schema.
3. Saving the plan back to JMX preserves all properties verbatim.

---

## 11. Complete Multi-Protocol Test Plan Example

```xml
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2" properties="5.0">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan"
              testname="Multi-Protocol Load Test" enabled="true">
      <boolProp name="TestPlan.functional_mode">false</boolProp>
    </TestPlan>
    <hashTree>

      <!-- Thread Group 1: HTTP + WebSocket -->
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup"
                   testname="HTTP + WS Users" enabled="true">
        <intProp name="ThreadGroup.num_threads">10</intProp>
        <intProp name="ThreadGroup.ramp_time">5</intProp>
        <elementProp name="ThreadGroup.main_controller" elementType="LoopController"
                     testclass="LoopController">
          <intProp name="LoopController.loops">5</intProp>
        </elementProp>
      </ThreadGroup>
      <hashTree>
        <!-- Step 1: HTTP Login -->
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy"
                          testname="HTTP Login" enabled="true">
          <stringProp name="HTTPSampler.domain">mock-http</stringProp>
          <intProp name="HTTPSampler.port">8081</intProp>
          <stringProp name="HTTPSampler.path">/api/login</stringProp>
          <stringProp name="HTTPSampler.method">POST</stringProp>
          <stringProp name="HTTPSampler.protocol">http</stringProp>
        </HTTPSamplerProxy>
        <hashTree>
          <JSONPostProcessor guiclass="JSONPostProcessorGui"
                             testclass="JSONPostProcessor"
                             testname="Extract Token" enabled="true">
            <stringProp name="JSONPostProcessor.jsonPathExprs">$.token</stringProp>
            <stringProp name="JSONPostProcessor.referenceNames">auth_token</stringProp>
          </JSONPostProcessor>
          <hashTree/>
        </hashTree>

        <!-- Step 2: WebSocket Subscribe -->
        <WebSocketSampler guiclass="WebSocketSamplerGui" testclass="WebSocketSampler"
                          testname="WS Subscribe" enabled="true">
          <stringProp name="WebSocketSampler.url">ws://mock-websocket:8082/ws</stringProp>
          <stringProp name="WebSocketSampler.message">{"subscribe":"updates"}</stringProp>
          <intProp name="WebSocketSampler.expected_messages">3</intProp>
          <stringProp name="WebSocketSampler.connection_id">main-ws</stringProp>
        </WebSocketSampler>
        <hashTree/>

        <!-- Step 3: SSE Listen -->
        <SSESampler guiclass="SSESamplerGui" testclass="SSESampler"
                    testname="SSE Updates" enabled="true">
          <stringProp name="SSESampler.url">http://mock-sse:8084/events</stringProp>
          <intProp name="SSESampler.listen_duration">5000</intProp>
          <stringProp name="SSESampler.event_filter">update</stringProp>
        </SSESampler>
        <hashTree/>
      </hashTree>

      <!-- Thread Group 2: gRPC -->
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup"
                   testname="gRPC Users" enabled="true">
        <intProp name="ThreadGroup.num_threads">5</intProp>
        <elementProp name="ThreadGroup.main_controller" elementType="LoopController"
                     testclass="LoopController">
          <intProp name="LoopController.loops">10</intProp>
        </elementProp>
      </ThreadGroup>
      <hashTree>
        <GrpcSampler guiclass="GrpcSamplerGui" testclass="GrpcSampler"
                     testname="gRPC GetUser" enabled="true">
          <stringProp name="GrpcSampler.host">mock-grpc</stringProp>
          <intProp name="GrpcSampler.port">50051</intProp>
          <stringProp name="GrpcSampler.service">test.TestService</stringProp>
          <stringProp name="GrpcSampler.method">GetUser</stringProp>
          <stringProp name="GrpcSampler.request_json">{"id": 1}</stringProp>
        </GrpcSampler>
        <hashTree/>
      </hashTree>

      <!-- Thread Group 3: MQTT Pub/Sub -->
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup"
                   testname="MQTT Users" enabled="true">
        <intProp name="ThreadGroup.num_threads">5</intProp>
        <elementProp name="ThreadGroup.main_controller" elementType="LoopController"
                     testclass="LoopController">
          <intProp name="LoopController.loops">10</intProp>
        </elementProp>
      </ThreadGroup>
      <hashTree>
        <MQTTSampler guiclass="MQTTSamplerGui" testclass="MQTTSampler"
                     testname="MQTT Connect" enabled="true">
          <stringProp name="MQTTSampler.broker_url">tcp://mock-mqtt:1883</stringProp>
          <stringProp name="MQTTSampler.action">CONNECT</stringProp>
          <stringProp name="MQTTSampler.topic">test/data</stringProp>
          <stringProp name="MQTTSampler.connection_id">mqtt-conn</stringProp>
        </MQTTSampler>
        <hashTree/>

        <MQTTSampler guiclass="MQTTSamplerGui" testclass="MQTTSampler"
                     testname="MQTT Publish" enabled="true">
          <stringProp name="MQTTSampler.broker_url">tcp://mock-mqtt:1883</stringProp>
          <stringProp name="MQTTSampler.action">PUBLISH</stringProp>
          <stringProp name="MQTTSampler.topic">test/data</stringProp>
          <stringProp name="MQTTSampler.message">{"sensor": "temp", "value": 22.5}</stringProp>
          <stringProp name="MQTTSampler.qos">1</stringProp>
          <stringProp name="MQTTSampler.connection_id">mqtt-conn</stringProp>
        </MQTTSampler>
        <hashTree/>
      </hashTree>

    </hashTree>
  </hashTree>
</jmeterTestPlan>
```
