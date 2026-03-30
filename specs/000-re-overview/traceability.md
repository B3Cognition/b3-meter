# Source-to-Spec Traceability

**Generated**: 2026-03-29
**Total Source Files**: ~576 (per analysis.json)
**Domains Covered**: 7
**Coverage Estimate**: ~75% (primary source modules fully covered; test corpus and binary artifacts not counted)

## Coverage Summary

| Domain | Primary Source Module | Coverage | Key Requirements |
|--------|----------------------|----------|-----------------|
| 001-re-worker-proto | `modules/worker-proto/` | Full | FR-001.001-006 |
| 002-re-engine-service | `modules/engine-service/` | Full | FR-002.001-014 |
| 003-re-engine-adapter | `modules/engine-adapter/` | Full | FR-003.001-013 |
| 004-re-distributed-controller | `modules/distributed-controller/` | Full | FR-004.001-013 |
| 005-re-web-api | `modules/web-api/` | Full | FR-005.001-012 |
| 006-re-web-ui | `web-ui/src/` | Full | FR-006.001-009 |
| 007-re-test-infrastructure | `.github/`, `docker-*`, `test-servers/` | Full | FR-007.001-009 |

## File-to-Domain Mapping (Key Files)

| Source File | Domain | Depth |
|-------------|--------|-------|
| `modules/worker-proto/src/main/proto/worker.proto` | 001-re-worker-proto | full |
| `modules/engine-service/src/main/java/.../EngineService.java` | 002-re-engine-service | full |
| `modules/engine-service/src/main/java/.../ArrivalRateExecutor.java` | 002-re-engine-service | full |
| `modules/engine-service/src/main/java/.../SlaEvaluator.java` | 002-re-engine-service | full |
| `modules/engine-service/src/main/java/.../interpreter/*.java` (50+ files) | 003-re-engine-adapter | signatures |
| `modules/engine-adapter/src/main/java/.../jmx/JmxParser.java` | 003-re-engine-adapter | full |
| `modules/engine-adapter/src/main/java/.../security/XStreamSecurityPolicy.java` | 003-re-engine-adapter | full |
| `modules/engine-adapter/src/main/java/.../cli/B3MeterCli.java` | 003-re-engine-adapter | signatures |
| `modules/distributed-controller/src/main/java/.../DistributedRunService.java` | 004-re-distributed-controller | full |
| `modules/distributed-controller/src/main/java/.../ResultAggregator.java` | 004-re-distributed-controller | signatures |
| `modules/distributed-controller/src/main/java/.../WorkerHealthPoller.java` | 004-re-distributed-controller | signatures |
| `modules/web-api/src/main/java/.../controller/TestRunController.java` | 005-re-web-api | full |
| `modules/web-api/src/main/java/.../security/JwtTokenService.java` | 005-re-web-api | full |
| `modules/web-api/src/main/java/.../security/SsrfProtectionService.java` | 005-re-web-api | signatures |
| `web-ui/src/components/Dashboard/LiveDashboard.tsx` | 006-re-web-ui | signatures |
| `web-ui/src/components/Innovation/SLADiscovery.tsx` | 006-re-web-ui | signatures |
| `web-ui/src/components/TreeEditor/TestPlanTree.tsx` | 006-re-web-ui | signatures |
| `.github/workflows/*.yml` (7 files) | 007-re-test-infrastructure | full |
| `docker-compose.*.yml` | 007-re-test-infrastructure | full |

## Orphan Files (Not Covered by a Domain Spec)

| File/Category | Reason | Status |
|---------------|--------|--------|
| `perf/` directory | Performance benchmarks (Gradle module, 0 source files in analysis) | Low risk |
| `test-data/` | Test fixtures for JMX plans | N/A (test support) |
| `test-plans/*.jmx` | Sample JMX test plans (38 files) | N/A (example data) |
| `scripts/db-backup.sh`, `db-restore.sh` | Operational scripts (in hotspots) | Should be covered in 005-re-web-api |
| `docs/api-reference.md`, `configuration.md` | Documentation | N/A (docs) |
| `web-ui/load-testing-research.md` | Research document backing SLA Discovery | N/A (research) |
| `reasoning-journal.json` (git history) | Previous in repo, no longer present | Gone |
| `.claude/commands/` | AI tooling — SHOULD NOT be in public OSS release | ⚠️ ACTION REQUIRED |
| `.specify/` | Specification tooling — evaluate for OSS inclusion | ⚠️ DECISION REQUIRED |

## Open-Source Preparation Issues

### ⚠️ Items Requiring Action Before Public Release

| Issue | Severity | Location | Recommended Action |
|-------|----------|----------|--------------------|
| Placeholder GitHub org `Testimonial` | Medium | `README.md:54` | Replace with actual GitHub org URL |
| `.claude/commands/` directory | Medium | `.claude/` | Add to `.gitignore` or remove before public release — internal AI tooling |
| `.specify/` directory | Low | `.specify/` | Decide: include as project tooling or add to `.gitignore` |
| `.claude/CLAUDE.md` | Low | `.claude/CLAUDE.md` | Review for internal references before publishing |

### ✅ Clean Items (No Action Required)

- No stats perform references in source code
- No opta references in source code
- No sports analytics domain references
- No private NPM registry configurations
- No internal artifact repository URLs
- No hardcoded API keys or secrets
- No private cloud endpoints
- No internal email addresses in source
- No organization-specific domain names (except placeholder `Testimonial`)
- License is Apache 2.0 ✅

## Requirement Traceability (Sample)

| Requirement | Source Evidence | Test Coverage |
|-------------|-----------------|---------------|
| FR-001.001 (6 RPCs defined) | `worker.proto:21-38` | `WorkerServiceImplTest.java` |
| FR-002.007 (ArrivalRate ±5%) | `ArrivalRateExecutor.java:196-202` | `ArrivalRateExecutorTest.java` |
| FR-002.013 (No Spring in engine-service) | Package structure, build.gradle.kts | Compile-time (no Spring dep in module) |
| FR-003.002 (XStream security) | `XStreamSecurityPolicy.java` | `XStreamSecurityPolicyTest.java` |
| FR-003.006 (Concurrent run isolation) | `ConcurrentRunIsolationTest.java` | `ConcurrentRunIsolationTest.java` |
| FR-004.001 (Coordinated start ±100ms) | `DistributedRunService.java:135` | `DistributedRunServiceTest.java` |
| FR-005.007 (JWT TTL 15 min) | `JwtTokenService.java:39` | `JwtTokenServiceTest.java` (implied) |
| FR-005.009 (Security headers) | `SecurityHeadersFilter.java` | Integration test |
| FR-006.009 (WCAG 2.1 AA) | `src/__tests__/accessibility.test.tsx` | `accessibility.test.tsx` |
