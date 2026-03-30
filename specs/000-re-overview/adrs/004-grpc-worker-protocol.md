# ADR-004: gRPC for Worker-Controller Communication

**Status**: Accepted
**Date**: 2026-03-29 (reverse-engineered from codebase)
**Deciders**: Project owner (Ladislav Bihari)

## Context

Distributed load testing requires the controller to: configure remote workers with test plans, start all workers simultaneously, receive streaming result batches, and send stop signals. The choice of communication protocol determines latency, bandwidth efficiency, and operational complexity.

Apache JMeter's original distributed mode uses Java RMI — a protocol tightly coupled to Java, not firewall-friendly, and limited to Java clients.

## Decision Drivers

- Streaming results: Workers must push continuous result batches during a test
- Coordinated start: All workers must receive a precise timestamp and start simultaneously
- Binary efficiency: Test plan (JMX bytes) and histogram data are binary payloads
- Language-agnostic: Protocol should allow future non-Java worker implementations
- Bidirectional: Controller must be able to stop workers mid-test

## Considered Options

### Option 1: gRPC + Protocol Buffers

**Pros**:
- Server-streaming RPC (`StreamResults`) for continuous result push
- Binary protocol (protobuf) — compact test plans and histograms
- Bidirectional RPCs possible
- Language-agnostic IDL: Go/Rust/Python workers could be built
- gRPC health check standard included
- Connection multiplexing over HTTP/2

**Cons**:
- HTTP/2 required — may be blocked by some enterprise proxies
- Requires proto compilation step in build
- More complex than plain REST

### Option 2: REST + WebSocket

**Pros**:
- Firewall-friendly (standard HTTP)
- WebSocket for result streaming
- Familiar to most developers

**Cons**:
- JSON is verbose for binary data (base64 overhead)
- No first-class streaming RPC model
- WebSocket reconnection handling is ad-hoc
- No IDL / schema enforcement

### Option 3: Custom TCP protocol

**Pros**:
- Maximum control

**Cons**:
- High implementation cost
- No ecosystem tools
- Firewall issues even worse than gRPC

## Decision

**Option 1: gRPC** was chosen as the primary protocol.

A WebSocket fallback transport (`WebSocketWorkerTransport.java`) was also implemented to handle environments where gRPC is blocked by proxies. Both implement the same `WorkerTransport` interface.

Source evidence: `worker.proto` defines 6 RPCs; `GrpcWorkerTransport.java` and `WebSocketWorkerTransport.java` both implement `WorkerTransport`.

## Consequences

**Positive**:
- Clean IDL-defined contract (`worker.proto`) — breaking changes are explicit
- `StreamResults` RPC naturally handles continuous result streaming
- Binary protobuf handles JMX bytes and HdrHistogram efficiently
- Future: non-Java worker implementations are possible

**Negative**:
- HTTP/2 requirement can cause issues in corporate proxy environments
- Proto versioning discipline required (see ADR-005)
- WebSocket transport requires ongoing feature parity with gRPC transport

**Risks**:
- Breaking proto changes cascade to all consumers — strict versioning needed
- WebSocket transport may lag behind gRPC transport in feature completeness

## Related

- [ADR-005-proto-versioning.md](005-proto-versioning.md)
- [constitution.md](../constitution.md)
- Source: `worker.proto`, `WorkerTransport.java`, `GrpcWorkerTransport.java`, `WebSocketWorkerTransport.java`
