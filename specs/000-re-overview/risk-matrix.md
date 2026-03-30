# Risk Assessment: jMeter Next OSS Release

**Generated**: 2026-03-29
**Context**: Open-source release preparation
**Related**: [migration-strategy.md](migration-strategy.md)

---

## Risk Matrix

### Severity Definitions

| Level | Impact | Description |
|-------|--------|-------------|
| Critical | 5 | Reputational damage, security incident, project failure |
| High | 4 | Significant OSS release delay, major rework |
| Medium | 3 | Notable delay, moderate rework required |
| Low | 2 | Minor cleanup, limited impact |
| Minimal | 1 | Negligible impact |

### Likelihood Definitions

| Level | Probability | Description |
|-------|-------------|-------------|
| Almost Certain | 5 | >90% — currently observed |
| Likely | 4 | 60–90% — probable without action |
| Possible | 3 | 30–60% — may occur |
| Unlikely | 2 | 10–30% — could occur |
| Rare | 1 | <10% — unlikely but noted |

---

## Technical Risks

| ID | Risk | Likelihood | Impact | Score | Mitigation |
|----|------|------------|--------|-------|------------|
| T1 | XStream deserialization attack via crafted JMX file | 2 | 5 | **10** | XStreamSecurityPolicy allowlist already in place; mitigated but must be maintained |
| T2 | CoordinatedOmission silent data distortion (H1 open item) | 3 | 4 | **12** | Clarify detector behavior; add explicit test assertions for coordinated-omission scenarios |
| T3 | PropertyPanel.tsx 1268 lines → contributor deterrent / bug surface | 4 | 3 | **12** | Document decomposition plan; add TODO comments targeting areas for split |
| T4 | JMX T014 debt: raw XML storage + future parsing inconsistency | 3 | 3 | **9** | Document the limitation clearly; add integration test for round-trip fidelity |
| T5 | Virtual thread pinning regression (synchronized vs ReentrantLock) | 2 | 4 | **8** | Enforced by constitution; add `@Test` checking for `synchronized` usage in engine-service |
| T6 | In-memory JWT key: all sessions lost on restart | 4 | 2 | **8** | Acceptable for single-node desktop mode; document limitation explicitly for multi-instance users |
| T7 | SSRF bypass via DNS rebinding (attacker controls DNS TTL) | 2 | 4 | **8** | Fail-closed on DNS failure already implemented; document as known limitation |
| T8 | gRPC breaking change cascades to all downstream domains | 2 | 5 | **10** | Proto versioning ADR required; backward-compat test suite for proto changes |
| T9 | Worker circuit breaker recovery not specified (CHK-004-020) | 3 | 3 | **9** | Document recovery mechanism; add test for OPEN → half-open → CLOSED transition |

## Organizational / OSS Risks

| ID | Risk | Likelihood | Impact | Score | Mitigation |
|----|------|------------|--------|-------|------------|
| O1 | Single contributor → bus factor 1 | 5 | 4 | **20** | Comprehensive docs, ADRs, specs; onboarding guide; open issues for good-first-issue |
| O2 | Testimonial placeholder left in README when public | 4 | 3 | **12** | Wave 0 blocker — fix before any public commit |
| O3 | `.claude/` internal AI tooling exposed in public repo | 4 | 3 | **12** | Wave 0 blocker — add to `.gitignore` before public |
| O4 | External contributors modifying engine-service with Spring imports (breaking Constitution Principle I) | 3 | 4 | **12** | CI Checkstyle rule enforcing no Spring imports in engine-service |
| O5 | Contributor adds `synchronized` keyword, causing VT pinning | 3 | 3 | **9** | CI lint check for `synchronized` in executor classes |
| O6 | OSS license (Apache 2.0) conflicts with JMeter dependency license | 2 | 5 | **10** | Apache JMeter is Apache 2.0 — compatible; verify all other deps in OWASP scan |

## Business / Release Risks

| ID | Risk | Likelihood | Impact | Score | Mitigation |
|----|------|------------|--------|-------|------------|
| B1 | Security vulnerability discovered post-public-release | 2 | 5 | **10** | OWASP scan in CI; security policy in SECURITY.md; GitHub security advisories |
| B2 | Feature parity gaps vs Apache JMeter discovered by users | 3 | 3 | **9** | Document known gaps; "Not Yet Supported" section in README; good-first-issues |
| B3 | Performance regression in distributed mode undiscovered | 2 | 4 | **8** | Nightly benchmark CI with regression notification |
| B4 | H2 embedded database scalability ceiling hit | 3 | 2 | **6** | Document scale limits; SQLite option available; contribution welcome |

---

## Risk Heat Map

```
        Impact →
        1    2    3    4    5
    5 │    │ T6 │ T3 │ O1 │    │
    4 │    │    │ T4 │ T2 │    │
L 3 │    │    │ O2 │ T9 │ T5 │
i   │    │    │ O3 │ O5 │ B2 │
k 2 │    │    │    │ T7 │ T1 │
e   │    │    │    │    │ T8 │
l 1 │    │    │    │    │    │
i   └────┴────┴────┴────┴────┘
h
```

**High attention zone (Score ≥ 12)**: O1, T2, T3, O2, O3, O4

---

## Domain-Specific Risks

### 001-re-worker-proto

| Risk | Score | Mitigation |
|------|-------|------------|
| Proto breaking change cascades (T8) | 10 | See ADR-001-proto-versioning |
| gRPC error code contract gaps | 4 | Document gRPC status codes per RPC |

### 002-re-engine-service

| Risk | Score | Mitigation |
|------|-------|------------|
| CoordinatedOmission silent distortion (T2) | 12 | Clarify H1; add test coverage |
| Spring import regression (O4) | 12 | Checkstyle enforced in CI |
| Virtual thread pinning (T5) | 8 | ReentrantLock enforced; lint check |

### 003-re-engine-adapter

| Risk | Score | Mitigation |
|------|-------|------------|
| XStream CVE — new classes added to JMeter without allowlist update | 10 | Review allowlist on JMeter version bumps |
| HC4/HC5 selection confusion | 6 | Document selection mechanism; deprecation plan |

### 004-re-distributed-controller

| Risk | Score | Mitigation |
|------|-------|------------|
| Circuit breaker recovery undefined (T9) | 9 | Document OPEN→CLOSED path |
| WebSocket reconnect during active run undefined | 6 | Read source; document behavior |

### 005-re-web-api

| Risk | Score | Mitigation |
|------|-------|------------|
| SSRF DNS rebinding (T7) | 8 | Document; fail-closed already in place |
| Soft-delete orphaning run history | 6 | Document intended behavior; add cascade or null FK |
| JWT key refresh on restart (T6) | 8 | Document for multi-instance users |

### 006-re-web-ui

| Risk | Score | Mitigation |
|------|-------|------------|
| PropertyPanel.tsx complexity (T3) | 12 | Decomposition plan before 2.0 |
| SLADiscovery algorithm undocumented | 6 | Document algorithm in spec + code comments |

### 007-re-test-infrastructure

| Risk | Score | Mitigation |
|------|-------|------------|
| Benchmark regression threshold undefined | 4 | Set explicit threshold in benchmark.yml |
| OWASP false positives masking real CVEs | 4 | Review suppressions file on each update |

---

## Critical Risk Response Plan

### O1 — Bus Factor 1 (Score: 20) — HIGHEST RISK

| Attribute | Value |
|-----------|-------|
| Owner | [REQUIRES INPUT: project owner] |
| Response | Mitigate |
| Trigger | OSS release; any contributor PR |
| Actions | 1. Create architecture guide; 2. Write CONTRIBUTING.md; 3. Tag 10+ good-first-issues; 4. Document all ADRs; 5. Add code comments to hotspot files |

### T2 — Coordinated Omission Silent Distortion (Score: 12)

| Attribute | Value |
|-----------|-------|
| Owner | [REQUIRES INPUT] |
| Response | Mitigate |
| Trigger | Any PR touching executor classes |
| Actions | Read `CoordinatedOmissionDetector.java`; add test assertions; document behavior in spec |

---

## Risk Monitoring Schedule

| Risk | Frequency | Method | Escalation |
|------|-----------|--------|------------|
| OWASP CVEs (B1, O6) | Every CI run | OWASP check plugin | PR blocked on critical |
| Performance regression (B3) | Nightly | `benchmark.yml` GitHub Action | GitHub notification |
| XStream allowlist (T1) | On JMeter version bump | Manual review | PR review gate |
| Constitution compliance (O4) | Every CI run | Checkstyle `engine-service` no-Spring rule | PR blocked |
| Test coverage (T2, T5) | Every CI run | JaCoCo 80% threshold | PR blocked |
