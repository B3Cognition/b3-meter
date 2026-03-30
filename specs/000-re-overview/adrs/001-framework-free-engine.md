# ADR-001: Framework-Free Engine Service

**Status**: Accepted
**Date**: 2026-03-29 (reverse-engineered from codebase; original decision predates this record)
**Deciders**: Project owner (Ladislav Bihari)

## Context

The `engine-service` module is the core execution layer of jMeter Next. A key decision was made about whether this module should use a framework (Spring, CDI, Guice) or remain pure JDK.

Apache JMeter's core engine historically mixed UI, testing logic, and framework concerns heavily, making it difficult to test in isolation and hard to reason about concurrency.

## Decision Drivers

- Testability: Pure JDK classes are trivially unit-testable without Spring context startup
- Future-proofing: No framework lock-in at the execution layer
- Virtual Thread compatibility: Spring (at time of decision) had partial VT support; avoiding it eliminates VT pinning risk from framework internals
- Separation of concerns: Framework glue (DI, lifecycle management) should be in the adapter layer, not the core

## Considered Options

### Option 1: Framework-Free (Pure JDK)

**Pros**:
- Zero startup overhead in tests
- No framework version coupling
- Full control over threading model
- Easy to reason about concurrency (no hidden proxies)

**Cons**:
- Manual dependency wiring required
- No auto-configuration benefits
- More boilerplate for lifecycle management

### Option 2: Spring in Engine Service

**Pros**:
- DI container for service wiring
- Auto-configuration for scheduling, health checks
- Familiar to Java developers

**Cons**:
- Spring context startup in every test
- Spring's `@Async` and `@Scheduled` use thread pools that can pin Virtual Threads
- Framework internals can introduce unexpected `synchronized` blocks
- Makes the boundary between engine and API layer fuzzy

## Decision

**Option 1: Framework-Free** was chosen and is documented as "Constitution Principle I".

The `engine-service` module has zero framework imports. All dependency wiring happens in `engine-adapter` (via JMeter) and `web-api` (via Spring Boot).

Source evidence: `modules/engine-service/build.gradle.kts` has no Spring dependency; `EngineService.java` imports only JDK types.

## Consequences

**Positive**:
- Tests run in milliseconds without Spring context
- Virtual Threads work correctly with `ReentrantLock` (JEP-491 compliant)
- Clear module boundary: engine-service is the stable contract; adapters can be swapped

**Negative**:
- More manual DI wiring in adapter/API layers
- Contributes to module complexity in engine-adapter

**Risks**:
- Contributors unfamiliar with this pattern may attempt to add Spring — CI Checkstyle guard required

## Enforcement

CI gate: Checkstyle rule in `engine-service` module rejecting any Spring/JMeter imports.

## Related

- [constitution.md](../constitution.md) — Constitution Principle I
- [ADR-002-virtual-threads.md](002-virtual-threads.md)
