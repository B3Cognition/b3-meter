# Specification: Engine Adapter (JMeter Integration Layer)

**Domain**: 003-re-engine-adapter
**Created**: 2026-03-29
**Status**: Draft (Reverse-Engineered)
**Dependencies**: 002-re-engine-service

## Overview

The engine-adapter module bridges the pure-Java engine-service layer with the Apache JMeter runtime. It implements `EngineService` against a real `StandardJMeterEngine`, parses JMX test plans from XML, manages 50+ protocol-specific sampler executor classes, handles HTTP client factories (HC4/HC5), provides a CLI entry point, and generates HTML reports.

**Source Files Analyzed**:
- `modules/engine-adapter/src/main/java/com/b3meter/engine/adapter/EngineServiceImpl.java`
- `modules/engine-adapter/src/main/java/com/b3meter/engine/adapter/TestPlanExecutor.java`
- `modules/engine-adapter/src/main/java/com/b3meter/engine/adapter/jmx/JmxParser.java`
- `modules/engine-adapter/src/main/java/com/b3meter/engine/adapter/cli/B3MeterCli.java`
- `modules/engine-adapter/src/main/java/com/b3meter/engine/adapter/http/Hc5HttpClientFactory.java`
- `modules/engine-adapter/src/main/java/com/b3meter/engine/adapter/security/XStreamSecurityPolicy.java`
- `modules/engine-service/src/main/java/com/b3meter/engine/service/interpreter/` (50+ executor classes)

## Complexity Estimation

| Metric | Value | Implication |
|--------|-------|-------------|
| **Files** | ~55 main + ~20 test | Large |
| **Lines of Code** | ~30,000 est. | Largest module |
| **Git Commits** | Major uplift commit included | Most active area |
| **Contributors** | 1 | Specialist knowledge |
| **Hotspot Score** | High | TestPlanExecutor in hotspots |

**Estimated Complexity**: Very High
**Rationale**: Bridges between clean JDK API and legacy JMeter internals. 50+ executor classes for different sampler/controller types, XStream XML deserialization (security-sensitive), HTTP/2 protocol negotiation, multi-tenant test isolation.

## User Scenarios & Testing

### US-003.1 — Parse JMX Test Plan (Priority: P1)

As a test run service, I need to parse a JMX XML file into an in-memory object tree so that the engine can execute the test plan without reading from disk at runtime.

**Source Evidence**:
- File: `jmx/JmxParser.java` — JMX XML → object model
- File: `jmx/JmxParseException.java` — error type for parse failures

**Acceptance Scenarios**:
1. **Given** a valid JMX file, **When** `JmxParser.parse()` is called, **Then** returns the root `TestPlan` object with all child elements
2. **Given** a JMX file with invalid XML, **When** parsing, **Then** throws `JmxParseException` with descriptive message
3. **Given** a JMX file with unsupported element types, **When** parsing, **Then** unsupported elements are logged and skipped (graceful degradation)

### US-003.2 — Execute Test Plan via JMeter Engine (Priority: P1)

As a test operator, I need the adapter to submit the parsed plan to JMeter's `StandardJMeterEngine` and bridge lifecycle events (start, stop, results) to the engine-service abstraction.

**Source Evidence**:
- File: `EngineServiceImpl.java` — implements `EngineService`
- File: `TestPlanExecutor.java` — test execution coordinator
- File: `InMemorySampleStreamBroker.java` — results streaming

**Acceptance Scenarios**:
1. **Given** a parsed test plan, **When** `startRun` is called, **Then** JMeter engine starts and virtual users begin sampling
2. **Given** a running test, **When** `stopRun` is called, **Then** JMeter engine receives graceful stop signal
3. **Given** multiple concurrent tests, **When** `activeRuns()` is called, **Then** returns all currently running test contexts

### US-003.3 — HTTP/2 Protocol Support (Priority: P1)

As a load tester targeting modern HTTP services, I need the HTTP sampler to support HTTP/2 via ALPN negotiation so that I can test HTTP/2-only endpoints accurately.

**Source Evidence**:
- File: `http/Hc5HttpClientFactory.java` — Apache HttpComponents 5 factory
- File: `http/Hc4HttpClientFactory.java` — HC4 compatibility factory
- Test: `HttpProtocolNegotiationTest.java` — protocol negotiation tests

**Acceptance Scenarios**:
1. **Given** a target URL supporting HTTP/2, **When** using `Hc5HttpClientFactory`, **Then** ALPN negotiation results in HTTP/2 being used
2. **Given** a target URL supporting only HTTP/1.1, **When** using `Hc5HttpClientFactory`, **Then** falls back to HTTP/1.1
3. **Given** legacy HC4 mode is configured, **When** `Hc4HttpClientFactory` is used, **Then** only HTTP/1.1 is used

### US-003.4 — XStream Security Policy Enforcement (Priority: P1)

As a security-conscious operator, I need the JMX XML deserializer to restrict allowed classes so that malicious JMX files cannot perform classpath attacks via XStream deserialization.

**Source Evidence**:
- File: `security/XStreamSecurityPolicy.java` — XStream class allowlist
- Test: `XStreamSecurityPolicyTest.java`

**Acceptance Scenarios**:
1. **Given** a JMX file containing a JMeter-internal class, **When** deserialized, **Then** succeeds (class is on allowlist)
2. **Given** a JMX file containing `java.lang.Runtime`, **When** deserialized, **Then** throws security exception (class is blocked)
3. **Given** a JMX file with `<com.attacker.Payload>`, **When** parsed, **Then** rejected immediately

### US-003.5 — Headless CLI Execution (Priority: P2)

As a DevOps engineer, I need to run test plans from the command line without the web UI so that tests can be integrated into CI/CD pipelines.

**Source Evidence**:
- File: `cli/B3MeterCli.java` — CLI entry point
- Test: `B3MeterCliTest.java`
- Build: allows `./gradlew :modules:engine-adapter:run --args="..."`

**Acceptance Scenarios**:
1. **Given** `--plan path/to/plan.jmx --non-gui --duration 30`, **When** CLI is executed, **Then** test runs for 30 seconds and outputs results to stdout
2. **Given** `--plan` is missing, **When** CLI is run, **Then** exits with error code and usage message
3. **Given** a valid plan, **When** CLI runs, **Then** generates HTML report in the output directory

### US-003.6 — Multi-Tenant Test Context Isolation (Priority: P1)

As a multi-user application, I need each concurrent test run to have fully isolated state so that threads from run A cannot see or affect run B's variables, extractors, or samplers.

**Source Evidence**:
- File: `ConcurrentRunIsolationTest.java` — dedicated isolation test
- File: `test/MultiTenantTestContext.java` — test harness for multi-tenancy

**Acceptance Scenarios**:
1. **Given** two concurrent test runs, **When** both are executing, **Then** JMeter thread-local variables (JMeterVariables) do not bleed between runs
2. **Given** run A is stopped, **When** run B continues, **Then** run B's samplers continue unaffected

### US-003.7 — JMX Component Schema Extraction (Priority: P3)

As a web UI frontend, I need a machine-readable schema of all JMeter component properties so that the UI can render dynamic property forms for any component type.

**Source Evidence**:
- File: `schema/BeanInfoSchemaExtractor.java` — extracts component schemas via Java BeanInfo
- File: `schema/ComponentSchema.java` — schema data structure
- REST: `SchemaController.java` in web-api exposes this as API

**Acceptance Scenarios**:
1. **Given** `HTTPSamplerProxy` component class, **When** schema is extracted, **Then** returns all configurable properties with types and defaults
2. **Given** a custom JMeter plugin, **When** it's on the classpath, **Then** its schema is automatically included

### US-003.8 — HTML Report Generation (Priority: P2)

As a test analyst, I need a standalone HTML report generated after each test run so that I can share results without requiring the web UI.

**Source Evidence**:
- File: `report/HtmlReportGenerator.java`
- Test: `HtmlReportGeneratorTest.java`

**Acceptance Scenarios**:
1. **Given** a completed test run, **When** `generateReport()` is called, **Then** produces a self-contained HTML file with charts and summary statistics
2. **Given** no samples collected (empty run), **When** report is generated, **Then** produces a report noting zero samples rather than crashing

## Requirements

### Functional Requirements

**JMX Parsing (Two Parsers with Different Roles)**
- **FR-003.001**: `JmxParser` (engine-adapter) MUST parse JMeter 5.x JMX XML format (XStream-based serialization) for full execution
- **FR-003.001b**: `JmxTreeWalker` (engine-service/plan) is a lightweight StAX-based parser used for **validation only** (no class resolution, no XStream). Used at JMX import time in web-api (`TestPlanService.importJmx`) before storage. Source: `TestPlanService.java:170`
- **FR-003.002**: `JmxParser` MUST enforce `XStreamSecurityPolicy` — no arbitrary class deserialization
- **FR-003.003**: Parse errors MUST throw `JmxParseException` with the offending element identified

**Protocol Sampler Executors (78 classes)**
- **FR-003.004**: System MUST support the following samplers and components:
  - **Protocol Samplers (24)**: HTTP, FTP, JDBC, SMTP, TCP, WebSocket, gRPC, MQTT, DASH, AJP, JMS, BSF, BeanShell, JSR223, JUnit, LDAP, MailReader, OS Process, Debug, AccessLog, SOAP, SSE, HLS, WebRTC
  - **Controllers (13)**: ForEach, If, Include, Interleave, Loop, Module, OnceOnly, Random, RandomOrder, Recording, Runtime, Switch, Throughput
  - **Assertions (9)**: Base, BeanShell, Compare, HTML, JSON, JSR223, XML, XPath, XPath2
  - **Extractors (4)**: Base, Boundary, CSS, XPath
  - **Timers (6)**: Base, BeanShell, ConstantThroughput, JSR223, Poisson, Synchronizing
  - **Pre/Post Processors (8)**: BeanShellPost, BeanShellPre, DebugPost, JSR223Post, JSR223Pre, RegExUserParams, URLRewritingModifier, UserParameters
  - **Config Elements (9)**: Counter, DNSCacheManager, HTTPAuthManager, HTTPCacheManager, JDBCConnectionConfig, KeystoreConfig, LDAPDefaults, LoginConfig, RandomVariable
  - **Listeners (3)**: BackendListener, HTMLLinkParser, SimpleDataWriter
- **FR-003.005**: Each sampler executor MUST extend a common `SamplerExecutor` base
- **FR-003.006**: Executor failures MUST be recorded as sample errors, not as JVM exceptions that propagate up

**HTTP Client**
- **FR-003.007**: HC5 factory MUST support HTTP/2 via ALPN
- **FR-003.008**: Both HC4 and HC5 factories MUST implement the `HttpClientFactory` interface (engine-service)
- **FR-003.009**: HTTP client MUST support connection pooling and keep-alive

**CLI**
- **FR-003.010**: CLI MUST accept `--plan <path>`, `--non-gui`, `--duration <seconds>` flags
- **FR-003.011**: CLI MUST generate exit codes: 0=success, 1=test failure (SLA breach), 2=error

**Legacy Bridge**
- **FR-003.012**: `LegacyPropertyBridge` MUST translate jmeter.properties keys to new configuration model
- **FR-003.013**: `NoOpUIBridge` MUST silence JMeter's AWT/Swing UI requests in headless mode

## Key Entities

### ComponentSchema

| Attribute | Type | Description |
|-----------|------|-------------|
| componentClass | String | FQN of the JMeter component |
| properties | List<PropertyDef> | Configurable properties with name, type, default, description |

### JmxParseException

Checked exception; extends `RuntimeException`. Contains the offending element name for debugging.

## Edge Cases

- **JMX with unknown element types**: Logged at WARN level, element skipped — graceful degradation
- **XStream CVE mitigation**: `XStreamSecurityPolicy` maintains an explicit allowlist of JMeter classes, blocking all others — Source: `XStreamSecurityPolicy.java`
- **HC4 vs HC5 selection**: Configurable per-plan or globally via `LegacyPropertyBridge` property mapping
- **HTML report on empty run**: Must handle zero samples without NPE — Source: `HtmlReportGeneratorTest.java`

## Success Criteria

- **SC-003.001**: `JmxParser` successfully parses all JMX files in `test-plans/` corpus
- **SC-003.002**: `XStreamSecurityPolicy` rejects `java.lang.Runtime` and passes JMeter-internal classes
- **SC-003.003**: `ConcurrentRunIsolationTest` passes — no state bleed between concurrent runs
- **SC-003.004**: HC5 factory successfully negotiates HTTP/2 with HTTP/2-capable test server
- **SC-003.005**: CLI exits with code 0 on successful run, 1 on SLA breach, 2 on error
