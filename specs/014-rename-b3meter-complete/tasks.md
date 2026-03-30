# Tasks — 014 Complete b3meter → b3meter Rename

**Spec:** [spec.md](spec.md)
**Total tasks:** 7
**Estimated effort:** ~1 hour

---

## Phase 1 — Operational source/config (FR-001)

### T-001 — Makefile Docker image tags
**Files:** `Makefile`
**Change:** `b3meter-controller` → `b3meter-controller`, `b3meter-worker` → `b3meter-worker`
**Dependencies:** none

### T-002 — FTP mock server greeting
**Files:** `test-servers/ftp-mock/server.js`
**Change:** `b3meter FTP mock server` → `b3meter FTP mock server`
**Dependencies:** none

### T-003 — package-lock.json stale name
**Files:** `web-ui/package-lock.json`
**Change:** `b3meter-ui` → `b3meter-ui` (2 occurrences)
**Dependencies:** none

---

## Phase 2 — Documentation (FR-002)

### T-004 — worker-proto VERSIONING.md
**Files:** `modules/worker-proto/VERSIONING.md`
**Change:** `b3meter.worker` → `b3meter.worker` (proto package refs, already updated in actual .proto file)
**Dependencies:** none

### T-005 — specs/ directory bulk update
**Files:** All `specs/*/` markdown files containing `b3meter` or `b3meter`
**Change:** `b3meter` → `b3meter`, `b3meter` → `b3meter` (where not intentional migration context)
**Preserve:** `UPGRADING.md` — intentional migration guide references
**Dependencies:** none

### T-006 — deploy/ and remaining docs
**Files:** `deploy/helm/b3meter/README.md`, any remaining docs outside specs/
**Change:** bulk sed replace
**Dependencies:** none

---

## Phase 3 — Verification

### T-007 — Verify and build
**Steps:**
1. `grep -rn "b3meter\|b3meter" . --include="*.md" --include="*.java" --include="*.kt" --include="*.kts" --include="*.js" --include="*.ts" --include="*.yaml" --include="*.yml" --include="Makefile" --exclude-dir=".git" --exclude-dir="build" --exclude-dir=".gradle" --exclude-dir="node_modules"` — confirm only UPGRADING.md and ci.yml branch refs remain
2. `./gradlew build` — must pass
3. `./gradlew spotlessCheck` — must pass
**Dependencies:** T-001 through T-006

---

## Phase 4 — Branch rename (FR-003, human decision required)

### T-008 — Branch rename (BLOCKED: awaiting user decision)
**Options:**
- A: `b3meter` → `main`
- B: `b3meter` → `b3meter`
- C: keep as `b3meter`

After user decision, CI workflow branch refs updated accordingly.
