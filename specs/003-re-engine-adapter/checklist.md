# Quality Checklist: Engine Adapter (JMeter Integration Layer)

**Domain**: 003-re-engine-adapter
**Created**: 2026-03-29
**Spec**: [spec.md](spec.md)

**Purpose**: Validate requirements quality before planning phase.

---

## Source Evidence Quality

- [x] CHK-003-001 - All functional requirements backed by source references (JmxParser, XStreamSecurityPolicy, Hc5HttpClientFactory, CLI) [Traceability]
- [x] CHK-003-002 - Source:line or class references accurate (e.g., `JmxParser.java`, `XStreamSecurityPolicy.java`, `B3MeterCli.java`) [Accuracy]
- [x] CHK-003-003 - "Source Evidence" section present for all 8 user stories [Completeness]

## Requirements Completeness

- [x] CHK-003-004 - All 8 user stories have acceptance scenarios (JMX parse, execution, HTTP/2, XStream security, CLI, isolation, schema, HTML report) [Completeness]
- [x] CHK-003-005 - Two-parser architecture documented: `JmxParser` (XStream, execution) vs `JmxTreeWalker` (StAX, import validation) — FR-003.001b [Clarity]
- [x] CHK-003-006 - Complete sampler list documented: 78 executors across 8 categories (FR-003.004 updated from "50+" to full categorized list) [Coverage]
- [x] CHK-003-007 - CLI exit codes defined: 0=success, 1=SLA breach, 2=error (FR-003.011) [Measurability]

## Entity Definitions

- [x] CHK-003-008 - `ComponentSchema` defined with attributes [Completeness]
- [x] CHK-003-009 - `JmxParseException` type documented with error context [Completeness]
- [ ] CHK-003-010 - `SamplerExecutor` base class interface not documented — what methods must each executor implement? [Completeness]

## Edge Cases & Error Handling

- [x] CHK-003-011 - JMX with unknown element types: WARN log + skip (graceful degradation) [Coverage]
- [x] CHK-003-012 - XStream CVE mitigation: explicit allowlist blocking all non-JMeter classes [Edge Cases]
- [x] CHK-003-013 - HTML report on empty run: must not NPE [Edge Cases]

## Clarification Items

- [x] CHK-003-014 - No [NEEDS CLARIFICATION] items in this spec [Clarity]
- [x] CHK-003-015 - Two-parser distinction explained; no residual ambiguity [Completeness]

## Domain Dependencies

- [x] CHK-003-016 - Upstream dependencies listed (002-re-engine-service) [Completeness]
- [x] CHK-003-017 - Integration points with 005 (web-api imports JMX via JmxTreeWalker) documented [Coverage]

## Domain-Specific Items (JMeter Integration Security)

- [x] CHK-003-018 - XStream deserialization attack vector documented with mitigation (allowlist policy) [Security]
- [x] CHK-003-019 - Multi-tenant isolation test referenced (ConcurrentRunIsolationTest) [Completeness]
- [ ] CHK-003-020 - HC4 vs HC5 selection mechanism: how is the factory chosen per-plan? Config key not specified (FR-003.008 mentions "interface" but not selection logic) [Clarity]
- [ ] CHK-003-021 - `LegacyPropertyBridge` (FR-003.012) — which jmeter.properties keys are translated? No mapping table documented. [Completeness]
- [ ] CHK-003-022 - `NoOpUIBridge` (FR-003.013) — what JMeter UI calls are silenced? No list of intercepted methods. [Completeness]

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
> - CHK-003-010: `SamplerExecutor` base interface contract not documented
> - CHK-003-020: HC4 vs HC5 factory selection mechanism unclear
> - CHK-003-021: `LegacyPropertyBridge` key mapping table missing
> - CHK-003-022: `NoOpUIBridge` intercepted methods not listed
