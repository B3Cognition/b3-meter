# Specification: Web UI (React Frontend)

**Domain**: 006-re-web-ui
**Created**: 2026-03-29
**Status**: Draft (Reverse-Engineered)
**Dependencies**: 005-re-web-api

## Overview

The web-ui is a React 19 + TypeScript + Vite frontend that provides a visual interface for creating, editing, and running JMeter load tests. Key features include: a drag-and-drop test plan tree editor, a dynamic property form panel for all 27+ JMeter component types, a real-time live dashboard with charts (response time, throughput, error rate), distributed mode configuration, proxy recorder, plugin manager, SLA discovery, A/B performance testing, and chaos load generation.

**Source Files Analyzed**:
- `web-ui/src/components/TreeEditor/TestPlanTree.tsx`
- `web-ui/src/components/PropertyPanel/PropertyPanel.tsx`
- `web-ui/src/components/Dashboard/LiveDashboard.tsx`
- `web-ui/src/components/DistributedConfig/DistributedConfig.tsx`
- `web-ui/src/components/Innovation/SLADiscovery.tsx` (hotspot #1)
- `web-ui/src/components/Innovation/ABPerformance.tsx`
- `web-ui/src/components/Innovation/ChaosLoad.tsx`
- `web-ui/src/types/api.ts`
- `web-ui/src/App.tsx`

## Complexity Estimation

| Metric | Value | Implication |
|--------|-------|-------------|
| **Files** | 43 TSX + 30 TS + 12 CSS | Large frontend |
| **Lines of Code** | ~25,000 est. | Full-featured SPA |
| **Git Commits** | SLADiscovery.tsx hotspot #1 | Active UI development |
| **Contributors** | 1 | Full ownership |
| **Hotspot Score** | Very High | SLADiscovery in top hotspot |

**Estimated Complexity**: High
**Rationale**: Large component count, complex state management, real-time charting, dynamic form generation from backend schema, drag-and-drop tree editor, and three "Innovation" feature components.

## User Scenarios & Testing

### US-006.1 — Create and Edit Test Plan (Priority: P1)

As a load tester, I need to visually build a test plan tree (Thread Group → Samplers → Config Elements → Listeners) so that I can define a realistic load test without writing XML.

**Source Evidence**:
- File: `TreeEditor/TestPlanTree.tsx` — draggable tree editor
- File: `TreeEditor/NodeRenderer.tsx` — individual node rendering
- File: `TreeEditor/NodeContextMenu.tsx` — right-click actions

**Acceptance Scenarios**:
1. **Given** an empty test plan, **When** user adds a Thread Group, **Then** a new node appears in the tree
2. **Given** an HTTP Sampler node, **When** user right-clicks, **Then** context menu shows Add Child / Duplicate / Delete options
3. **Given** two nodes, **When** user drags one onto another, **Then** the dragged node becomes a child of the target
4. **Given** the tree has unsaved changes, **When** user navigates away, **Then** a confirmation dialog warns about unsaved changes

### US-006.2 — Configure Component Properties (Priority: P1)

As a load tester, I need a dynamic property form that shows the correct fields for each component type so that I can configure samplers, extractors, assertions, and controllers without knowing their XML structure.

**Source Evidence**:
- File: `PropertyPanel/PropertyPanel.tsx` — main property panel (1268+ lines)
- File: `PropertyPanel/DynamicForm.tsx` — schema-driven form rendering
- File: `PropertyPanel/schemas/` — Zod schemas for all component types

**Acceptance Scenarios**:
1. **Given** an HTTP Sampler is selected, **When** the property panel opens, **Then** fields for URL, method, headers, body, and timeout are shown
2. **Given** a Response Time Extractor is selected, **When** the panel opens, **Then** fields for match number, default value, and variable name are shown
3. **Given** the user changes a field value, **When** the form is saved, **Then** the JMX tree is updated and the plan is auto-saved

### US-006.3 — Real-Time Live Dashboard (Priority: P1)

As a test runner, I need to see response time, throughput, and error rate charts updating in real time while the test runs so that I can react immediately to performance regressions.

**Source Evidence**:
- File: `Dashboard/LiveDashboard.tsx` — SSE consumer + chart container
- File: `Dashboard/ResponseTimeChart.tsx` — time-series response time chart
- File: `Dashboard/ThroughputChart.tsx` — requests-per-second chart
- File: `Dashboard/ErrorRateChart.tsx` — error percentage chart
- File: `Dashboard/SummaryReport.tsx` — aggregate stats table

**Acceptance Scenarios**:
1. **Given** a running test, **When** the dashboard opens, **Then** charts begin updating with SSE data within 1 second
2. **Given** a test has been running 60 seconds, **When** the chart renders, **Then** shows a time-series with 12 data points (one per 5s interval)
3. **Given** the test stops, **When** SSE stream closes, **Then** charts freeze at the final state without error
4. **Given** error rate exceeds 10%, **When** visualized, **Then** error rate chart line turns red

### US-006.4 — View Detailed Request Log (Priority: P2)

As a test debugger, I need to see individual request/response details so that I can diagnose why specific requests are failing.

**Source Evidence**:
- File: `Dashboard/ViewResultsTree.tsx` — hierarchical results tree
- File: `Dashboard/AggregateReport.tsx` — per-sampler aggregate statistics

**Acceptance Scenarios**:
1. **Given** a running test, **When** user opens "View Results Tree", **Then** each sample shows URL, response code, response time, response body
2. **Given** a failed sample, **When** selected, **Then** error details (assertion failures, connection errors) are shown
3. **Given** 1000+ samples, **When** view is open, **Then** UI remains responsive (virtualized rendering)

### US-006.5 — Distributed Mode Configuration (Priority: P2)

As a distributed test operator, I need to select worker nodes and configure virtual user distribution before starting a distributed run so that I can run higher load than a single node supports.

**Source Evidence**:
- File: `DistributedConfig/DistributedConfig.tsx` — distributed run setup
- Test: `src/__tests__/DistributedConfig.test.tsx`

**Acceptance Scenarios**:
1. **Given** registered workers in the system, **When** user opens Distributed Config, **Then** available workers are listed with health indicators
2. **Given** 3 workers selected, **When** user sets 300 total VUs, **Then** each worker shows auto-calculated VU allocation (100/100/100)
3. **Given** distributed mode is configured, **When** Run is clicked, **Then** API is called with `distributed=true` and worker IDs

### US-006.6 — SLA Discovery (Priority: P1, Innovation Feature)

As a performance engineer, I need the UI to automatically suggest SLA thresholds based on baseline run results so that I don't have to manually determine what "good" performance looks like.

**Source Evidence**:
- File: `Innovation/SLADiscovery.tsx` — hotspot #1 (most-changed file)
- File: `web-ui/load-testing-research.md` — research backing this feature

**Acceptance Scenarios**:
1. **Given** a completed baseline run, **When** user opens SLA Discovery, **Then** suggests p95, p99, error rate thresholds based on observed distribution
2. **Given** suggested thresholds, **When** user accepts, **Then** SLA configuration is added to the test plan
3. **Given** SLA thresholds are active, **When** a subsequent run violates them, **Then** SLA violation is highlighted in the dashboard

### US-006.7 — Upload Existing JMX Plan (Priority: P1)

As a JMeter user with existing test plans, I need to upload a JMX file so that I can run my existing tests without recreating them in the UI.

**Source Evidence**:
- File: `JmxUpload/JmxUpload.tsx` — JMX file upload component

**Acceptance Scenarios**:
1. **Given** the user drops a `.jmx` file onto the upload area, **When** it is processed, **Then** the test plan tree is populated from the JMX structure
2. **Given** an invalid XML file, **When** uploaded, **Then** error message explains the parse failure
3. **Given** a valid JMX, **When** uploaded, **Then** plan is saved via API and appears in the plan list

### US-006.8 — A/B Performance Comparison (Priority: P2, Innovation Feature)

As a developer, I need to compare two test runs side by side so that I can validate that a code change did not regress performance.

**Source Evidence**:
- File: `Innovation/ABPerformance.tsx`

**Acceptance Scenarios**:
1. **Given** two completed runs, **When** user initiates A/B comparison, **Then** side-by-side charts show response time and throughput differences
2. **Given** run B is 20% slower than run A, **When** displayed, **Then** the delta is highlighted in red

### US-006.9 — Chaos Load Generation (Priority: P3, Innovation Feature)

As a resilience tester, I need to inject irregular, bursty, or spike traffic patterns so that I can test how my service responds to real-world irregular load.

**Source Evidence**:
- File: `Innovation/ChaosLoad.tsx`

**Acceptance Scenarios**:
1. **Given** chaos mode selected, **When** test runs, **Then** arrival rate follows a configured irregular pattern (spikes, troughs, bursts)

## Requirements

### Functional Requirements

**Test Plan Editor**
- **FR-006.001**: Tree editor MUST support all 27+ JMeter element types (Thread Groups, HTTP/FTP/JDBC/gRPC samplers, controllers, extractors, assertions, listeners)
- **FR-006.002**: Property forms MUST be dynamically generated from backend schema API (not hardcoded)
- **FR-006.003**: JMX import/export MUST preserve all element properties round-trip (no data loss)

**Dashboard**
- **FR-006.004**: Charts MUST update within 1s of SSE event receipt
- **FR-006.005**: Dashboard MUST handle test durations up to 24 hours without memory leak
- **FR-006.006**: View Results Tree MUST virtualize when sample count > 100

**Authentication**
- **FR-006.007**: UI MUST handle JWT expiry by refreshing token silently before retrying failed request
- **FR-006.008**: On 401 after refresh failure, UI MUST redirect to login

**Accessibility**
- **FR-006.009**: Application MUST pass WCAG 2.1 AA automated accessibility checks
- Source: `src/__tests__/accessibility.test.tsx` — dedicated a11y test file

## Key Entities

### API Types (`web-ui/src/types/api.ts`)

Key types matching backend REST contracts:
- `TestPlanDto` — plan with id, name, revisionId, jmxContent
- `TestRunDto` — run with id, planId, status, startedAt, completedAt
- `MetricsDto` — snapshot with samplerLabel, sampleCount, avgResponseTime, p95, p99, errorRate
- `SlaStatusDto` — verdict (PASS/VIOLATED), violations list

## Edge Cases

- **SSE reconnection**: If SSE connection drops, client should reconnect with `Last-Event-ID` header
- **PropertyPanel 1268+ lines**: Largest file in frontend — complexity risk; candidate for splitting
- **Offline editor**: Tree editor should work without network; only sync on save
- **JMX round-trip fidelity**: JMX imported and re-exported must be functionally identical
- **Large response bodies in ViewResultsTree**: Must be truncated in display to avoid DOM overflow

## Success Criteria

- **SC-006.001**: `accessibility.test.tsx` passes with zero violations
- **SC-006.002**: All `__tests__/*.test.tsx` pass (11 test files)
- **SC-006.003**: SLADiscovery correctly suggests thresholds based on 3 sample baseline runs
- **SC-006.004**: LiveDashboard renders 1000 data points without frame drops (< 16ms/frame)
- **SC-006.005**: JMX round-trip: parse → render → export produces functionally equivalent XML
