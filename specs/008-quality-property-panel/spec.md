# Specification: PropertyPanel Decomposition

**Domain**: 008-quality-property-panel
**Created**: 2026-03-29
**Status**: Draft
**Dependencies**: 006-re-web-ui

---

## Overview

`PropertyPanel.tsx` is a 1382-line monolithic component that combines constants, six distinct form components, a schema-driven generic form, a JMX summary widget, and the exported panel shell into a single file. This decomposition splits it into 9 co-located files inside `web-ui/src/components/PropertyPanel/` without changing any behaviour. The result is a module where each file has a single clear responsibility and can be read, tested, and modified independently.

---

## Problem Statement

### Current State

`web-ui/src/components/PropertyPanel/PropertyPanel.tsx` (1382 lines) contains:
- Lines 1–97: Module-level constants (`TYPE_DISPLAY_NAMES`, `HTTP_FIELDSETS`, `HTTP_PARAM_COLUMNS`, `HTTP_FILES_COLUMNS`)
- Lines 130–499: `HTTPSamplerForm` — full HTTP sampler form (2 tabs, params/body/files tables, proxy settings)
- Lines 538–589: `UniversalFields` — name + comments inputs shared by all JMeter elements
- Lines 614–786: `ThreadGroupForm` — thread group configuration with scheduler
- Lines 806–918: `TestPlanForm` — test plan configuration with user-defined variables
- Lines 939–1002: `GenericPropertyForm` — type-dispatch router component
- Lines 1019–1071: `DynamicFormFallback` — lazy schema-fetching form fallback
- Lines 1097–1243: `PropertyForm` — Zod-schema-driven generic form renderer
- Lines 1261–1332: `JmxSummary` — regex-based JMX element counter summary
- Lines 1343–1381: `PropertyPanel` — exported root panel shell

`DynamicForm.tsx` already exists as a separate file in the same directory, confirming the established pattern of per-responsibility files.

### What Is Wrong

1. **Cognitive load**: A developer working on `ThreadGroupForm` must scroll past 480 lines of HTTP sampler code to reach it.
2. **Merge conflicts**: Any parallel edit to the file produces a conflict regardless of which component was touched.
3. **Test granularity**: There is no natural unit-test boundary — testing `JmxSummary` requires importing the entire module.
4. **IDE navigation**: Type-check and auto-complete across the full file is slower than across focused modules; code actions like "extract component" cannot be applied to already-inlined components.
5. **OSS contributor friction**: New contributors cannot understand which component to edit without reading 1382 lines.

### Impact

- Every PR touching any property panel component touches the same 1382-line file (high conflict probability).
- Adding a new sampler form requires opening a file large enough to exceed GitHub's diff rendering limit.
- Source evidence: `web-ui/src/components/PropertyPanel/PropertyPanel.tsx` lines 1–1382; constitution.md §1.4 "PropertyPanel.tsx Monolith".

---

## User Stories

### US-008.1 — Focused Form File Navigation (P1)

As a contributor adding support for a new JMeter component form, I need each form component to live in its own file so that I can open exactly the file I need to edit without navigating a 1382-line module.

**Source evidence**: `PropertyPanel.tsx` lines 130–786 — three major form components are co-located with no file boundary between them.

**Acceptance Scenarios**:
- Given the decomposition is complete, when I need to modify HTTP sampler behaviour, then I open `HTTPSamplerForm.tsx` (≤ 400 lines) and no other file requires changing.
- Given the decomposition is complete, when I need to add a scheduler field to `ThreadGroupForm`, then `ThreadGroupForm.tsx` is the only file that changes.
- Given I run `git log --follow web-ui/src/components/PropertyPanel/ThreadGroupForm.tsx`, then the history shows only commits that touched thread-group behaviour.

### US-008.2 — Isolated Component Testing (P2)

As a developer writing a Vitest unit test for `JmxSummary`, I need it in its own file so that I can import it directly without pulling in all HTTP sampler state management.

**Source evidence**: `PropertyPanel.tsx` lines 1261–1332 — `JmxSummary` is embedded in the same module as `HTTPSamplerForm` with its 6 `useState` hooks.

**Acceptance Scenarios**:
- Given `JmxSummary.tsx` exists as a standalone file, when I write `import { JmxSummary } from './JmxSummary'`, then Vitest resolves it without importing HTTPSamplerForm.
- Given I test `JmxSummary` with a known XML string, then the test does not require a mock for any HTTP sampler store state.

### US-008.3 — Reusable Constants (P2)

As a developer building a new configuration panel, I need `TYPE_DISPLAY_NAMES` and the fieldset configuration to be importable from a dedicated constants file so that I can reference them without importing form components.

**Source evidence**: `PropertyPanel.tsx` lines 1–97 — `TYPE_DISPLAY_NAMES` (34 entries), `HTTP_FIELDSETS`, `HTTP_PARAM_COLUMNS`, `HTTP_FILES_COLUMNS` are module-level constants defined at the top of a form-component file.

**Acceptance Scenarios**:
- Given `constants.ts` exists, when I import `TYPE_DISPLAY_NAMES` from `'./constants'`, then TypeScript resolves it as `Record<string, string>` without importing any React component.
- Given `HTTP_PARAM_COLUMNS` is exported from `constants.ts`, when `HTTPSamplerForm.tsx` imports it, then the runtime behaviour is identical to the current inline definition.

### US-008.4 — Unchanged Public API (P1)

As a developer consuming the `PropertyPanel` component from `App.tsx`, I need the public export contract to remain unchanged so that no consumer import paths break.

**Source evidence**: `web-ui/src/components/PropertyPanel/PropertyPanel.tsx` line 1343 — `PropertyPanel` is the only component consumed by `App.tsx`.

**Acceptance Scenarios**:
- Given the decomposition is complete, when `App.tsx` imports `PropertyPanel` from its current path, then the import resolves without modification.
- Given the full test suite runs after decomposition, then zero tests fail due to import errors.
- Given the running application renders a test plan, then the Property Panel UI is visually and functionally identical to pre-decomposition.

### US-008.5 — Incremental Future Growth (P3)

As the sole maintainer preparing for OSS release, I need a clear one-file-per-component pattern so that external contributors can add sampler forms without review guidance about where to put code.

**Source evidence**: `specs/000-re-overview/constitution.md` §1.4 — "Single contributor: All knowledge concentrated in one engineer — documentation critical for OSS contributors."

**Acceptance Scenarios**:
- Given a contributor wants to add a `JDBCSamplerForm`, then they create `JDBCSamplerForm.tsx` and add one case to `GenericPropertyForm` in `PropertyPanel.tsx` — no other file requires modification.
- Given the decomposition is complete, then the `PropertyPanel/` directory contains one file per logical concern with no file exceeding 450 lines.

---

## Functional Requirements

### FR-008.001 — File Decomposition

The 1382-line `PropertyPanel.tsx` must be split into exactly the following files within `web-ui/src/components/PropertyPanel/`:

| File | Contents | Line budget (approx.) |
|------|----------|-----------------------|
| `constants.ts` | `TYPE_DISPLAY_NAMES`, `HTTP_FIELDSETS`, `HTTP_PARAM_COLUMNS`, `HTTP_FILES_COLUMNS` | ≤ 110 |
| `UniversalFields.tsx` | `UniversalFields` component | ≤ 70 |
| `HTTPSamplerForm.tsx` | `HTTPSamplerForm` component | ≤ 400 |
| `ThreadGroupForm.tsx` | `ThreadGroupForm` component | ≤ 200 |
| `TestPlanForm.tsx` | `TestPlanForm` component | ≤ 130 |
| `PropertyForm.tsx` | `PropertyForm` component | ≤ 170 |
| `DynamicFormFallback.tsx` | `DynamicFormFallback` component | ≤ 70 |
| `JmxSummary.tsx` | `JmxSummary` component | ≤ 90 |
| `PropertyPanel.tsx` | `GenericPropertyForm` (router) + `PropertyPanel` (shell) | ≤ 120 |

**Source evidence**: `PropertyPanel.tsx` lines 1–97, 130–499, 538–589, 614–786, 806–918, 939–1002, 1019–1071, 1097–1243, 1261–1332, 1343–1381.

### FR-008.002 — Named Exports

Every extracted component and constant must be exported as a named export (not default export) from its own file. `PropertyPanel` remains the only export consumed by `App.tsx`.

### FR-008.003 — Zero Behaviour Change

No prop signatures, no store interactions, no API calls, no render output, no CSS class names, and no React key patterns may change. This is a pure structural refactor.

### FR-008.004 — Internal Import Paths

Files within the `PropertyPanel/` directory must import from each other using relative paths (`'./constants'`, `'./UniversalFields'`). No absolute path aliases may be introduced.

### FR-008.005 — DynamicForm.tsx Unchanged

The existing `DynamicForm.tsx` file must not be modified. `DynamicFormFallback.tsx` imports `DynamicForm` from `'./DynamicForm'`.

### FR-008.006 — Schemas Directory Unchanged

The `schemas/` subdirectory and `schemas/index.ts` must not be modified.

### FR-008.007 — CSS File Unchanged

`PropertyPanel.css` must not be modified. The CSS import must remain in `PropertyPanel.tsx`.

---

## Success Criteria

### SC-008.001 — File Count

After decomposition, `ls web-ui/src/components/PropertyPanel/*.tsx` lists exactly: `DynamicForm.tsx`, `DynamicFormFallback.tsx`, `HTTPSamplerForm.tsx`, `JmxSummary.tsx`, `PropertyForm.tsx`, `PropertyPanel.tsx`, `TestPlanForm.tsx`, `ThreadGroupForm.tsx`, `UniversalFields.tsx` — 9 files. `constants.ts` adds one additional `.ts` file.

### SC-008.002 — TypeScript Compilation Clean

`npx tsc --noEmit` in `web-ui/` reports zero errors after decomposition.

### SC-008.003 — Vitest Suite Green

`npm run test` in `web-ui/` reports zero failures after decomposition.

### SC-008.004 — Line Budget Compliance

No extracted file (excluding `PropertyPanel.tsx`) exceeds 450 lines. `PropertyPanel.tsx` does not exceed 130 lines.

### SC-008.005 — No New Dependencies

`web-ui/package.json` is not modified. No new npm packages are introduced.

### SC-008.006 — Unchanged Exports

The type of `PropertyPanel` exported from `PropertyPanel.tsx` is structurally identical to the pre-decomposition export (same props interface, same displayName if set).

---

## Non-Functional Requirements

### NFR-008.001 — Maintainability

Each extracted file must have a single, clearly named responsibility. File names must exactly match their primary export name (e.g., `HTTPSamplerForm.tsx` exports `HTTPSamplerForm`).

### NFR-008.002 — No Runtime Overhead

The decomposition must not introduce any additional React context providers, higher-order components, or memoization boundaries that did not exist before.

### NFR-008.003 — TypeScript Strict Mode Preserved

All extracted files must compile cleanly under the existing `tsconfig.json` strict settings. No `@ts-ignore` or `@ts-expect-error` suppressions may be introduced.

### NFR-008.004 — Vite HMR Compatibility

Hot Module Replacement must continue to work correctly. Named re-exports in barrel files are acceptable only if they do not break HMR boundaries.

---

## Out of Scope

- Refactoring any component's internal logic, state management, or side effects.
- Changing prop types or adding new props.
- Converting class components (none exist) or migrating hook patterns.
- Adding new form components for unimplemented JMeter types.
- Introducing a new barrel `index.ts` unless one already exists.
- Changing the schemas directory structure.
- Performance optimisations (memoization, lazy loading).
- Accessibility improvements.
- CSS changes.
