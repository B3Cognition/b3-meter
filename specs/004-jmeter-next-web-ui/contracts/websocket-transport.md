# WebSocket Transport — Wire Protocol

## Subprotocol: `jmeter-worker/v1`

## Overview

The WebSocket transport is the fallback communication channel for distributed mode
when gRPC is unavailable (e.g. HTTP/2 is blocked by a corporate proxy). The
controller node acts as the WebSocket server; each worker node connects as a client.

---

## Frame Format

Each WebSocket message is a **binary frame** with the following layout:

| Offset | Length | Type               | Description                              |
|--------|--------|--------------------|------------------------------------------|
| 0–3    | 4      | big-endian uint32  | Total message length (including header)  |
| 4–7    | 4      | big-endian uint32  | Message type identifier (see below)      |
| 8+     | var    | Protobuf bytes     | Serialised message body (same `.proto` types as gRPC) |

### Message Types

| Value | Name                | Direction           | Description                                     |
|-------|---------------------|---------------------|-------------------------------------------------|
| 1     | `ConfigureRequest`  | Controller → Worker | Send test plan and run parameters               |
| 2     | `ConfigureAck`      | Worker → Controller | Worker acknowledges configuration               |
| 3     | `StartRequest`      | Controller → Worker | Instruct worker to begin test execution         |
| 4     | `StartAck`          | Worker → Controller | Worker confirms test has started                |
| 5     | `StopRequest`       | Controller → Worker | Instruct worker to halt execution               |
| 6     | `StopAck`           | Worker → Controller | Worker confirms test has stopped                |
| 7     | `SampleResultBatch` | Worker → Controller | Stream of aggregated 1-second sample buckets    |
| 8     | `HealthRequest`     | Controller → Worker | Liveness probe                                  |
| 9     | `HealthStatus`      | Worker → Controller | Response to health probe (includes worker state)|

---

## Negotiation

The worker initiates an HTTP Upgrade request with the subprotocol header:

```
GET /worker-ws HTTP/1.1
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Protocol: jmeter-worker/v1
```

The server **must** respond with the matching protocol or reject the connection:

- **Accept**: `101 Switching Protocols` with `Sec-WebSocket-Protocol: jmeter-worker/v1`
- **Reject**: `400 Bad Request` if the subprotocol is not supported

A connection without the correct `Sec-WebSocket-Protocol` header must be refused.

---

## Session Lifecycle

```
Worker                             Controller
  |                                    |
  |--- HTTP Upgrade (WS handshake) --->|
  |<-- 101 Switching Protocols --------|
  |                                    |
  |<-- ConfigureRequest (type=1) ------|
  |--- ConfigureAck (type=2) --------->|
  |                                    |
  |<-- StartRequest (type=3) ----------|
  |--- StartAck (type=4) ------------->|
  |                                    |
  |--- SampleResultBatch (type=7) ---->|  (repeating)
  |--- SampleResultBatch (type=7) ---->|
  |                                    |
  |<-- HealthRequest (type=8) ---------|  (periodic)
  |--- HealthStatus (type=9) --------->|
  |                                    |
  |<-- StopRequest (type=5) -----------|
  |--- StopAck (type=6) -------------->|
  |                                    |
  |<-- (server closes connection) -----|
```

---

## Reconnection

Workers must attempt reconnection using **exponential backoff**:

| Attempt | Delay before retry |
|---------|--------------------|
| 1       | 1 s                |
| 2       | 2 s                |
| 3       | 4 s                |
| 4       | 8 s                |
| 5       | 16 s               |
| 6+      | 30 s (max)         |

On a successful reconnect the worker must:

1. Send a `ConfigureRequest` (type 1) to re-sync state with the controller.
2. Wait for a `ConfigureAck` (type 2) before resuming sample publication.

If the test is still running on the worker side (e.g. the worker survived but the
network was interrupted), the worker continues executing and buffers `SampleResultBatch`
frames internally until the connection is re-established.

---

## Protobuf Schema

The body of each frame uses the same `.proto` message definitions as the gRPC service
(see `modules/worker-proto/`). No new message types are introduced; the wire format
differs only in the binary framing header above.

---

## Error Handling

- Frames with an unknown message type (values not in the table above) must be
  silently discarded; the connection must not be closed.
- A frame whose declared length in bytes 0–3 does not match the actual WebSocket
  message size must cause the receiver to close the connection with status code
  `1002 (Protocol Error)`.
- The controller must send a `StopRequest` before closing the connection gracefully.
  Abnormal closure (e.g. network failure) is handled by the reconnection policy above.
