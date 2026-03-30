# ADR-006: XStream Deserialization Security Policy

**Status**: Accepted
**Date**: 2026-03-29 (reverse-engineered from codebase)
**Deciders**: Project owner (Ladislav Bihari)

## Context

Apache JMeter uses XStream for JMX (XML) serialization/deserialization. XStream has a history of critical CVEs allowing remote code execution via deserialization of crafted XML containing arbitrary class references (similar to Java deserialization gadget chains).

jMeter Next processes user-uploaded JMX files, which are a potential attack surface.

## Decision Drivers

- Security: Uploaded JMX files must not be able to execute arbitrary code
- Functionality: All valid JMeter component classes must deserialize successfully
- Defense in depth: Multiple layers of protection preferred

## Considered Options

### Option 1: Block all non-JMeter classes (Allowlist)

Implement `XStreamSecurityPolicy` with an explicit allowlist of JMeter internal classes. Any class not on the allowlist throws a security exception.

**Pros**:
- Strong protection: unknown classes are rejected immediately
- Explicit contract: allowlist documents what is supported
- Easy to audit

**Cons**:
- Allowlist needs maintenance as JMeter adds classes
- Custom JMeter plugins cannot be deserialized without allowlist update

### Option 2: Block known-dangerous classes (Denylist)

Use XStream's built-in class denial to block known gadget chains.

**Pros**:
- Less maintenance

**Cons**:
- Security is only as good as the denylist
- New gadget chains discovered after release bypass the deny list
- XStream CVE history shows denylist approach is insufficient

### Option 3: Replace XStream with StAX-based parser

Use a custom StAX parser (no class resolution) for all JMX operations.

**Pros**:
- No deserialization risk at all

**Cons**:
- JMX format is tightly coupled to XStream's class serialization model
- Full reimplementation required; incomplete at time of decision (T014 debt)

## Decision

**Option 1 (Allowlist) as primary; Option 3 (StAX) for import validation**.

- `XStreamSecurityPolicy.java` implements the allowlist for full execution parsing
- `JmxTreeWalker.java` (StAX-based) is used at JMX import time — no class loading, pure XML validation
- Full XStream parsing occurs only at test execution time with the allowlist enforced

Source evidence: `XStreamSecurityPolicy.java`; `TestPlanService.java:169-176` uses `JmxTreeWalker` for import validation.

## Consequences

**Positive**:
- Defense in depth: StAX validation before storage, allowlist enforcement at execution
- `java.lang.Runtime` and other dangerous classes are explicitly rejected
- Known attack surface documented and mitigated

**Negative**:
- Allowlist must be updated when JMeter adds new component classes
- Custom JMeter plugins require allowlist additions

**Risks**:
- JMeter version upgrade may add new component classes not on allowlist → tests fail with security exception
- Mitigation: Integration test that runs all 38 JMX test plans from `test-plans/` to catch allowlist gaps

## Enforcement

FR-003.002 (spec): `JmxParser` MUST enforce `XStreamSecurityPolicy`.
SC-003.002 (spec): `XStreamSecurityPolicy` rejects `java.lang.Runtime` and passes JMeter-internal classes.

## Related

- [constitution.md](../constitution.md) — Principle IV (Security by Default)
- Source: `XStreamSecurityPolicy.java`, `JmxTreeWalker.java`, `TestPlanService.java:169-176`
