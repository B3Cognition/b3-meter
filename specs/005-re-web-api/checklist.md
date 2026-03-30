# Quality Checklist: Web API (REST Backend)

**Domain**: 005-re-web-api
**Created**: 2026-03-29
**Spec**: [spec.md](spec.md)

**Purpose**: Validate requirements quality before planning phase.

---

## Source Evidence Quality

- [x] CHK-005-001 - All functional requirements backed by source references (10 controllers documented with file paths) [Traceability]
- [x] CHK-005-002 - Source:line references accurate (e.g., `JwtTokenService.java:39`, `TestRunController.java:50-58`) [Accuracy]
- [x] CHK-005-003 - "Source Evidence" section present for all 9 user stories [Completeness]

## Requirements Completeness

- [x] CHK-005-004 - All 9 user stories have acceptance scenarios (start run, poll status, SSE, JWT auth, CRUD, worker mgmt, rate limiting, SSRF, proxy recorder) [Completeness]
- [x] CHK-005-005 - JWT specifics documented: RS256 signing, 15-min TTL, in-memory key pair, `ACCESS_TOKEN_TTL_MS=900_000` (FR-005.006–007) [Clarity]
- [x] CHK-005-006 - Resource quota defaults documented: 3 concurrent runs, 10000 VUs, 4h max (FR-005.010 updated) [Coverage]
- [x] CHK-005-007 - JMX import size limit documented: 50 MB (`MAX_JMX_SIZE_BYTES`) [Measurability]

## Entity Definitions

- [x] CHK-005-008 - `TestPlanEntity/Dto` fully defined: `id, name, ownerId, treeData, createdAt, updatedAt` (updated by validation) [Completeness]
- [x] CHK-005-009 - `TestRunEntity/Dto` defined with status enum: `PENDING/RUNNING/STOPPING/STOPPED/ERROR` [Completeness]
- [x] CHK-005-010 - `UserEntity` defined with field constraints (unique username, BCrypt hash, role enum ADMIN/USER) [Clarity]

## Edge Cases & Error Handling

- [x] CHK-005-011 - Single-user desktop mode (`singleUserMode=true`) documented [Coverage]
- [x] CHK-005-012 - In-memory JWT key: tokens invalid after restart documented [Edge Cases]
- [x] CHK-005-013 - SSRF blocked CIDRs documented: 10/8, 172.16/12, 192.168/16, 169.254/16, 127/8, IPv6 blocked, fail-closed on DNS failure [Edge Cases]

## Clarification Items

- [x] CHK-005-014 - All [NEEDS CLARIFICATION] items resolved (soft-delete, localhost SSRF blocking — both resolved by validation pass) [Clarity]
- [x] CHK-005-015 - Soft-delete behavior confirmed: no active-run guard at delete time [Completeness]

## Domain Dependencies

- [x] CHK-005-016 - Upstream dependencies listed (002, 003, 004) [Completeness]
- [x] CHK-005-017 - Integration point with 006 (web-ui consumes these endpoints) documented [Coverage]

## Domain-Specific Items (Spring Boot Security)

- [x] CHK-005-018 - Plan restore endpoint documented (POST /plans/{id}/restore/{rev}) as acceptance scenario [Completeness]
- [x] CHK-005-019 - SSE concurrency requirement documented (FR-005.012: multiple subscribers for same run) [Completeness]
- [ ] CHK-005-020 - Refresh token mechanism: spec mentions "refresh token cookie" but no TTL, rotation policy, or storage mechanism for refresh tokens documented [Completeness]
- [ ] CHK-005-021 - Rate limiter configuration: per-client, per-endpoint, or global? What is the default threshold N and cooldown period? (FR-005 mentions 429 but no specifics) [Clarity]
- [ ] CHK-005-022 - `TestPlanRevisionEntity` structure not defined in spec — what fields does it contain? (referenced in US-005.5 but no entity table) [Completeness]
- [ ] CHK-005-023 - Proxy recorder port conflict handling: what if the configured port is already in use? No error scenario documented. [Edge Cases]
- [ ] CHK-005-024 - `DELETE /api/v1/plans/{planId}` behavior when plan has associated run history — run records orphaned? Documented as soft-delete with no guard, but run history impact undefined. [Edge Cases]

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
| Domain-Specific | 7 | 2/7 |
| **Total** | **24** | **19/24** |

**Reviewer**: _______________
**Date**: _______________
**Status**: [ ] Approved [ ] Needs Revision

> Outstanding:
> - CHK-005-020: Refresh token TTL, rotation, and storage policy
> - CHK-005-021: Rate limiter threshold and cooldown specifics
> - CHK-005-022: `TestPlanRevisionEntity` entity table missing
> - CHK-005-023: Proxy recorder port conflict handling
> - CHK-005-024: Soft-delete impact on associated run history
