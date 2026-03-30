# Project Constitution: jMeter Next

**Generated**: 2026-03-29
**Source System**: Brownfield — jMeter Next v1.0 (active development)
**Goal**: Open-source release preparation (not a platform migration)
**Contributors**: 1 (Ladislav Bihari — full-stack ownership)

---

## Part 1: Current System Analysis

### 1.1 Technology Stack

| Component | Technology | Version | Notes |
|-----------|------------|---------|-------|
| Core language | Java | 21 (LTS) | Virtual Threads (JEP-444), RecordPatterns (JEP-440) |
| Build | Gradle | Kotlin DSL (`build.gradle.kts`) | Multi-module monorepo, 6 modules |
| Spring Boot | Spring Boot | 3.x | Web API module only (modules/web-api) |
| Persistence | Spring JDBC + H2 | 2.x / 2.2.x | Embedded, zero-config; SQLite optional |
| gRPC | gRPC-Java + Protobuf | 3.x | Worker↔Controller wire protocol |
| Frontend | React | 19 | TypeScript, Vite build |
| Testing | JUnit 5 + Playwright | 5.x / 1.x | Unit + E2E; accessibility tests |
| CI/CD | GitHub Actions | 7 workflows | PR gate, nightly, benchmark, release |
| Containers | Docker + Compose | - | 2 Dockerfiles; 2 compose configs |
| Security | OWASP Dependency Check | Latest | CVE scanning with suppressions |

### 1.2 Architectural Patterns

**Structure**: Gradle multi-module monorepo with hexagonal architecture

**Patterns Identified**:
- **Hexagonal Architecture (Ports & Adapters)**: `engine-service` defines interfaces; `engine-adapter` implements them against JMeter. The core is framework-free.
- **Strangler Fig (internal)**: `JmxTreeWalker` (StAX, lightweight) for import validation; `JmxParser` (XStream) for execution — JMeter internals being progressively isolated.
- **Circuit Breaker**: `WorkerHealthPoller` + `WorkerClient.isCircuitOpen()` for distributed fault tolerance.
- **Event-Driven Streaming**: `InMemorySampleStreamBroker` → `SseEmitterSink` → React SSE consumer.
- **Command-Query Separation**: `startRun()`/`stopRun()` side-effecting; `getRunStatus()`, `activeRuns()` read-only.
- **Coordinated distributed start**: Future timestamp (`now + 5000ms`) passed to all workers simultaneously.

**Anti-patterns Identified**:
- **Large component (PropertyPanel.tsx)**: 1268+ lines — candidate for decomposition
- **JMX import deferred**: Full XStream parsing deferred to T014 — raw XML stored, not parsed into typed tree at import
- **Single contributor**: All knowledge concentrated in one engineer — documentation critical for OSS contributors
- **No test plan revision conflict detection**: Soft-delete with no active-run guard could orphan run history

### 1.3 Hotspot Analysis

| File | Category | Likely Cause | Risk |
|------|----------|--------------|------|
| `SLADiscovery.tsx` | UI Feature | Active innovation development | Medium — complex algorithm |
| `WorkerServiceImpl.java` | gRPC | Core distribution logic | High — concurrency-sensitive |
| `worker.proto` | Protocol | API evolution | Medium — breaking changes cascade |
| `TestPlanExecutor.java` | Execution | JMeter integration complexity | High — legacy bridge |
| `application.yml` | Config | Environment tuning | Low — configuration drift |

### 1.4 Technical Debt

1. **JMX Full Parsing Deferred (T014)**: `TestPlanService.importJmx` stores raw XML; full XStream parsing deferred. Risk: stored JSON format mixing with XML for new plans.
   - Files affected: `TestPlanService.java`, `TestPlanEntity`, all plan export paths

2. **PropertyPanel.tsx Monolith**: 1268+ lines handles form rendering, validation, schema management, and save logic.
   - Files affected: `PropertyPanel.tsx`

3. **HC4/HC5 Dual Factory**: Two HTTP client factories maintained for backward compatibility. HC4 should eventually be deprecated once HC5 stability is proven.
   - Files affected: `Hc4HttpClientFactory.java`, `Hc5HttpClientFactory.java`, `LegacyPropertyBridge.java`

4. **In-Memory JWT Keys**: RS256 key pair regenerated on restart — all sessions invalidated. No refresh token persistence. Acceptable for desktop/single-node; limitation for multi-instance deployment.
   - Files affected: `JwtTokenService.java`

### 1.5 OSS Preparation Issues

| Issue | Severity | Status | Action |
|-------|----------|--------|--------|
| `github.com/Testimonial/b3meter.git` in README:54 | HIGH | Unfixed | Replace with actual GitHub org |
| `.claude/commands/` AI tooling | HIGH | Unfixed | Add `.claude/` to `.gitignore` |
| `.specify/squad/` analysis data | MEDIUM | Unfixed | Add to `.gitignore` |
| `.specify/extensions/` spec tooling | LOW | Decision required | Include or exclude |
| `.claude/CLAUDE.md` internal refs | LOW | Unfixed | Review before publishing |

### 1.6 Lessons Learned

**Preserve**:
- Framework-free engine-service — enables clean testing, future framework swap
- HDR histogram for distributed percentiles — accurate, no coordinated omission
- StAX-based JmxTreeWalker for import validation — secure, no class loading
- JUnit 5 + dedicated test classes per source class

**Avoid**:
- Storing raw XML without full parsing (T014 debt)
- Growing single-file React components beyond 400 lines
- Tight coupling between web-api and JMeter internal classes

**Improve**:
- Contributor documentation (architecture guides, how-to guides)
- PropertyPanel decomposition before adding more component types
- Refresh token persistence for multi-instance scenarios

---

## Part 2: Target Constitution (OSS Release Standard)

### 2.1 Technology Stack

The stack is **retained as-is** for the OSS release. No platform migration.

| Component | Technology | Version | Status |
|-----------|------------|---------|--------|
| Core language | Java | 21 LTS | ✓ Current |
| Build | Gradle Kotlin DSL | Latest stable | ✓ Current |
| Spring Boot | Spring Boot 3.x | Latest 3.x | ✓ Current |
| Persistence | H2 (default), SQLite (optional) | Latest | ✓ Current |
| gRPC | gRPC-Java | Latest stable | ✓ Current |
| Frontend | React 19 + TypeScript | Latest stable | ✓ Current |
| Testing | JUnit 5 + Playwright | Latest stable | ✓ Current |

### 2.2 Architectural Principles

1. **Constitution Principle I — Framework-Free Engine Core**
   - `engine-service` module MUST NOT import Spring, JMeter, or any third-party framework classes
   - Only JDK standard library allowed in `engine-service`
   - Rationale: testability, future-proofing, clear separation of concerns

2. **Constitution Principle II — Virtual Threads (JEP-444)**
   - All executor classes MUST use Java 21 Virtual Threads for VU simulation
   - MUST NOT use `synchronized` on virtual thread code paths — use `ReentrantLock` (JEP-491)
   - Rationale: High VU count without OS thread overhead

3. **Constitution Principle III — Accurate Percentiles**
   - All cross-worker percentile aggregation MUST use HDR histogram merge
   - MUST NOT average p-values across workers (mathematically incorrect)
   - Rationale: Accuracy under distributed load; coordinated omission prevention

4. **Constitution Principle IV — Security by Default**
   - XStream deserialization MUST use allowlist (`XStreamSecurityPolicy`)
   - SSRF protection MUST block all private IP ranges including loopback
   - JWT MUST use RS256 (not HS256)
   - All Docker images MUST run as non-root

5. **Constitution Principle V — Zero-Config Single-Node Deployment**
   - Default persistence MUST be H2 embedded (no database setup required)
   - `docker compose -f docker-compose.test.yml up` MUST work with zero configuration
   - Rationale: Contributor experience; developer onboarding

### 2.3 Coding Standards

- **Java Style**: Google Java Style Guide; enforced via Checkstyle in CI
- **TypeScript**: Strict mode (`tsconfig.json: strict: true`); ESLint
- **Test Coverage**: Minimum 80% line coverage per module (enforced by phase-0 gate)
- **Test Naming**: `{ClassName}Test.java` for each production class; `{ComponentName}.test.tsx` for React
- **Documentation**: Javadoc required for all public API methods; TSDoc for exported types
- **Commit Style**: Conventional Commits (`feat:`, `fix:`, `docs:`, `test:`, `refactor:`)

### 2.4 Quality Gates

All gates enforced by `phase0-gate.yml` GitHub Actions workflow:

- [ ] All unit tests pass (`./gradlew test`)
- [ ] TypeScript type check passes (`tsc --noEmit`)
- [ ] 80% test coverage threshold met
- [ ] ESLint passes (zero errors)
- [ ] OWASP dependency scan: no critical unresolved CVEs
- [ ] Dockerfile security: non-root user verified
- [ ] No `System.out.println` in production code (Checkstyle)

### 2.5 Contributor Guidelines

- All new features require: spec update + implementation + tests + docs
- Breaking changes to `worker.proto` require ADR approval
- New sampler executors follow existing pattern in `interpreter/` directory
- OSS contributors should read `CONTRIBUTING.md` before submitting PRs

---

## Approval

- [ ] Legacy analysis reviewed for accuracy
- [ ] Architectural principles confirmed by project owner
- [ ] OSS preparation checklist reviewed
- [ ] Quality gates verified as working in CI

**Approved by**: _______________
**Date**: _______________
