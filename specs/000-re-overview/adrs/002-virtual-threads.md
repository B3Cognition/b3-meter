# ADR-002: Java 21 Virtual Threads for VU Simulation

**Status**: Accepted
**Date**: 2026-03-29 (reverse-engineered from codebase)
**Deciders**: Project owner (Ladislav Bihari)

## Context

Load testing tools need to simulate thousands of concurrent virtual users (VUs). Traditional approaches use platform threads (one thread per VU), goroutines, async I/O, or event loops.

The choice of concurrency model fundamentally affects: maximum VU count per node, memory footprint, debugging complexity, and I/O performance.

## Decision Drivers

- Scale: Must support high VU counts on commodity hardware
- Java 21: Virtual Threads (JEP-444) are production-ready in the target Java version
- Simplicity: Thread-per-VU model is easiest to understand and debug
- Synchronization: Must avoid VT pinning under high concurrency (JEP-491)

## Considered Options

### Option 1: Java 21 Virtual Threads (JEP-444)

**Pros**:
- Millions of VUs per JVM (vs ~10k with platform threads)
- Thread-per-VU model: simple, debuggable, matches JMeter's mental model
- Full Java blocking API support (JDBC, HTTP, gRPC)
- No callback hell; sequential code style

**Cons**:
- `synchronized` keyword causes VT pinning — must use `ReentrantLock`
- Java 21 LTS required (not backward compatible with Java 17)
- JVM profilers/tools less mature for VT debugging

### Option 2: Reactive (Project Reactor / RxJava)

**Pros**:
- Very high concurrency without threads
- Battle-tested in production

**Cons**:
- Non-linear code flow; hard to reason about test scenarios
- Error propagation complex
- Incompatible with blocking JMeter sampler implementations
- Much higher contributor learning curve

### Option 3: Platform Threads

**Pros**:
- Compatible with all Java versions
- Mature tooling

**Cons**:
- ~10k VUs max per JVM (OS thread limit)
- 1-2 MB stack per thread × 10k = 10-20 GB RAM for large tests
- Does not scale to jMeter Next's target use case

## Decision

**Option 1: Java 21 Virtual Threads** was chosen.

Source evidence: `ArrivalRateExecutor.java` comments explicitly reference JEP-444; `FR-002.006` requires VT; `FR-002.009` mandates `ReentrantLock`.

## Consequences

**Positive**:
- High VU count on commodity hardware
- Simple thread-per-VU code model
- Blocking I/O in samplers works naturally

**Negative**:
- Java 21 minimum version — stated requirement
- All executor code must avoid `synchronized` — CI enforcement needed
- JVM startup with many VTs may have higher overhead in very short tests

**Risks**:
- Contributors adding `synchronized` cause VT pinning under load — hard to detect without specific load tests
- Third-party JMeter sampler plugins may internally use `synchronized` — out of scope for enforcement

## Enforcement

Constitution Principle II enforced by:
1. Checkstyle rule (or static analysis) checking for `synchronized` in executor classes
2. Explicit comment in `ArrivalRateExecutor.java:48`
3. JEP-491 reference in spec FR-002.009

## Related

- [ADR-001-framework-free-engine.md](001-framework-free-engine.md)
- [constitution.md](../constitution.md) — Principles I and II
- Source: `ArrivalRateExecutor.java:48-54`
