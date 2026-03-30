# Tasks: PropertyPanel Decomposition

**Domain**: 008-quality-property-panel
**Created**: 2026-03-29
**Status**: Complete — all 12 tasks done, tsc clean, tests unchanged from baseline

---

## Task List

### T-008-001 — Establish Pre-decomposition Baseline

**Type**: test
**Files affected**: none (read-only)
**Complexity**: S
**Dependencies**: none

**Description**: Confirm the codebase is in a clean state before starting any extraction. Record baseline metrics.

**Steps**:
1. Run `cd web-ui && npx tsc --noEmit` — must report zero errors.
2. Run `npm run test` — record pass/fail counts.
3. Run `git status` in `web-ui/src/components/PropertyPanel/` — confirm `PropertyPanel.tsx` has no uncommitted changes.

**Acceptance Criteria**:
- TypeScript compilation exits with code 0.
- Vitest exits with code 0 (all tests pass).
- `PropertyPanel.tsx` is tracked by git with no pending modifications.

---

### T-008-002 — Extract `constants.ts`

**Type**: refactor
**Files affected**:
- `web-ui/src/components/PropertyPanel/constants.ts` (create)
- `web-ui/src/components/PropertyPanel/PropertyPanel.tsx` (modify)
**Complexity**: S
**Dependencies**: T-008-001

**Description**: Move the four module-level constants from `PropertyPanel.tsx` lines 1–97 into a new `constants.ts` file.

**Steps**:
1. Create `constants.ts`.
2. Copy `TYPE_DISPLAY_NAMES` (lines 9–46), `HTTP_FIELDSETS` (lines 48–65), `HTTP_PARAM_COLUMNS` (lines 67–80), `HTTP_FILES_COLUMNS` (lines 82–97) into the new file. Add `export` keyword to each `const`.
3. Add any required type imports to `constants.ts` (e.g., `ColumnDef` type if column defs are typed).
4. In `PropertyPanel.tsx`: remove lines 1–97; add `import { TYPE_DISPLAY_NAMES, HTTP_FIELDSETS, HTTP_PARAM_COLUMNS, HTTP_FILES_COLUMNS } from './constants';` at the top of the file.
5. Run `npx tsc --noEmit` — must report zero errors.

**Acceptance Criteria**:
- `constants.ts` exports all four constants with correct TypeScript types.
- `PropertyPanel.tsx` imports all four from `'./constants'`.
- `npx tsc --noEmit` exits with code 0.
- No logic changes (pure move).

---

### T-008-003 — Extract `UniversalFields.tsx`

**Type**: refactor
**Files affected**:
- `web-ui/src/components/PropertyPanel/UniversalFields.tsx` (create)
- `web-ui/src/components/PropertyPanel/PropertyPanel.tsx` (modify)
**Complexity**: S
**Dependencies**: T-008-002

**Description**: Move the `UniversalFields` component (lines 538–589) into its own file.

**Steps**:
1. Create `UniversalFields.tsx`.
2. Identify the exact imports needed by `UniversalFields` from the current `PropertyPanel.tsx` imports block (React, `useState`, `useEffect`, `useTestPlanStore` or equivalent, UI component library imports for inputs).
3. Copy the component function into `UniversalFields.tsx` with all required imports. Add `export` keyword.
4. Remove lines 538–589 from `PropertyPanel.tsx`. Add `import { UniversalFields } from './UniversalFields';`.
5. Run `npx tsc --noEmit`.

**Acceptance Criteria**:
- `UniversalFields.tsx` is a self-contained file with all its own imports.
- `PropertyPanel.tsx` does not inline any `UniversalFields` code.
- `npx tsc --noEmit` exits with code 0.

---

### T-008-004 — Extract `JmxSummary.tsx`

**Type**: refactor
**Files affected**:
- `web-ui/src/components/PropertyPanel/JmxSummary.tsx` (create)
- `web-ui/src/components/PropertyPanel/PropertyPanel.tsx` (modify)
**Complexity**: S
**Dependencies**: T-008-002

**Description**: Move the `JmxSummary` component (lines 1261–1332) into its own file.

**Steps**:
1. Create `JmxSummary.tsx`.
2. Identify imports needed: React, `useUiStore` (or equivalent for `planXmlMap`), any regex helpers if extracted as constants.
3. Copy the component function. Add `export` keyword.
4. Remove lines 1261–1332 from `PropertyPanel.tsx`. Add `import { JmxSummary } from './JmxSummary';`.
5. Run `npx tsc --noEmit`.

**Acceptance Criteria**:
- `JmxSummary.tsx` reads `planXmlMap` from the same store as before.
- `npx tsc --noEmit` exits with code 0.

---

### T-008-005 — Extract `DynamicFormFallback.tsx`

**Type**: refactor
**Files affected**:
- `web-ui/src/components/PropertyPanel/DynamicFormFallback.tsx` (create)
- `web-ui/src/components/PropertyPanel/PropertyPanel.tsx` (modify)
**Complexity**: S
**Dependencies**: T-008-002

**Description**: Move the `DynamicFormFallback` component (lines 1019–1071) into its own file.

**Steps**:
1. Create `DynamicFormFallback.tsx`.
2. Identify imports: React, `useState`, `useEffect`, `DynamicForm` from `'./DynamicForm'`, any fetch utility or API base URL constant.
3. Copy the component function. Add `export` keyword.
4. Remove lines 1019–1071 from `PropertyPanel.tsx`. Add `import { DynamicFormFallback } from './DynamicFormFallback';`.
5. Run `npx tsc --noEmit`.

**Acceptance Criteria**:
- `DynamicFormFallback.tsx` imports `DynamicForm` from `'./DynamicForm'` (not from `PropertyPanel.tsx`).
- The `GET /api/v1/schemas/{type}` fetch URL is unchanged.
- `npx tsc --noEmit` exits with code 0.

---

### T-008-006 — Extract `PropertyForm.tsx`

**Type**: refactor
**Files affected**:
- `web-ui/src/components/PropertyPanel/PropertyForm.tsx` (create)
- `web-ui/src/components/PropertyPanel/PropertyPanel.tsx` (modify)
**Complexity**: M
**Dependencies**: T-008-002

**Description**: Move the `PropertyForm` component (lines 1097–1243) into its own file. This is the Zod-schema-driven generic renderer — it has the most complex internal logic of all extracted components.

**Steps**:
1. Read lines 1097–1110 carefully to determine whether `getFieldsets()` is defined inline or imported.
2. Create `PropertyForm.tsx`.
3. Identify imports: React, `useForm`, `zodResolver`, `z` from zod, `HTTP_FIELDSETS` (or equivalent fieldset config) from `'./constants'`, the schema registry from `'./schemas'`, any UI component library imports (form fields, fieldset wrapper, etc.).
4. Copy `PropertyForm` and any helper functions defined in its closure (e.g., `getFieldsets`). Add `export` keyword.
5. Remove lines 1097–1243 from `PropertyPanel.tsx`. Add `import { PropertyForm } from './PropertyForm';`.
6. Run `npx tsc --noEmit`.

**Acceptance Criteria**:
- `PropertyForm.tsx` handles all field type introspection (`ZodNumber`, `ZodBoolean`, `ZodEnum`, `ZodString`) identically to the original.
- `getFieldsets()` (if it exists) is co-located with `PropertyForm` in `PropertyForm.tsx`.
- `npx tsc --noEmit` exits with code 0.

---

### T-008-007 — Extract `TestPlanForm.tsx`

**Type**: refactor
**Files affected**:
- `web-ui/src/components/PropertyPanel/TestPlanForm.tsx` (create)
- `web-ui/src/components/PropertyPanel/PropertyPanel.tsx` (modify)
**Complexity**: S
**Dependencies**: T-008-002

**Description**: Move the `TestPlanForm` component (lines 806–918) into its own file.

**Steps**:
1. Create `TestPlanForm.tsx`.
2. Identify imports: React, form hooks, `useTestPlanStore`, `UserDefinedVariables` or `EditableTable` if used, any checkbox/field UI components.
3. Copy the component function. Add `export` keyword.
4. Remove lines 806–918 from `PropertyPanel.tsx`. Add `import { TestPlanForm } from './TestPlanForm';`.
5. Run `npx tsc --noEmit`.

**Acceptance Criteria**:
- Functional mode checkboxes and `UserDefinedVariables` table render correctly (verified manually in browser).
- `npx tsc --noEmit` exits with code 0.

---

### T-008-008 — Extract `ThreadGroupForm.tsx`

**Type**: refactor
**Files affected**:
- `web-ui/src/components/PropertyPanel/ThreadGroupForm.tsx` (create)
- `web-ui/src/components/PropertyPanel/PropertyPanel.tsx` (modify)
**Complexity**: M
**Dependencies**: T-008-002

**Description**: Move the `ThreadGroupForm` component (lines 614–786) into its own file. Particular care is needed for the `useWatch` conditional rendering for `infiniteValue` and `schedulerValue`.

**Steps**:
1. Create `ThreadGroupForm.tsx`.
2. Identify imports: React, `useForm`, `useWatch`, `zodResolver`, `useTestPlanStore`, `useEffect`, error-action radio components, scheduler fields, any constants from `'./constants'`.
3. Copy the component function. Confirm `useWatch` calls are inside the component (not in module scope). Add `export` keyword.
4. Remove lines 614–786 from `PropertyPanel.tsx`. Add `import { ThreadGroupForm } from './ThreadGroupForm';`.
5. Run `npx tsc --noEmit`.

**Acceptance Criteria**:
- The infinite loop toggle correctly shows/hides the loop count field (verified manually).
- The scheduler toggle correctly shows/hides scheduler fields (verified manually).
- `npx tsc --noEmit` exits with code 0.

---

### T-008-009 — Extract `HTTPSamplerForm.tsx`

**Type**: refactor
**Files affected**:
- `web-ui/src/components/PropertyPanel/HTTPSamplerForm.tsx` (create)
- `web-ui/src/components/PropertyPanel/PropertyPanel.tsx` (modify)
**Complexity**: M
**Dependencies**: T-008-002

**Description**: Move the `HTTPSamplerForm` component (lines 130–499) into its own file. This is the largest extraction (~370 lines) with 6 `useState` hooks, 1 `useEffect`, and `EditableTable` usage for params and file uploads.

**Steps**:
1. Create `HTTPSamplerForm.tsx`.
2. Import `HTTP_FIELDSETS`, `HTTP_PARAM_COLUMNS`, `HTTP_FILES_COLUMNS` from `'./constants'`.
3. Identify all other imports: React hooks, `useForm`, `zodResolver`, `useTestPlanStore`, `EditableTable`, tab components, textarea, proxy field components, connection timeout fields.
4. Copy the component function with all 6 `useState` declarations and the `useEffect`. Add `export` keyword.
5. Remove lines 130–499 from `PropertyPanel.tsx`. Add `import { HTTPSamplerForm } from './HTTPSamplerForm';`.
6. Run `npx tsc --noEmit`.

**Acceptance Criteria**:
- Basic tab and Advanced tab both render and save correctly (verified manually).
- Params table, body data textarea, and file upload table all function identically.
- `npx tsc --noEmit` exits with code 0.

---

### T-008-010 — Trim and Finalise `PropertyPanel.tsx`

**Type**: refactor
**Files affected**:
- `web-ui/src/components/PropertyPanel/PropertyPanel.tsx` (modify)
**Complexity**: S
**Dependencies**: T-008-003, T-008-004, T-008-005, T-008-006, T-008-007, T-008-008, T-008-009

**Description**: After all 8 extractions, `PropertyPanel.tsx` should contain only `GenericPropertyForm` and `PropertyPanel`. Audit and clean up the imports block.

**Steps**:
1. Review the imports block — remove any import that is no longer referenced in the remaining ~120 lines.
2. Confirm the file contains exactly: CSS import, 8 component imports, `GenericPropertyForm`, `PropertyPanel`.
3. Run `npx tsc --noEmit`.
4. Confirm file is ≤ 130 lines.

**Acceptance Criteria**:
- `PropertyPanel.tsx` is ≤ 130 lines.
- No unused imports remain.
- `npx tsc --noEmit` exits with code 0.

---

### T-008-011 — Update Affected Test Files

**Type**: test
**Files affected**: any test file that imports internal components from `PropertyPanel.tsx`
**Complexity**: S
**Dependencies**: T-008-010

**Description**: Search for test files that import internal components from `PropertyPanel.tsx` and update their import paths.

**Steps**:
1. Run: `grep -r "from.*PropertyPanel" web-ui/src --include="*.test.*" --include="*.spec.*"` to find all test imports.
2. For each test importing `UniversalFields`, `JmxSummary`, `HTTPSamplerForm`, `ThreadGroupForm`, `TestPlanForm`, `PropertyForm`, or `DynamicFormFallback` from the old location, update the import to point to the new file.
3. For tests importing only `PropertyPanel` (the root), no change is needed.
4. Run `npm run test`.

**Acceptance Criteria**:
- `npm run test` exits with code 0.
- Zero import resolution errors in the test output.

---

### T-008-012 — Final Validation

**Type**: test
**Files affected**: none
**Complexity**: S
**Dependencies**: T-008-011

**Description**: Run the complete validation suite and confirm the decomposition is complete and correct.

**Steps**:
1. Run `npx tsc --noEmit` — must exit 0.
2. Run `npm run test` — must exit 0 with same pass count as T-008-001 baseline.
3. Verify file count: `ls web-ui/src/components/PropertyPanel/*.tsx | wc -l` — must equal 9.
4. Verify no file exceeds 450 lines: `wc -l web-ui/src/components/PropertyPanel/*.tsx`.
5. Verify `PropertyPanel.tsx` ≤ 130 lines.
6. Commit with message: `refactor(web-ui): decompose PropertyPanel.tsx into 9 focused files`.

**Acceptance Criteria**:
- All SC-008.001 through SC-008.006 pass.
- Git diff shows only additions (new files) and reductions (PropertyPanel.tsx shrinks from 1382 to ≤ 130 lines).
