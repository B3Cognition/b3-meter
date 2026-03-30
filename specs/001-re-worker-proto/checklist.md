# Quality Checklist: Worker Protocol (gRPC Wire Format)

**Domain**: 001-re-worker-proto
**Created**: 2026-03-29
**Spec**: [spec.md](spec.md)

**Purpose**: Validate requirements quality before planning phase.

---

## Source Evidence Quality

- [x] CHK-001-001 - All functional requirements backed by source file references (`worker.proto` line numbers) [Traceability]
- [x] CHK-001-002 - Source:line references are accurate and verifiable (e.g., `worker.proto:23`, `worker.proto:73-93`) [Accuracy]
- [x] CHK-001-003 - "Source Evidence" section present for all 6 user stories [Completeness]

## Requirements Completeness

- [x] CHK-001-004 - All 6 user stories have acceptance scenarios (Configure, Start, Stop, StopNow, StreamResults, GetHealth) [Completeness]
- [x] CHK-001-005 - Functional requirements cover full RPC lifecycle (6 methods, message types, state machine) [Clarity]
- [x] CHK-001-006 - Protocol backward-compatibility requirement documented (FR-001.004: percentiles map retained alongside hdr_histogram) [Coverage]
- [x] CHK-001-007 - Success criteria are measurable (SC-001.001–004 are verifiable) [Measurability]

## Entity Definitions

- [x] CHK-001-008 - `TestPlanMessage` and `SampleResultBatch` defined with typed fields and constraints [Completeness]
- [x] CHK-001-009 - `HealthStatus` entity and `HealthRequest` described in user stories; state enum documented [Completeness]
- [x] CHK-001-010 - Field constraints specified (e.g., `virtual_users >= 1`, `duration_seconds = 0` means unlimited) [Clarity]

## Edge Cases & Error Handling

- [x] CHK-001-011 - Empty `hdr_histogram` fallback to `percentiles` map documented [Coverage]
- [x] CHK-001-012 - Worker rejects Configure → rollback all previously-configured workers documented [Edge Cases]
- [x] CHK-001-013 - `start_at` in the past → immediate start behavior documented [Edge Cases]

## Clarification Items

- [x] CHK-001-014 - No [NEEDS CLARIFICATION] items remain in this spec [Clarity]
- [x] CHK-001-015 - All ambiguities resolved (N/A — spec is clean) [Completeness]

## Domain Dependencies

- [x] CHK-001-016 - Upstream dependencies clearly listed (None — foundational) [Completeness]
- [x] CHK-001-017 - Downstream consumers documented (002, 004 depend on this protocol) [Coverage]

## Domain-Specific Items

- [x] CHK-001-018 - Worker state machine transition table is complete and non-ambiguous (6 states, valid transitions listed) [Completeness]
- [x] CHK-001-019 - `SampleResultBatch.hdr_histogram` purpose and format explained (sparse RLE HdrHistogram bytes for accurate cross-worker merge) [Clarity]
- [ ] CHK-001-020 - Proto versioning strategy defined — what happens when proto changes (backward compat, deprecation policy)? [Coverage]
- [ ] CHK-001-021 - Are gRPC stream error codes (NOT_FOUND, CANCELLED, etc.) documented? The spec only covers happy path errors. [Edge Cases]

---

## Review Summary

| Category | Items | Checked |
|----------|-------|---------|
| Source Evidence | 3 | 3/3 |
| Requirements Completeness | 4 | 4/4 |
| Entity Definitions | 3 | 3/3 |
| Edge Cases | 3 | 3/3 |
| Clarification Items | 2 | 2/2 |
| Domain Dependencies | 2 | 2/2 |
| Domain-Specific | 4 | 2/4 |
| **Total** | **21** | **19/21** |

**Reviewer**: _______________
**Date**: _______________
**Status**: [ ] Approved [ ] Needs Revision

> Outstanding: Proto versioning strategy (CHK-001-020) and gRPC error codes (CHK-001-021) are gaps worth addressing before implementation.
