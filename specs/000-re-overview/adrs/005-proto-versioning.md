# ADR-005: Protocol Buffer Versioning Strategy

**Status**: Proposed — [REQUIRES INPUT: decision needed]
**Date**: 2026-03-29
**Deciders**: [REQUIRES INPUT: project owner + contributors]

## Context

`worker.proto` defines the gRPC contract between the distributed controller and workers. Since jMeter Next is being released as OSS, the community may want to evolve this protocol (add new fields, new RPCs, or deprecate existing ones). A versioning strategy is needed to prevent breaking changes from silently breaking deployments where controller and worker versions differ.

This ADR was flagged by CHK-001-020 (proto versioning strategy not documented).

## Decision Drivers

- Backward compatibility: Rolling deployments where controller and worker are different versions must not fail silently
- OSS evolution: Community contributions may need to add fields or RPCs
- Breaking changes must be explicit: Removing fields, changing types, or reordering field numbers breaks wire format
- Simplicity: Strategy should be simple for contributors to understand

## Considered Options

### Option 1: Proto3 Reserved Fields + Changelog

Use protobuf `reserved` keyword to mark deprecated fields. Maintain a `PROTO_CHANGELOG.md` documenting all field additions/removals. Controller code handles missing optional fields with defaults.

**Pros**:
- Simple; no version negotiation needed
- Proto3 is already backward-compatible for added optional fields
- Changelog provides audit trail

**Cons**:
- No enforcement of compatibility at runtime
- Easy to accidentally break by changing a field number or type

### Option 2: Package-Level Versioning (`worker.v1`, `worker.v2`)

When a breaking change is needed, create a new proto package (`com.b3meter.worker.v2`) with the new contract. Controller supports `v1` and `v2` simultaneously during a transition period.

**Pros**:
- Clear separation: `v1` workers continue working with `v1` controller path
- Breaking changes are explicit (new package = major version)
- Industry standard (Google API versioning)

**Cons**:
- Maintenance overhead: two parallel implementations during transition
- More complex routing in controller

### Option 3: Version field in HealthStatus

Include a `protocol_version` string in `HealthStatus`. Controller negotiates based on worker's reported version.

**Pros**:
- Runtime negotiation
- Smooth rolling deployments

**Cons**:
- Version strings are error-prone
- Adds complexity to health check path

## Decision

[REQUIRES INPUT — Choose one of the above or propose alternative]

**Recommended starting point**: Option 1 (Reserved Fields + Changelog) for the initial OSS release, with Option 2 adopted when the first breaking change is needed.

## Consequences

**If Option 1 adopted**:
- Positive: Simple, consistent with current practice
- Negative: No runtime enforcement; relies on discipline
- Required action: Create `PROTO_CHANGELOG.md`; add CI check that field numbers never change

**If Option 2 adopted**:
- Positive: Safe rolling upgrades
- Negative: More implementation work
- Required action: Create `worker.v1.proto`; update generated code paths

**Risks**:
- Without any strategy, a contributor who changes a field number or type will silently break distributed deployments

## Related

- [ADR-004-grpc-worker-protocol.md](004-grpc-worker-protocol.md)
- [001-re-worker-proto/spec.md](../../001-re-worker-proto/spec.md) — CHK-001-020
- Source: `worker.proto`
