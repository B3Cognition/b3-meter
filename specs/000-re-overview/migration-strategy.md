# Open-Source Preparation Strategy: jMeter Next

**Generated**: 2026-03-29
**Context**: OSS release preparation (not a platform migration)
**Related**: [constitution.md](constitution.md)

> **Note**: This document uses "migration" in the 7R framework sense to classify each domain's readiness for public release, not a language/platform migration.

---

## 1. 7R Analysis by Domain

Evaluated against OSS release readiness:

| Domain | 7R Recommendation | Rationale |
|--------|-------------------|-----------|
| 001-re-worker-proto | **Retain** | Stable, well-defined gRPC protocol. Minor: add proto versioning strategy. |
| 002-re-engine-service | **Refactor** | Core logic sound. Need: clarify CoordinatedOmissionDetector behavior; SLA reset semantics. |
| 003-re-engine-adapter | **Refactor** | JMeter bridge needs documentation. Need: SamplerExecutor interface contract; HC4 deprecation plan. |
| 004-re-distributed-controller | **Refactor** | Good architecture. Need: WorkerEndpoint entity doc; CB recovery spec; WebSocket reconnect spec. |
| 005-re-web-api | **Refactor** | Spring Boot 3 REST API is modern. Need: refresh token TTL; rate limiter config; revision entity doc. |
| 006-re-web-ui | **Refactor** | React 19 + TypeScript is current. Need: PropertyPanel decomposition; SLADiscovery algorithm docs. |
| 007-re-test-infrastructure | **Retain** | Well-structured CI/CD. Minor: add Docker versioning strategy; benchmark regression threshold. |

### 7R Definitions (as applied here)

| Option | Meaning in this context |
|--------|------------------------|
| **Retain** | Ship as-is; only minor documentation needed |
| **Refactor** | Code is sound; documentation/spec gaps to fill before OSS |
| **Rebuild** | N/A — no domain requires a rewrite |
| **Retire** | N/A — no features being removed |

---

## 2. OSS Preparation Approach

### Chosen Approach: Incremental Documentation + Targeted Cleanup

This is not a big-bang release. The approach is:

1. **Address blockers** (HIGH priority OSS issues) before any public announcement
2. **Fill documentation gaps** per domain (constitution, specs, checklists)
3. **Incremental quality improvements** aligned with existing CI gates
4. **Tag OSS release** once readiness checklist passes

```text
OSS Preparation Waves:
  Wave 0 (Blockers)  → Wave 1 (Documentation) → Wave 2 (Quality) → Wave 3 (Release)
```

---

## 3. Preparation Waves

### Wave 0: Blockers (Before Any Public Commit)

**Goal**: Ensure the repository is safe and complete for public viewing.

**Exit Criteria**: All HIGH priority OSS issues resolved.

| Task | Priority | Owner | Status |
|------|----------|-------|--------|
| Replace `github.com/Testimonial/b3meter.git` in README.md:54 | HIGH | [REQUIRES INPUT] | Pending |
| Add `.claude/` to `.gitignore` | HIGH | [REQUIRES INPUT] | Pending |
| Add `.specify/squad/` to `.gitignore` | MEDIUM | [REQUIRES INPUT] | Pending |
| Decision on `.specify/extensions/` inclusion | MEDIUM | [REQUIRES INPUT] | Decision required |
| Review `.claude/CLAUDE.md` for internal references | LOW | [REQUIRES INPUT] | Pending |

### Wave 1: Specification & Documentation (Weeks 1-2)

**Goal**: All 7 domain specs are complete, validated, and reviewed.

**Exit Criteria**: All checklist items at 90%+ checked; open items documented with decisions.

| Task | Domain | Items |
|------|--------|-------|
| Clarify CoordinatedOmissionDetector behavior | 002 | H1 from validation report |
| Clarify PropertyPanel auto-save timing | 006 | H2 from validation report |
| Document `WorkerEndpoint` entity | 004 | CHK-004-010 |
| Document circuit breaker recovery (OPEN → CLOSED) | 004 | CHK-004-020 |
| Document WebSocket reconnect during active run | 004 | CHK-004-021 |
| Document refresh token TTL and rotation policy | 005 | CHK-005-020 |
| Document rate limiter defaults and config | 005 | CHK-005-021 |
| Document `TestPlanRevisionEntity` structure | 005 | CHK-005-022 |
| Document `SamplerExecutor` base interface contract | 003 | CHK-003-010 |
| Document SLADiscovery threshold suggestion algorithm | 006 | CHK-006-023 |
| Create CONTRIBUTING.md with development guide | - | OSS onboarding |
| Create architecture guide (ADRs summary) | - | OSS onboarding |

### Wave 2: Code Quality (Weeks 3-4)

**Goal**: Code quality meets OSS standards; known technical debt documented or addressed.

**Exit Criteria**: CI green, no critical OWASP CVEs, PropertyPanel decomposition plan.

| Task | Domain | Priority |
|------|--------|----------|
| Decomposition plan for PropertyPanel.tsx (1268+ lines) | 006 | Medium |
| T014: Plan for full JMX XStream parsing at import | 005/003 | Low |
| Deprecation notice for HC4 factory | 003 | Low |
| Add missing entity table: TestPlanRevisionEntity | 005 | Medium |
| Verify all Dockerfiles run as non-root | 007 | High |
| Verify OWASP scan clean (no unresolved critical CVEs) | 007 | High |
| Test coverage report: confirm 80% threshold per module | All | High |

### Wave 3: OSS Release (Week 5)

**Goal**: Public release tagged and announced.

**Exit Criteria**: All Wave 0-2 tasks complete; release tag created; GitHub org URL set.

| Task | Owner | Status |
|------|-------|--------|
| Create GitHub organization and set actual org URL | [REQUIRES INPUT] | Pending |
| Tag `v1.0.0` release | [REQUIRES INPUT] | Pending |
| Publish release notes (based on `git log`) | [REQUIRES INPUT] | Pending |
| Set up GitHub Discussions / Issues templates | [REQUIRES INPUT] | Pending |
| Configure repository topics and description | [REQUIRES INPUT] | Pending |

---

## 4. Rollback Plan

Since this is OSS preparation (not a production migration), rollback is simple:

| Scenario | Rollback Action |
|----------|-----------------|
| Proprietary reference found after public | Delete commit/PR; remove from git history if needed; re-review scan |
| Breaking API change pushed accidentally | Revert PR; update CHANGELOG with compatibility note |
| Security vulnerability discovered post-release | Create hotfix branch; patch release; security advisory via GitHub |

---

## 5. Success Metrics

| Metric | Current | Target | Measurement |
|--------|---------|--------|-------------|
| Spec coverage | 85.5% | ≥90% | Coverage report |
| OSS blockers resolved | 0/5 | 5/5 | Checklist CHK-000-OSS-* |
| Checklist completeness | 83% (avg) | ≥90% (avg) | Per-domain checklists |
| CI pass rate | [REQUIRES INPUT: check current green rate] | 100% | GitHub Actions |
| OWASP critical CVEs | [REQUIRES INPUT: run scan] | 0 | OWASP report |
| Test coverage per module | [REQUIRES INPUT: run coverage] | ≥80% | Gradle coverage report |
| [NEEDS CLARIFICATION] items | 2 | 0 | Validation report |

---

## 6. Domain Dependency Order for OSS Contributions

Contributors working on the codebase should follow this order when making cross-domain changes:

```
001-re-worker-proto
    └── 002-re-engine-service
            └── 003-re-engine-adapter
            └── 004-re-distributed-controller
                    └── 005-re-web-api
                            └── 006-re-web-ui
007-re-test-infrastructure (cross-cutting — validates all above)
```

Breaking changes to foundational domains (001, 002) require proportional updates to all downstream domains.
