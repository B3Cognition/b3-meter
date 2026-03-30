# Quality Checklist: Distributed Controller

**Domain**: 004-re-distributed-controller
**Created**: 2026-03-29
**Spec**: [spec.md](spec.md)

**Purpose**: Validate requirements quality before planning phase.

---

## Source Evidence Quality

- [x] CHK-004-001 - All functional requirements backed by source references (DistributedRunService, WorkerHealthPoller with constants, DnsWorkerDiscovery) [Traceability]
- [x] CHK-004-002 - Source:line references accurate (e.g., `DistributedRunService.java:87`, `DistributedRunService.java:135`) [Accuracy]
- [x] CHK-004-003 - "Source Evidence" section present for all 6 user stories [Completeness]

## Requirements Completeness

- [x] CHK-004-004 - All 6 user stories have acceptance scenarios (start distributed, aggregate results, health polling, DNS discovery, transport selection, graceful stop) [Completeness]
- [x] CHK-004-005 - VU distribution algorithm specified: integer division with remainder to first worker (FR-004.002) [Clarity]
- [x] CHK-004-006 - Coordinated start delay: 5000ms hardcoded (`COORDINATED_START_DELAY_MS`) documented [Coverage]
- [x] CHK-004-007 - Health poll constants documented: `POLL_INTERVAL_MS=5000`, `MAX_MISSED_HEARTBEATS=3` (15s total to unavailable) [Measurability]

## Entity Definitions

- [x] CHK-004-008 - `WorkerClient` defined with attributes and key methods [Completeness]
- [x] CHK-004-009 - `DistributionMode` enum values documented (LOCAL, DISTRIBUTED) [Completeness]
- [ ] CHK-004-010 - `WorkerEndpoint` entity not documented (returned by DnsWorkerDiscovery) — host, port, and metadata fields missing [Completeness]

## Edge Cases & Error Handling

- [x] CHK-004-011 - Empty `workerIds` → `IllegalArgumentException` documented [Coverage]
- [x] CHK-004-012 - Worker not in registry → `IllegalArgumentException` documented [Edge Cases]
- [x] CHK-004-013 - Configure rollback on failure: `StopNow` sent to all previously-configured workers [Edge Cases]

## Clarification Items

- [x] CHK-004-014 - No [NEEDS CLARIFICATION] items remain (poll interval and threshold resolved by validation pass) [Clarity]
- [x] CHK-004-015 - DNS discovery refresh interval clarified: fully configurable, no hardcoded default [Completeness]

## Domain Dependencies

- [x] CHK-004-016 - Upstream dependencies listed (001-re-worker-proto, 002-re-engine-service) [Completeness]
- [x] CHK-004-017 - Integration with 005 (web-api calls distributed controller) noted [Coverage]

## Domain-Specific Items (Distributed Coordination)

- [x] CHK-004-018 - ±100ms clock skew tolerance for coordinated start documented (SC-004.001, FR-004.001) [Measurability]
- [x] CHK-004-019 - HDR histogram merge preferred over p-value averaging (FR-004.005) [Correctness]
- [ ] CHK-004-020 - Circuit breaker recovery: how does a worker transition from OPEN back to CLOSED? Half-open probe mechanism not documented. US-004.3 says "eventually closed" without specifics. [Completeness]
- [ ] CHK-004-021 - WebSocket transport reconnect logic (`handleDisconnect` found in source): behavior during an active run undefined in spec [Edge Cases]
- [ ] CHK-004-022 - What is the behavior if the `COORDINATED_START_DELAY_MS` elapses and some workers have not yet confirmed Configure acceptance? [Edge Cases]

---

## Review Summary

| Category | Items | Checked |
|----------|-------|---------|
| Source Evidence | 3 | 3/3 |
| Requirements Completeness | 4 | 4/4 |
| Entity Definitions | 3 | 2/3 |
| Edge Cases | 3 | 3/3 |
| Clarification Items | 2 | 2/2 |
| Domain Dependencies | 2 | 2/2 |
| Domain-Specific | 5 | 2/5 |
| **Total** | **22** | **18/22** |

**Reviewer**: _______________
**Date**: _______________
**Status**: [ ] Approved [ ] Needs Revision

> Outstanding:
> - CHK-004-010: `WorkerEndpoint` entity definition missing
> - CHK-004-020: Circuit breaker recovery (OPEN → half-open → CLOSED) mechanism unspecified
> - CHK-004-021: WebSocket reconnect behavior during active run
> - CHK-004-022: Configure timeout before coordinated start
