# Specification: Web API (REST Backend)

**Domain**: 005-re-web-api
**Created**: 2026-03-29
**Status**: Draft (Reverse-Engineered)
**Dependencies**: 002-re-engine-service, 003-re-engine-adapter, 004-re-distributed-controller

## Overview

The web-api module is the Spring Boot 3 REST API that serves the web frontend and exposes all test lifecycle operations. It integrates the engine, distributed controller, persistence (JDBC with H2/SQLite), JWT authentication, Server-Sent Events for real-time streaming, proxy recorder, plugin management, and OpenAPI documentation.

**Source Files Analyzed**:
- `modules/web-api/src/main/java/com/b3meter/web/api/controller/TestRunController.java`
- `modules/web-api/src/main/java/com/b3meter/web/api/controller/TestPlanController.java`
- `modules/web-api/src/main/java/com/b3meter/web/api/controller/WorkerController.java`
- `modules/web-api/src/main/java/com/b3meter/web/api/security/JwtTokenService.java`
- `modules/web-api/src/main/java/com/b3meter/web/api/security/AuthService.java`
- `modules/web-api/src/main/java/com/b3meter/web/api/repository/JdbcTestPlanRepository.java` (+ others)
- `modules/web-api/src/main/java/com/b3meter/web/api/service/TestRunService.java`
- `modules/web-api/src/main/java/com/b3meter/web/api/security/RateLimitFilter.java`
- `modules/web-api/src/main/java/com/b3meter/web/api/security/SsrfProtectionService.java`

## Complexity Estimation

| Metric | Value | Implication |
|--------|-------|-------------|
| **Files** | ~60 main + ~20 test | Large |
| **Lines of Code** | ~20,000 est. | Full-stack API module |
| **Git Commits** | application.yml in hotspots | Active config changes |
| **Contributors** | 1 | Full-stack ownership |
| **Hotspot Score** | High | application.yml change frequency |

**Estimated Complexity**: High
**Rationale**: Full Spring Boot application with JWT auth, 10+ controllers, JDBC repositories, SSE streaming, proxy recorder, plugin system, and security hardening (CSRF, rate limiting, SSRF protection, resource quotas).

## User Scenarios & Testing

### US-005.1 — Start a Test Run (Priority: P1)

As a user, I need to POST to `/api/v1/runs` with a plan ID to start a test run and receive a run handle so that I can track the run status.

**Source Evidence**:
- File: `TestRunController.java:50-58` — `POST /api/v1/runs` → `202 Accepted`
- File: `dto/StartRunRequest.java` — request body with planId and optional overrides

**Acceptance Scenarios**:
1. **Given** a valid `planId`, **When** `POST /api/v1/runs`, **Then** returns 202 with `TestRunDto` containing `runId` and initial status
2. **Given** an invalid `planId`, **When** `POST /api/v1/runs`, **Then** returns 400 Bad Request
3. **Given** the engine throws `IllegalStateException` (duplicate run), **When** `POST /api/v1/runs`, **Then** returns 409 Conflict

### US-005.2 — Poll Run Status and Metrics (Priority: P1)

As a user, I need to GET run status and latest metrics so that I can display the current state and performance numbers in the UI.

**Source Evidence**:
- File: `TestRunController.java:65-70` — `GET /api/v1/runs/{runId}` → status
- File: `TestRunController.java:99-104` — `GET /api/v1/runs/{runId}/metrics` → latest metrics snapshot
- File: `TestRunController.java:106-116` — `GET /api/v1/runs/{runId}/sla` → SLA status

**Acceptance Scenarios**:
1. **Given** a running test, **When** `GET /api/v1/runs/{runId}`, **Then** returns current `TestRunDto` with status, start time, plan ID
2. **Given** an unknown `runId`, **When** `GET /api/v1/runs/{runId}`, **Then** returns 404
3. **Given** a running test with SLA configured, **When** `GET /api/v1/runs/{runId}/sla`, **Then** returns `SlaStatus` with verdict and violation list

### US-005.3 — Real-Time Metrics via Server-Sent Events (Priority: P1)

As a dashboard user, I need to receive live metrics updates without polling so that the dashboard updates in real time without hammering the API.

**Source Evidence**:
- File: `StreamingController.java` — SSE endpoint
- File: `bridge/WebUIBridge.java` — bridges engine events to SSE
- File: `bridge/SseEmitterSink.java` — Spring SSE emitter
- File: `bridge/EventSink.java` — event sink interface

**Acceptance Scenarios**:
1. **Given** a running test, **When** client opens SSE connection to `/api/v1/stream/{runId}`, **Then** receives `SampleBucket` events every reporting interval (default 5s)
2. **Given** SSE client disconnects, **When** the next event is published, **Then** the sink is removed from the bridge without error
3. **Given** test completes, **When** the last bucket is published, **Then** SSE stream sends a `done` event and closes

### US-005.4 — JWT Authentication (Priority: P1)

As a security-conscious operator, I need API access protected by JWT tokens so that only authenticated users can start/stop tests.

**Source Evidence**:
- File: `security/JwtTokenService.java` — RS256 JWT generation and validation
- File: `security/JwtAuthenticationFilter.java` — JWT filter in Spring Security chain
- File: `controller/AuthController.java` — `/api/v1/auth/login` and `/refresh` endpoints
- File: `config/SecurityConfig.java` — Spring Security configuration

**Acceptance Scenarios**:
1. **Given** valid credentials, **When** `POST /api/v1/auth/login`, **Then** returns RS256 JWT access token (15-min TTL) and refresh token cookie
2. **Given** an expired access token, **When** any protected endpoint is called, **Then** returns 401 Unauthorized
3. **Given** a tampered JWT, **When** any protected endpoint is called, **Then** returns 401 Unauthorized
4. **Given** single-user desktop mode, **When** `SecurityConfig.singleUserMode=true`, **Then** all requests are permitted without authentication

### US-005.5 — Test Plan CRUD (Priority: P1)

As a user, I need to create, read, update, and delete test plans so that I can manage my collection of load test scenarios.

**Source Evidence**:
- File: `TestPlanController.java` — REST CRUD for plans
- File: `repository/JdbcTestPlanRepository.java` — JDBC persistence
- File: `repository/TestPlanEntity.java` — persistence model
- File: `service/TestPlanService.java` — business logic including soft-delete behavior
- File: `dto/TestPlanDto.java`, `CreatePlanRequest.java`, `UpdatePlanRequest.java`

**Acceptance Scenarios**:
1. **Given** a `CreatePlanRequest` with name and JMX content, **When** `POST /api/v1/plans`, **Then** persists plan and returns 201 with plan ID
2. **Given** a `CreatePlanRequest` with blank name, **When** `POST /api/v1/plans`, **Then** returns 400 (`"Plan name must not be blank"`)
3. **Given** a plan ID, **When** `GET /api/v1/plans/{planId}`, **Then** returns `TestPlanDto` with latest revision
4. **Given** an update to plan content, **When** `PUT /api/v1/plans/{planId}`, **Then** creates new revision (history preserved in `TestPlanRevisionEntity`)
5. **Given** a plan (with or without active runs), **When** `DELETE /api/v1/plans/{planId}`, **Then** soft-deletes the plan (no active-run guard — `repository.deleteById` called directly)
6. **Given** a `.jmx` file ≤ 50 MB, **When** `POST /api/v1/plans/import`, **Then** plan is created from raw XML and returned as `TestPlanDto`
7. **Given** a file exceeding 50 MB, **When** import is attempted, **Then** throws `FileTooLargeException` ("JMX file exceeds 50 MB limit")
8. **Given** a plan ID and revision number, **When** `POST /api/v1/plans/{planId}/restore/{revisionNumber}`, **Then** plan tree data is reverted to that revision and a new revision entry is created
  - Source: `TestPlanService.java:230` — `restore(String id, int revisionNumber)`

### US-005.6 — Worker Management (Priority: P2)

As a distributed mode user, I need to register and list remote worker nodes so that the distributed controller knows which workers are available.

**Source Evidence**:
- File: `WorkerController.java` — worker CRUD
- File: `repository/JdbcWorkerRepository.java` — worker persistence
- File: `dto/RegisterWorkerRequest.java`, `WorkerDto.java`

**Acceptance Scenarios**:
1. **Given** a `RegisterWorkerRequest` with host and port, **When** `POST /api/v1/workers`, **Then** worker is persisted and health probe is initiated
2. **Given** a registered worker, **When** `GET /api/v1/workers`, **Then** returns list of workers with health status
3. **Given** a worker is removed, **When** `DELETE /api/v1/workers/{workerId}`, **Then** worker is deregistered and circuit breaker state is cleared

### US-005.7 — Rate Limiting (Priority: P2)

As a security operator, I need API endpoints rate-limited so that a single client cannot overwhelm the server with requests.

**Source Evidence**:
- File: `security/RateLimitFilter.java` — per-client rate limiter

**Acceptance Scenarios**:
1. **Given** a client sends > N requests per minute to a protected endpoint, **When** the limit is exceeded, **Then** returns 429 Too Many Requests
2. **Given** rate limit is exceeded, **When** the cooldown expires, **Then** client requests are accepted again

### US-005.8 — SSRF Protection (Priority: P2)

As a security operator, I need to prevent Server-Side Request Forgery by validating that test target URLs resolve to public IPs so that jMeter Next cannot be used as a SSRF proxy.

**Source Evidence**:
- File: `security/SsrfProtectionService.java` — URL validation against private IP ranges

**Acceptance Scenarios**:
1. **Given** a test plan targeting `http://192.168.1.100/api`, **When** SSRF protection is enabled, **Then** run is rejected with "target resolves to private IP" error
2. **Given** a test plan targeting `http://10.0.0.1/`, **When** SSRF protection is enabled, **Then** run is rejected
3. **Given** a test plan targeting `https://httpbin.org/get`, **When** SSRF protection is enabled, **Then** run proceeds normally

### US-005.9 — Proxy Recorder (Priority: P3)

As a test creator, I need a built-in HTTP proxy recorder so that I can capture real browser traffic and convert it into a JMX test plan automatically.

**Source Evidence**:
- File: `service/ProxyRecorderService.java` — proxy server implementation
- File: `controller/ProxyRecorderController.java` — start/stop/download endpoints
- File: `service/CapturedRequest.java` — captured request model

**Acceptance Scenarios**:
1. **Given** proxy is started at port X, **When** browser is configured to use it and navigates to a page, **Then** all HTTP requests are captured
2. **Given** recording session is stopped, **When** user downloads the recording, **Then** a valid JMX file is generated from captured traffic

## Requirements

### Functional Requirements

**API Endpoints**
- **FR-005.001**: All test-lifecycle endpoints MUST be under `/api/v1/` prefix
- **FR-005.002**: API MUST be documented via OpenAPI 3 (SpringDoc)
- **FR-005.003**: All write endpoints MUST require authentication (except login)

**Persistence**
- **FR-005.004**: Default storage MUST be H2 in-memory or file-based (zero-config for development)
- **FR-005.005**: Test plan revisions MUST be stored with full history (append-only `TestPlanRevisionEntity`)

**Security**
- **FR-005.006**: JWT tokens MUST use RS256 signing (not HS256) for forward secrecy
- **FR-005.007**: JWT access token TTL MUST be 15 minutes (`ACCESS_TOKEN_TTL_MS = 900_000`)
- **FR-005.008**: CSRF protection MUST be enabled for browser-originated requests (`CsrfCookieFilter`)
- **FR-005.009**: Response headers MUST include `X-Content-Type-Options`, `X-Frame-Options` etc. (`SecurityHeadersFilter`)
- **FR-005.010**: Resource quota per user MUST limit concurrent runs, virtual users, and run duration (`ResourceQuotaService`). Defaults: `maxConcurrentRuns=3`, `maxVirtualUsers=10000`, `maxDurationSeconds=14400` (4h). All configurable via `jmeter.quota.*` properties.

**Streaming**
- **FR-005.011**: SSE endpoint MUST deliver events within 1s of bucket publication
- **FR-005.012**: SSE endpoint MUST handle concurrent subscribers for the same run

## Key Entities

### TestPlanEntity / TestPlanDto

| Attribute | Type | Description |
|-----------|------|-------------|
| id | String (UUID) | Plan identifier |
| name | String | Human-readable plan name |
| ownerId | String | Owner identifier (defaults to `"system"`) |
| treeData | String | JMX XML or JSON tree data |
| createdAt | Instant | Creation timestamp |
| updatedAt | Instant | Last update timestamp |

Source: `TestPlanService.java:58-68`, `TestPlanService.java:273-282`

### TestRunEntity / TestRunDto

| Attribute | Type | Description |
|-----------|------|-------------|
| id | UUID | Run identifier |
| planId | UUID | Source plan |
| status | TestRunStatus | PENDING/RUNNING/STOPPING/STOPPED/ERROR |
| startedAt | Instant | Run start time |
| completedAt | Instant | Run completion time (nullable) |

### UserEntity

| Attribute | Type | Description | Constraints |
|-----------|------|-------------|-------------|
| id | UUID | User identifier | |
| username | String | Login name | Unique, non-blank |
| passwordHash | String | BCrypt hash | Non-null |
| role | String | ADMIN or USER | |

## Edge Cases

- **Single-user desktop mode**: `SecurityConfig` permits all requests when `singleUserMode=true` — Source: `TestRunController.java:33-34`
- **In-memory JWT key**: Keys are regenerated on restart; tokens issued before restart are invalid — Source: `JwtTokenService.java:24-27`
- **SSE client disconnect**: `SseEmitterSink` removes itself from bridge on `TimeoutException` or `IOException` — Source: `SseEmitterSink.java`
- **SSRF loopback blocked**: `127.0.0.0/8` (all loopback addresses) IS in the default blocked CIDR list — Source: `SsrfProtectionService.java`

## Success Criteria

- **SC-005.001**: All 10 controllers respond to valid requests with correct HTTP status codes
- **SC-005.002**: JWT filter rejects tampered tokens with 401 in all test cases
- **SC-005.003**: SSE delivers first event within 1s of test start
- **SC-005.004**: SSRF protection rejects private IP ranges (10.x, 172.16-31.x, 192.168.x, 127.x)
- **SC-005.005**: Rate limiter returns 429 after configured threshold is exceeded
