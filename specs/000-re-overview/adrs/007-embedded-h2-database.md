# ADR-007: Embedded H2 Database as Default Persistence

**Status**: Accepted
**Date**: 2026-03-29 (reverse-engineered from codebase)
**Deciders**: Project owner (Ladislav Bihari)

## Context

jMeter Next needs to persist test plans, run history, worker registrations, and user accounts. The choice of database affects: setup complexity, deployment scenarios, scalability, and contributor experience.

## Decision Drivers

- Zero-config: Single developer / single node use case should require no database setup
- Container-friendly: `docker compose up` must work with no external services
- OSS contributor experience: Clone and run in < 5 minutes
- Scale: Must handle typical load testing use patterns (dozens of plans, hundreds of runs per day)

## Considered Options

### Option 1: H2 Embedded (file or in-memory)

**Pros**:
- Zero external dependencies: database lives inside the JVM or as a single file
- Instant startup
- Full JDBC SQL support (same Spring JDBC code works for H2 and PostgreSQL)
- In-memory mode perfect for tests

**Cons**:
- Single JVM only (not shared between multiple controller instances)
- H2 is not recommended for high-volume production workloads
- Different behavior from production PostgreSQL in some edge cases

### Option 2: PostgreSQL (required)

**Pros**:
- Production-grade
- Scalable to high volumes
- Used by most teams in production

**Cons**:
- Requires running a separate database service
- Breaks zero-config goal
- Docker Compose requires a postgres service (startup complexity)

### Option 3: SQLite (file-based)

**Pros**:
- Single file; zero external service
- Widely deployed

**Cons**:
- Limited Spring JDBC support
- Write-lock contention under concurrent requests
- Not standard in Java ecosystem

## Decision

**H2 embedded as default**, with configuration to switch to any JDBC-compatible database (PostgreSQL, MySQL) via `application.yml`.

Source evidence: `FR-005.004` (spec): "Default storage MUST be H2 in-memory or file-based (zero-config for development)"; `docker-compose.test.yml` has no external database service.

## Consequences

**Positive**:
- `docker compose up` works with one command
- Test suites use in-memory H2 — no test database setup
- Same JDBC code works for H2 and PostgreSQL

**Negative**:
- Multi-instance deployment not supported with H2 file mode (single-writer)
- Scale ceiling: H2 begins degrading above ~10k runs (rough estimate)

**Risks**:
- H2 vs PostgreSQL query dialect differences may cause migration pain
- Mitigation: Use standard ANSI SQL in repositories; avoid H2-specific syntax

## Scale Guidance

| Scale | Recommended Config |
|-------|-------------------|
| Single developer / CI | H2 in-memory (`spring.datasource.url=jdbc:h2:mem:jmeter`) |
| Small team / persistent storage | H2 file (`jdbc:h2:file:/data/jmeter`) |
| Production / multi-user | PostgreSQL or MySQL via `application.yml` override |

## Related

- [constitution.md](../constitution.md) — Principle V (Zero-Config Single-Node)
- Source: `docker-compose.test.yml`, `modules/web-api/src/main/resources/application.yml`
