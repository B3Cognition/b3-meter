# Gap Analysis: jMeter Next OSS Release

**Generated**: 2026-03-29
**Context**: Gaps to close before and after open-source release
**Related**: [constitution.md](constitution.md), [migration-strategy.md](migration-strategy.md)

---

## 1. Feature Parity Gaps vs Apache JMeter

### Features Present in jMeter Next (Confirmed)

| Feature | Implementation | Status |
|---------|----------------|--------|
| 78+ sampler/controller executors | `interpreter/` directory | ✓ Implemented |
| gRPC sampler | `GrpcSamplerExecutor.java` | ✓ Implemented |
| MQTT sampler | `MQTTSamplerExecutor.java` | ✓ Implemented |
| WebSocket sampler | `WebSocketSamplerExecutor.java` | ✓ Implemented |
| SSE sampler | `SSESamplerExecutor.java` | ✓ Implemented (beyond JMeter) |
| WebRTC sampler | `WebRTCSamplerExecutor.java` | ✓ Implemented (beyond JMeter) |
| HLS/DASH media samplers | `HLSSamplerExecutor`, `DASHSamplerExecutor` | ✓ Implemented (beyond JMeter) |
| Arrival-rate executor | `ArrivalRateExecutor.java` | ✓ Implemented (beyond JMeter) |
| Ramping arrival-rate | `RampingArrivalRateExecutor.java` | ✓ Implemented (beyond JMeter) |
| SLA monitoring | `SlaEvaluator.java` | ✓ Implemented (beyond JMeter) |
| Distributed execution | `distributed-controller` module | ✓ Implemented |
| Real-time SSE dashboard | `LiveDashboard.tsx` + `StreamingController.java` | ✓ Implemented |
| HTTP/2 (ALPN) | `Hc5HttpClientFactory.java` | ✓ Implemented |
| Proxy recorder | `ProxyRecorderService.java` | ✓ Implemented |
| JMX import/export | `TestPlanService`, `JmxTreeWalker` | ✓ Implemented |
| HTML report generation | `HtmlReportGenerator.java` | ✓ Implemented |
| CLI headless mode | `B3MeterCli.java` | ✓ Implemented |
| WCAG 2.1 AA accessibility | `accessibility.test.tsx` | ✓ Implemented (beyond JMeter) |

### Features Deferred / Partially Implemented

| Feature | Gap | Priority | Notes |
|---------|-----|----------|-------|
| JMX full XStream parsing at import | T014 — raw XML stored, not typed tree | Low | Tracked in `TestPlanService.java:20-22` comment |
| Plugin manager UI | `PluginManager.tsx` referenced in spec | Medium | UI feature — confirm implementation status |
| SLADiscovery algorithm | Algorithm undocumented | Medium | `SLADiscovery.tsx` is hotspot; algorithm needs spec |
| A/B comparison detail | Metric scope unclear | Low | `ABPerformance.tsx` — confirm what metrics shown |
| ChaosLoad patterns | Pattern types unspecified | Low | `ChaosLoad.tsx` — confirm pattern types |

### Features Beyond JMeter (Innovation)

| Feature | Domain | Contributor Value |
|---------|--------|------------------|
| Arrival-rate executors | 002 | k6-style load model |
| Real-time SLA monitoring | 002, 005 | Immediate threshold alerting |
| SLADiscovery auto-thresholds | 006 | AI-assisted baseline |
| A/B performance comparison | 006 | Release regression testing |
| ChaosLoad generation | 006 | Resilience testing |
| WebRTC/HLS/DASH/SSE samplers | 003 | Modern protocol support |

---

## 2. Documentation Gaps

| Document | Current State | Required | Priority |
|----------|---------------|----------|----------|
| CONTRIBUTING.md | Missing (or minimal) | Full guide for new contributors | **HIGH** |
| Architecture guide | Missing | ADR + module map + principles | **HIGH** |
| API reference | OpenAPI (SpringDoc auto-generated) | Confirm it's exposed and complete | Medium |
| `worker.proto` versioning policy | Missing | How to evolve proto safely | Medium |
| Deployment guide (non-Docker) | Missing | JVM requirements, config options | Medium |
| Security policy (`SECURITY.md`) | Missing | CVE reporting process | **HIGH** (OSS requirement) |
| Code of Conduct (`CODE_OF_CONDUCT.md`) | Missing | Standard contributor CoC | Medium |
| Changelog (`CHANGELOG.md`) | Missing | Release history | Medium |

---

## 3. Infrastructure Gaps

| Component | Current State | Target State | Gap | Action |
|-----------|---------------|--------------|-----|--------|
| GitHub org URL | `Testimonial` placeholder | Actual org URL | **Blocker** | Replace in README:54 |
| `.gitignore` coverage | `.claude/` exposed | `.claude/`, `.specify/squad/` excluded | **Blocker** | Add entries to `.gitignore` |
| Security policy | Missing | `SECURITY.md` with CVE process | High | Create file |
| Contributor docs | Missing | `CONTRIBUTING.md` | High | Create file |
| Issue templates | Missing | Bug report, feature request templates | Medium | Create `.github/ISSUE_TEMPLATE/` |
| PR template | Missing | Standard PR checklist | Medium | Create `.github/pull_request_template.md` |
| GitHub topics/description | Unconfigured | "load-testing, jmeter, performance-testing, java, grpc" | Low | Configure on repository |
| Docker Hub / GHCR registry | [REQUIRES INPUT] | Published images on release | Medium | Configure in `release.yml` |
| Code coverage badge | Missing in README | Coverage % badge | Low | Add after CI confirms baseline |

---

## 4. Specification Gaps

Derived from checklist open items across all domains:

| Gap | Domain | Priority | Action |
|-----|--------|----------|--------|
| CoordinatedOmissionDetector behavior (H1) | 002 | Medium | Read source; update spec |
| PropertyPanel auto-save timing (H2) | 006 | Medium | Read source; update spec |
| WorkerEndpoint entity definition | 004 | Medium | Document fields in spec |
| Circuit breaker OPEN→CLOSED recovery | 004 | Medium | Document mechanism |
| WebSocket reconnect during active run | 004 | Low | Document behavior |
| Configure timeout before coordinated start | 004 | Low | Document behavior |
| Refresh token TTL and rotation | 005 | Medium | Document in spec |
| Rate limiter defaults (N req/min, cooldown) | 005 | Medium | Read `RateLimitFilter.java`; document |
| `TestPlanRevisionEntity` entity table | 005 | Medium | Add to spec from source |
| Proxy recorder port conflict handling | 005 | Low | Document error scenario |
| Soft-delete + run history cascade | 005 | Low | Document intended behavior |
| SamplerExecutor base interface contract | 003 | Medium | Document methods |
| HC4/HC5 selection mechanism | 003 | Medium | Document config key |
| SLADiscovery threshold algorithm | 006 | Medium | Read source; document |
| ChaosLoad pattern types | 006 | Low | Read source; document |
| Proto versioning strategy | 001 | Medium | Create ADR |
| Benchmark regression threshold | 007 | Low | Set in benchmark.yml |

---

## 5. Dependency Gaps

### External Dependencies to Review

| Dependency | Used In | Notes |
|------------|---------|-------|
| Apache JMeter runtime | engine-adapter | Apache 2.0 — license compatible |
| XStream | engine-adapter | Security-sensitive; allowlist enforced |
| gRPC-Java | worker-proto, distributed-controller | Apache 2.0 |
| HdrHistogram | engine-service | BSD 2-Clause — compatible |
| Spring Boot 3.x | web-api | Apache 2.0 |
| H2 Database | web-api | MPL 2.0 / EPL 1.0 — compatible |
| React 19 | web-ui | MIT |
| Playwright | web-ui tests | Apache 2.0 |

All confirmed Apache 2.0 / MIT / BSD compatible. License gap: none found.

### Dependencies Requiring Monitoring

| Dependency | Risk | Action |
|------------|------|--------|
| XStream | CVE-prone; new JMeter classes require allowlist update | Review allowlist on JMeter version bump |
| HC4 (Apache HttpComponents 4) | Deprecated; HC5 is successor | Deprecation plan: HC4 → HC5 migration guide |
| H2 Database | Embedded; scalability ceiling | Document scale limits; link to PostgreSQL configuration guide |

---

## 6. Gap Closure Plan

### Priority Matrix

| Gap Category | Critical | High | Medium | Low |
|--------------|----------|------|--------|-----|
| OSS Blockers | 2 | 1 | 2 | 1 |
| Documentation | 0 | 3 | 4 | 2 |
| Infrastructure | 2 | 2 | 3 | 1 |
| Specification | 0 | 0 | 10 | 5 |
| Dependencies | 0 | 0 | 2 | 1 |

### Wave Closure

| Wave | Gaps Addressed | Exit Criteria |
|------|----------------|---------------|
| Wave 0 | OSS blockers (Testimonial URL, .gitignore) | 0 blockers remaining |
| Wave 1 | All HIGH documentation gaps; all MEDIUM spec gaps | Docs complete; spec checklists ≥90% |
| Wave 2 | MEDIUM infrastructure gaps; remaining spec gaps | CI green; SECURITY.md present |
| Wave 3 | OSS release | Public tag created; README accurate |
