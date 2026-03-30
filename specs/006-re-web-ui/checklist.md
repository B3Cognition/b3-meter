# Quality Checklist: Web UI (React Frontend)

**Domain**: 006-re-web-ui
**Created**: 2026-03-29
**Spec**: [spec.md](spec.md)

**Purpose**: Validate requirements quality before planning phase.

---

## Source Evidence Quality

- [x] CHK-006-001 - All functional requirements backed by source references (component files listed with paths) [Traceability]
- [x] CHK-006-002 - Source:line references present for key hotspot file (`SLADiscovery.tsx` hotspot #1) [Accuracy]
- [x] CHK-006-003 - "Source Evidence" section present for all 9 user stories [Completeness]

## Requirements Completeness

- [x] CHK-006-004 - All 9 user stories have acceptance scenarios [Completeness]
- [x] CHK-006-005 - SSE reconnection with `Last-Event-ID` header documented in edge cases [Clarity]
- [x] CHK-006-006 - Accessibility requirement documented: WCAG 2.1 AA via automated test (FR-006.009) [Coverage]
- [x] CHK-006-007 - Dashboard 24-hour no-memory-leak requirement documented (FR-006.005) [Measurability]

## Entity Definitions

- [x] CHK-006-008 - Key API types documented: `TestPlanDto`, `TestRunDto`, `MetricsDto`, `SlaStatusDto` with fields [Completeness]
- [x] CHK-006-009 - API types reference 005-re-web-api as source of truth (note in spec) [Consistency]
- [ ] CHK-006-010 - `MetricsDto` fields not fully specified in entity table — only `samplerLabel, sampleCount, avgResponseTime, p95, p99, errorRate` listed but no types or constraints [Completeness]

## Edge Cases & Error Handling

- [x] CHK-006-011 - SSE reconnection with `Last-Event-ID` documented [Coverage]
- [x] CHK-006-012 - `PropertyPanel 1268+ lines` — complexity risk noted as candidate for splitting [Edge Cases]
- [x] CHK-006-013 - Large response body truncation in `ViewResultsTree` documented [Edge Cases]

## Clarification Items

- [ ] CHK-006-014 - [H2] US-006.2 "plan is auto-saved" — timing unclear (immediate? debounced? on blur?). Read `PropertyPanel.tsx` to resolve. [NEEDS CLARIFICATION]
- [x] CHK-006-015 - All other items clear; SLADiscovery threshold suggestion algorithm described at sufficient level for planning [Completeness]

## Domain Dependencies

- [x] CHK-006-016 - Upstream dependencies listed (005-re-web-api) [Completeness]
- [x] CHK-006-017 - SSE streaming contract with 005 documented (FR-006.004: within 1s of SSE receipt) [Coverage]

## Domain-Specific Items (React SPA Quality)

- [x] CHK-006-018 - ViewResultsTree virtualization threshold documented: > 100 samples (FR-006.006) [Measurability]
- [x] CHK-006-019 - JMX round-trip fidelity requirement documented: parse → render → export produces functionally equivalent XML (SC-006.005) [Completeness]
- [x] CHK-006-020 - JWT token refresh silent retry documented: FR-006.007 (refresh before retry), FR-006.008 (redirect on 401 after refresh failure) [Completeness]
- [ ] CHK-006-021 - ChaosLoad (US-006.9) acceptance scenario is minimal: "arrival rate follows irregular pattern" — what patterns are supported? How are they configured? No parameter spec. [Completeness]
- [ ] CHK-006-022 - A/B Performance (US-006.8): "side-by-side charts" — which metrics are compared? Time range alignment? Statistical significance shown? [Completeness]
- [ ] CHK-006-023 - SLADiscovery (US-006.6): what algorithm is used to suggest thresholds? "Based on observed distribution" — p95 of baseline? percentile-of-percentiles? [Clarity]

---

## Review Summary

| Category | Items | Checked |
|----------|-------|---------|
| Source Evidence | 3 | 3/3 |
| Requirements Completeness | 4 | 4/4 |
| Entity Definitions | 3 | 2/3 |
| Edge Cases | 3 | 3/3 |
| Clarification Items | 2 | 1/2 |
| Domain Dependencies | 2 | 2/2 |
| Domain-Specific | 6 | 3/6 |
| **Total** | **23** | **18/23** |

**Reviewer**: _______________
**Date**: _______________
**Status**: [ ] Approved [ ] Needs Revision

> Outstanding:
> - CHK-006-010: `MetricsDto` entity table incomplete (no types/constraints)
> - CHK-006-014: H2 auto-save timing in PropertyPanel needs clarification
> - CHK-006-021: ChaosLoad pattern types and configuration
> - CHK-006-022: A/B comparison metric scope and alignment
> - CHK-006-023: SLADiscovery threshold suggestion algorithm
