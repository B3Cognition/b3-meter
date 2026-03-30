# Implementation Plan: PropertyPanel Decomposition

**Domain**: 008-quality-property-panel
**Created**: 2026-03-29
**Status**: Draft

---

## Approach

This is a pure structural decomposition with zero behaviour change. The strategy is to extract one component at a time from the bottom of the dependency graph upward, verifying TypeScript compiles cleanly after each extraction before proceeding to the next. The dependency order (from least to most depended-upon) is:

1. `constants.ts` — no React imports, no component deps
2. `UniversalFields.tsx` — depends only on store and React
3. `JmxSummary.tsx` — depends only on store and React
4. `DynamicFormFallback.tsx` — depends on `DynamicForm.tsx` (already separate)
5. `PropertyForm.tsx` — depends on constants and schemas
6. `TestPlanForm.tsx` — depends on store and React
7. `ThreadGroupForm.tsx` — depends on store and React
8. `HTTPSamplerForm.tsx` — depends on constants and store
9. `PropertyPanel.tsx` (trimmed) — depends on all of the above

After each extraction, run `npx tsc --noEmit` to confirm no import path errors are introduced.

The final `PropertyPanel.tsx` retains only `GenericPropertyForm` (the type-dispatch router) and the exported `PropertyPanel` shell, plus the CSS import.

---

## File Changes

### Files to Create

| File | Source Lines in PropertyPanel.tsx | Action |
|------|----------------------------------|--------|
| `web-ui/src/components/PropertyPanel/constants.ts` | 1–97 | Extract `TYPE_DISPLAY_NAMES`, `HTTP_FIELDSETS`, `HTTP_PARAM_COLUMNS`, `HTTP_FILES_COLUMNS`; add named exports |
| `web-ui/src/components/PropertyPanel/UniversalFields.tsx` | 538–589 | Move `UniversalFields`; add React + store imports; named export |
| `web-ui/src/components/PropertyPanel/JmxSummary.tsx` | 1261–1332 | Move `JmxSummary`; add React + store imports; named export |
| `web-ui/src/components/PropertyPanel/DynamicFormFallback.tsx` | 1019–1071 | Move `DynamicFormFallback`; import `DynamicForm` from `'./DynamicForm'`; named export |
| `web-ui/src/components/PropertyPanel/PropertyForm.tsx` | 1097–1243 | Move `PropertyForm`; import from `'./constants'` and `'../schemas'` as needed; named export |
| `web-ui/src/components/PropertyPanel/TestPlanForm.tsx` | 806–918 | Move `TestPlanForm`; add React + store imports; named export |
| `web-ui/src/components/PropertyPanel/ThreadGroupForm.tsx` | 614–786 | Move `ThreadGroupForm`; add React + store imports; named export |
| `web-ui/src/components/PropertyPanel/HTTPSamplerForm.tsx` | 130–499 | Move `HTTPSamplerForm`; import `HTTP_FIELDSETS`, `HTTP_PARAM_COLUMNS`, `HTTP_FILES_COLUMNS` from `'./constants'`; named export |

### Files to Modify

| File | Change |
|------|--------|
| `web-ui/src/components/PropertyPanel/PropertyPanel.tsx` | Remove all extracted code (lines 1–97, 130–499, 538–589, 614–786, 806–918, 1019–1071, 1097–1243, 1261–1332); add import statements for all extracted modules; retain `GenericPropertyForm` (lines 939–1002), `PropertyPanel` (lines 1343–1381), CSS import |

### Files to Leave Unchanged

| File | Reason |
|------|--------|
| `web-ui/src/components/PropertyPanel/DynamicForm.tsx` | Already separate; not touched |
| `web-ui/src/components/PropertyPanel/PropertyPanel.css` | CSS only; not touched |
| `web-ui/src/components/PropertyPanel/schemas/**` | Schema registry; not touched |
| `web-ui/src/App.tsx` | Imports `PropertyPanel` by path — path unchanged |
| All test files that import from `PropertyPanel` | May need import path update if they import internal components directly — see Testing Strategy |

---

## Dependencies / Prerequisites

1. Node.js environment with `web-ui` dependencies installed (`npm install` in `web-ui/`).
2. TypeScript strict mode currently passing — confirm with `npx tsc --noEmit` before starting.
3. Existing Vitest suite currently passing — confirm with `npm run test` before starting.
4. No in-flight PRs touching `PropertyPanel.tsx` — check git status to avoid rebase conflicts.

---

## Extraction Sequence Detail

### Step 1: Extract `constants.ts`

Create `constants.ts` with all four constants. Add `export` keyword before each `const`. Verify no React import is needed (these are plain objects/arrays). Add `import type { ColumnDef } from '...'` if the column definition type requires it.

In `PropertyPanel.tsx`: remove lines 1–97; add `import { TYPE_DISPLAY_NAMES, HTTP_FIELDSETS, HTTP_PARAM_COLUMNS, HTTP_FILES_COLUMNS } from './constants';`

Run: `npx tsc --noEmit`

### Step 2: Extract `UniversalFields.tsx`

Create `UniversalFields.tsx`. Copy the component function and its local state. Import `useTestPlanStore` and React hooks from their current paths (check the existing imports block at the top of `PropertyPanel.tsx` for exact paths).

In `PropertyPanel.tsx`: remove lines 538–589; add `import { UniversalFields } from './UniversalFields';`

Run: `npx tsc --noEmit`

### Step 3: Extract `JmxSummary.tsx`

Create `JmxSummary.tsx`. Import `useUiStore` from its current path (check `PropertyPanel.tsx` imports for the store reference). No prop drilling required — the component reads directly from store.

In `PropertyPanel.tsx`: remove lines 1261–1332; add `import { JmxSummary } from './JmxSummary';`

Run: `npx tsc --noEmit`

### Step 4: Extract `DynamicFormFallback.tsx`

Create `DynamicFormFallback.tsx`. Import `DynamicForm` from `'./DynamicForm'`. Import `useState`, `useEffect`, and fetch utilities from their current locations.

In `PropertyPanel.tsx`: remove lines 1019–1071; add `import { DynamicFormFallback } from './DynamicFormFallback';`

Run: `npx tsc --noEmit`

### Step 5: Extract `PropertyForm.tsx`

Create `PropertyForm.tsx`. Import `HTTP_FIELDSETS` (or whatever fieldset config it uses) from `'./constants'`. Import the schema registry from `'./schemas'`. This component uses `getFieldsets()` — confirm the function is either defined inline in the component or imported from `schemas/index.ts`.

In `PropertyPanel.tsx`: remove lines 1097–1243; add `import { PropertyForm } from './PropertyForm';`

Run: `npx tsc --noEmit`

### Step 6: Extract `TestPlanForm.tsx`

Create `TestPlanForm.tsx`. Import React hooks, `useTestPlanStore`, and any form utilities. Import `UserDefinedVariables` or `EditableTable` from their current locations if used inline.

In `PropertyPanel.tsx`: remove lines 806–918; add `import { TestPlanForm } from './TestPlanForm';`

Run: `npx tsc --noEmit`

### Step 7: Extract `ThreadGroupForm.tsx`

Create `ThreadGroupForm.tsx`. Import `useWatch`, `useForm`, `zodResolver`, and `useTestPlanStore`. The component has `useWatch` for `infiniteValue` and `schedulerValue` conditionals — these stay inside the component.

In `PropertyPanel.tsx`: remove lines 614–786; add `import { ThreadGroupForm } from './ThreadGroupForm';`

Run: `npx tsc --noEmit`

### Step 8: Extract `HTTPSamplerForm.tsx`

Create `HTTPSamplerForm.tsx`. Import `HTTP_FIELDSETS`, `HTTP_PARAM_COLUMNS`, `HTTP_FILES_COLUMNS` from `'./constants'`. Import `EditableTable`, `useForm`, `zodResolver`, `useTestPlanStore`. This is the largest extraction (~370 lines) — proceed carefully with all import paths.

In `PropertyPanel.tsx`: remove lines 130–499; add `import { HTTPSamplerForm } from './HTTPSamplerForm';`

Run: `npx tsc --noEmit`

### Step 9: Verify and trim `PropertyPanel.tsx`

After all extractions, `PropertyPanel.tsx` should contain:
- CSS import
- All new component imports (8 lines)
- `GenericPropertyForm` (lines 939–1002, ~63 lines)
- `PropertyPanel` (lines 1343–1381, ~39 lines)
- Total: ≤ 120 lines

Audit the imports block to remove any now-unused imports.

Run: `npx tsc --noEmit && npm run test`

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Import path for a shared utility (e.g., `EditableTable`, `useTestPlanStore`) differs from assumption | Medium | TypeScript error immediately visible | Run `tsc --noEmit` after each step; correct path before proceeding |
| A type imported via the module-level `import` block is used by multiple extracted components | Medium | Each file needs its own import | Review all type usages before extraction; add per-file imports |
| `getFieldsets()` helper is defined inside `PropertyPanel.tsx` scope (not in schemas) | Low | Must move it to `PropertyForm.tsx` or `constants.ts` | Read lines 1097–1110 carefully before Step 5 |
| An existing test file imports a component by its old path from `PropertyPanel.tsx` | Low | Vitest import resolution error | Check test files for direct internal imports; update if found |
| Circular import created if `PropertyPanel.tsx` is imported by an extracted file | Very Low | TypeScript will error | Extracted files must never import from `PropertyPanel.tsx` |

---

## Rollback

Since this is a refactor of a single file into multiple files:

1. All extracted files are new — `git rm` them.
2. Restore `PropertyPanel.tsx` from git: `git checkout HEAD -- web-ui/src/components/PropertyPanel/PropertyPanel.tsx`
3. Verify: `npx tsc --noEmit && npm run test`

No database migrations, no API changes, no configuration changes — rollback is atomic.

---

## Testing Strategy

### Pre-decomposition Baseline

Before starting, capture:
```
npx tsc --noEmit 2>&1 | wc -l   # should be 0 errors
npm run test -- --reporter=verbose 2>&1 | tail -20  # capture pass/fail counts
```

### During Decomposition

Run `npx tsc --noEmit` after each of the 9 steps. Do not proceed to the next step if TypeScript reports errors.

### Post-decomposition Verification

1. `npx tsc --noEmit` — zero errors
2. `npm run test` — same pass count as baseline
3. Manual smoke test in browser: open the app, select an HTTP Sampler node, verify the property panel renders and saves correctly
4. Manual smoke test: select a Thread Group, verify scheduler toggle works

### Existing Test File Updates

Search for test files that import from `PropertyPanel.tsx` directly:
- If any test imports `UniversalFields`, `JmxSummary`, `DynamicFormFallback`, `PropertyForm`, `TestPlanForm`, `ThreadGroupForm`, or `HTTPSamplerForm` from `'./PropertyPanel'` or `'../PropertyPanel/PropertyPanel'`, update those imports to point to the new file.
- If any test imports only `PropertyPanel` (the root component), no change is needed.

### No New Tests Required

This is a structural refactor. New unit tests targeting individual extracted components are valuable but are out of scope for this task (they belong in a follow-on quality task).
