# Specification: Test Infrastructure (CI/CD, Docker, Test Servers)

**Domain**: 007-re-test-infrastructure
**Created**: 2026-03-29
**Status**: Draft (Reverse-Engineered)
**Dependencies**: 001 through 006

## Overview

The test infrastructure domain covers the build automation, CI/CD pipelines, Docker deployment configurations, test target servers (MQTT mock, HTTP mock), and quality gates. This domain ensures reproducible builds, automated testing, and easy deployment in Docker/Kubernetes environments.

**Source Files Analyzed**:
- `.github/workflows/ci.yml`, `pr-gate.yml`, `nightly.yml`, `benchmark.yml`, `release.yml`
- `docker-compose.test.yml`, `docker-compose.distributed.yml`
- `Dockerfile.controller`, `Dockerfile.worker`
- `test-servers/mqtt-mock/Dockerfile` (in hotspots)
- `config/owasp-suppressions.xml`
- `Makefile`

## Complexity Estimation

| Metric | Value | Implication |
|--------|-------|-------------|
| **Files** | ~23 yml + 2 Dockerfile + Makefile | Medium |
| **Lines of Code** | ~5,000 est. | Infrastructure-focused |
| **Git Commits** | test-servers Dockerfile in hotspots | Active changes |
| **Contributors** | 1 | |
| **Hotspot Score** | Medium | test-servers/mqtt-mock/Dockerfile |

**Estimated Complexity**: Medium
**Rationale**: Well-structured GitHub Actions with phase-gate approach. Multiple docker-compose configurations for different deployment scenarios.

## User Scenarios & Testing

### US-007.1 — CI/CD Build and Test on Every PR (Priority: P1)

As a contributor, I need every PR to run the full test suite automatically so that I get feedback before merge.

**Source Evidence**:
- File: `.github/workflows/pr-gate.yml` — PR gate workflow
- File: `.github/workflows/ci.yml` — main CI workflow

**Acceptance Scenarios**:
1. **Given** a PR is opened, **When** `pr-gate.yml` triggers, **Then** all unit tests must pass before merge is allowed
2. **Given** a PR changes Java code, **When** CI runs, **Then** `./gradlew build test` executes and results are posted to PR
3. **Given** TypeScript code is changed, **When** CI runs, **Then** `tsc --noEmit` type check is executed

### US-007.2 — Phase-0 Gate Quality Check (Priority: P1)

As a quality gatekeeper, I need a dedicated phase-0 gate workflow that enforces minimum quality standards so that only production-ready code reaches the main branch.

**Source Evidence**:
- File: `.github/workflows/phase0-gate.yml` — dedicated phase gate

**Acceptance Scenarios**:
1. **Given** code fails any phase-0 check, **When** PR is submitted, **Then** merge is blocked
2. **Given** code passes all checks, **When** phase-0 gate completes, **Then** PR can proceed to review

### US-007.3 — Single-Node Docker Deployment (Priority: P1)

As a user on a single machine, I need to run the entire stack with one Docker Compose command so that I can use jMeter Next without installing Java or Node.

**Source Evidence**:
- File: `docker-compose.test.yml` — test deployment
- File: `Dockerfile.controller` — backend container

**Acceptance Scenarios**:
1. **Given** `docker compose -f docker-compose.test.yml up`, **When** containers start, **Then** backend is available on port 8080 and frontend on port 3000
2. **Given** no prior data, **When** containers start, **Then** H2 database initializes automatically (zero-config)

### US-007.4 — Distributed Docker Deployment (Priority: P2)

As a distributed mode user, I need to start a controller and multiple workers with Docker Compose so that I can run distributed load tests locally.

**Source Evidence**:
- File: `docker-compose.distributed.yml` — distributed mode compose
- File: `Dockerfile.worker` — worker container

**Acceptance Scenarios**:
1. **Given** `docker compose -f docker-compose.distributed.yml up --scale worker=3`, **When** started, **Then** 1 controller + 3 workers are running and discoverable
2. **Given** workers are registered, **When** a distributed test starts, **Then** all 3 workers participate

### US-007.5 — MQTT Mock Server (Priority: P2)

As a test developer, I need a mock MQTT broker so that I can test the MQTT sampler in CI without requiring a real MQTT server.

**Source Evidence**:
- File: `test-servers/mqtt-mock/Dockerfile` — MQTT mock container (in hotspots)
- File: `test-servers/` directory

**Acceptance Scenarios**:
1. **Given** the MQTT mock is running, **When** MQTT sampler connects, **Then** it receives expected protocol-level responses
2. **Given** a test using MQTT sampler in CI, **When** running against the mock, **Then** test completes successfully

### US-007.6 — Nightly Benchmark Run (Priority: P2)

As a performance team, I need nightly benchmark runs that track performance regression over time so that no commit silently degrades throughput.

**Source Evidence**:
- File: `.github/workflows/benchmark.yml` — nightly benchmark
- File: `.github/workflows/nightly.yml` — nightly suite

**Acceptance Scenarios**:
1. **Given** nightly trigger at scheduled time, **When** benchmark runs, **Then** throughput and latency baselines are recorded
2. **Given** a regression vs. prior baseline, **When** benchmark completes, **Then** GitHub notification is sent

### US-007.7 — OWASP Dependency Vulnerability Scan (Priority: P1)

As a security team, I need dependencies scanned for known CVEs so that vulnerable libraries are caught before release.

**Source Evidence**:
- File: `config/owasp-suppressions.xml` — false-positive suppressions

**Acceptance Scenarios**:
1. **Given** OWASP dependency check runs, **When** a critical CVE is found, **Then** the build fails
2. **Given** a suppressed CVE, **When** found, **Then** it is logged as suppressed (not a build failure) if listed in `owasp-suppressions.xml`

## Requirements

### Functional Requirements

**CI/CD**
- **FR-007.001**: Every PR MUST trigger `pr-gate.yml` before merge is allowed
- **FR-007.002**: `ci.yml` MUST run `./gradlew build test` for Java + `tsc --noEmit` for TypeScript
- **FR-007.003**: `phase0-gate.yml` MUST enforce quality gates (test coverage, linting, security scan)
- **FR-007.004**: `release.yml` MUST build and push Docker images on version tag

**Docker**
- **FR-007.005**: `Dockerfile.controller` MUST produce a self-contained image with embedded H2 database
- **FR-007.006**: `Dockerfile.worker` MUST produce a minimal JRE image with only the worker-node module
- **FR-007.007**: Both images MUST run as non-root user

**Test Servers**
- **FR-007.008**: `test-servers/mqtt-mock` MUST handle CONNECT, SUBSCRIBE, PUBLISH, DISCONNECT messages per MQTT 3.1.1
- **FR-007.009**: Test server containers MUST be started via `docker-compose.test.yml` in CI

## Success Criteria

- **SC-007.001**: CI build passes on a clean clone with `./gradlew build`
- **SC-007.002**: `docker compose -f docker-compose.test.yml up` starts successfully with no manual config
- **SC-007.003**: OWASP scan runs without critical unresolved CVEs
- **SC-007.004**: Phase-0 gate blocks a PR with a failing test (verified in CI)
- **SC-007.005**: Distributed compose starts 1 controller + 3 workers, all healthy
