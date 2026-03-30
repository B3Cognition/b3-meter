# Quality Checklist: Engine Service (Core Execution Layer)

**Domain**: 002-re-engine-service
**Created**: 2026-03-29
**Spec**: [spec.md](spec.md)

**Purpose**: Validate requirements quality before planning phase.

---

## Source Evidence Quality

- [x] CHK-002-001 - All functional requirements backed by source file references (EngineService.java, ArrivalRateExecutor.java, SlaEvaluator.java with line numbers) [Traceability]
- [x] CHK-002-002 - Source:line references are accurate (e.g., `ArrivalRateExecutor.java:62-65`, `SlaEvaluator.java:41-72`) [Accuracy]
- [x] CHK-002-003 - "Source Evidence" section present for all 7 user stories [Completeness]

## Requirements Completeness

- [x] CHK-002-004 - All 7 user stories have acceptance scenarios covering both happy path and error cases [Completeness]
- [x] CHK-002-005 - `startRun` duplicate detection documented (IllegalStateException on same runId) [Clarity]
- [x] CHK-002-006 - Non-functional: Framework Independence (FR-002.013–014) — Constitution Principle I enforced [Coverage]
- [x] CHK-002-007 - VU pool shutdown grace period documented (30s await, then force) [Measurability]

## Entity Definitions

- [x] CHK-002-008 - `TestRunContext` defined with all fields and constraints [Completeness]
- [x] CHK-002-009 - `SampleBucket` defined with typed fields and constraints (sampleCount >= 0, errorPercent 0–100) [Completeness]
- [x] CHK-002-010 - `ExecutorType` enum values documented [Clarity]

## Edge Cases & Error Handling

- [x] CHK-002-011 - Dropped iterations at max VU capacity: logged at WARN level, every 1st and 100th occurrence [Coverage]
- [x] CHK-002-012 - VU pool shutdown: 30s grace then forced [Edge Cases]
- [ ] CHK-002-013 - [H1] US-002.7 coordinated omission "adjust reported latency" behavior is vague — `CoordinatedOmissionDetector.java` behavior not confirmed [NEEDS CLARIFICATION]

## Clarification Items

- [ ] CHK-002-014 - H1: US-002.7 "adjust reported latency" needs clarification: does detector add synthetic samples, adjust timings, or only log? [Clarity]
- [x] CHK-002-015 - All other ambiguities resolved by validation pass (JWT edge case removed, placed in correct spec) [Completeness]

## Domain Dependencies

- [x] CHK-002-016 - Upstream dependencies listed (001-re-worker-proto) [Completeness]
- [x] CHK-002-017 - Downstream consumers documented (003 implements; 004 via gRPC; 005 via Spring injection) [Coverage]

## Domain-Specific Items (Framework-Free Compliance)

- [x] CHK-002-018 - FR-002.013: No Spring imports verified (architectural assertion documented with build-time verification) [Compliance]
- [x] CHK-002-019 - FR-002.009: ReentrantLock requirement (not synchronized) documented with JEP-491 rationale [Compliance]
- [x] CHK-002-020 - HdrHistogram accuracy requirement: ±1% compared to exact sort (SC-002.005) [Measurability]
- [ ] CHK-002-021 - What happens when `EngineService` cannot allocate virtual threads (thread limit exceeded)? No error scenario defined. [Edge Cases]
- [ ] CHK-002-022 - SLA verdict lifecycle: once VIOLATED, can it reset to PASS within the same run? Not documented. [Completeness]

---

## Review Summary

| Category | Items | Checked |
|----------|-------|---------|
| Source Evidence | 3 | 3/3 |
| Requirements Completeness | 4 | 4/4 |
| Entity Definitions | 3 | 3/3 |
| Edge Cases | 3 | 2/3 |
| Clarification Items | 2 | 1/2 |
| Domain Dependencies | 2 | 2/2 |
| Domain-Specific | 5 | 3/5 |
| **Total** | **22** | **18/22** |

**Reviewer**: _______________
**Date**: _______________
**Status**: [ ] Approved [ ] Needs Revision

> Outstanding:
> - CHK-002-013/014: H1 coordinated omission behavior needs clarification (read `CoordinatedOmissionDetector.java`)
> - CHK-002-021: Virtual thread allocation failure scenario
> - CHK-002-022: SLA verdict reset behavior within a run
