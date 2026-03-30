# Tasks 012 — Rename to b3meter

**Status**: Open
**Date**: 2026-03-29
**Spec**: spec.md

All tasks implement the requirements in `specs/012-rename-b3meter/spec.md`.

Tasks are organised into phases. Complete each phase before starting the next.
Within a phase, tasks with no declared dependency on each other may be done in any order.

---

## Phase 1 — Legal / Compliance Files

### T-012-001 — Create NOTICE file

**Type**: implementation
**Complexity**: XS
**Dependencies**: none
**Spec Ref**: FR-001

**Acceptance Criteria**:
- [ ] File `NOTICE` exists at repository root (plain text, no binary).
- [ ] First line: `b3meter`
- [ ] Second line: `Copyright 2024-2026 b3meter Contributors`
- [ ] Blank line, then attributions for each of the following (with library name, version
      range, license, and URL):
  - Spring Framework (Apache 2.0)
  - gRPC Authors (Apache 2.0)
  - FasterXML / Jackson (Apache 2.0)
  - JetBrains / Kotlin stdlib (Apache 2.0)
  - jOOQ / Data Geekery (Apache 2.0)
  - Redgate Software / Flyway (Apache 2.0)
  - Apache Software Foundation / HttpClient5 (Apache 2.0)
  - Micrometer contributors (Apache 2.0)
  - jsonwebtoken.io / jjwt (Apache 2.0)
  - picocli contributors (Apache 2.0)
- [ ] Logback noted as LGPL-2.1 / EPL-1.0 dual-license, dynamically linked.
- [ ] H2 Database noted as MPL-2.0 / EPL-1.0 dual-license, dynamically linked.

---

### T-012-002 — Update LICENSE + CONTRIBUTING.md

**Type**: implementation
**Complexity**: XS
**Dependencies**: T-012-001 (NOTICE must exist before CONTRIBUTING instructs updating it)
**Spec Ref**: FR-001

**Files to change**:
- `LICENSE`
- `CONTRIBUTING.md`
- `.github/PULL_REQUEST_TEMPLATE.md` (create if absent)

**Acceptance Criteria**:
- [ ] `LICENSE`: copyright holder line reads `Copyright 2024-2026 b3meter Contributors`.
- [ ] `LICENSE`: Apache 2.0 body text otherwise unmodified.
- [ ] `LICENSE`: no occurrence of "jMeter Next", "b3meter", or "JMeter Next".
- [ ] `CONTRIBUTING.md`: product name references updated to b3meter.
- [ ] `CONTRIBUTING.md`: section "Adding Dependencies" instructs contributors to update
      `NOTICE` when adding a new third-party library.
- [ ] `.github/PULL_REQUEST_TEMPLATE.md` (new or existing): checklist item present:
      "If adding a new dependency, `NOTICE` has been updated."
- [ ] No occurrence of "jMeter Next" or "b3meter" as a product identifier in
      `CONTRIBUTING.md`.

---

## Phase 2 — User-Facing Product Name

### T-012-003 — Update documentation files

**Type**: implementation
**Complexity**: S
**Dependencies**: none
**Spec Ref**: FR-002

**Files to change**:
- `README.md`
- `docs/getting-started.md`
- `docs/configuration.md`
- `deploy/helm/b3meter/README.md` (if present)

**Acceptance Criteria**:
- [ ] Product name "jMeter Next", "JMeter Next", "b3meter" replaced with "b3meter"
      throughout all four files.
- [ ] `README.md` includes the trademark-safe JMX compatibility statement near any JMX
      reference: "Supports Apache JMeter JMX test plan format." with attribution line
      "Apache JMeter is a trademark of the Apache Software Foundation."
- [ ] No H1 heading, page title, or marketing tagline in README contains "JMeter" or
      "jMeter" as part of the product name.
- [ ] `git grep -i "jmeter.next" -- README.md docs/` returns zero matches (excluding
      the trademark attribution line which is permitted descriptive use).

---

### T-012-004 — Update web UI product name

**Type**: implementation
**Complexity**: XS
**Dependencies**: none
**Spec Ref**: FR-002

**Files to change**:
- `web-ui/index.html`
- `web-ui/src/App.tsx`
- `web-ui/src/components/MenuBar/MenuBar.tsx`
- `web-ui/package.json`

**Acceptance Criteria**:
- [ ] `web-ui/index.html` `<title>` → `b3meter`.
- [ ] `web-ui/src/App.tsx` toolbar title string and About dialog text → b3meter.
- [ ] `web-ui/src/components/MenuBar/MenuBar.tsx` "About jMeter Next" menu item text →
      "About b3meter".
- [ ] `web-ui/package.json` `"name"` field → `"b3meter-ui"`.
- [ ] No occurrence of "jMeter Next" or "b3meter" remains in any of these four files.

---

### T-012-005 — Update backend user-facing strings

**Type**: implementation
**Complexity**: S
**Dependencies**: none
**Spec Ref**: FR-002

**Files to change**:
- `modules/web-api/src/main/java/com/b3meter/web/api/config/OpenApiConfig.java`
- `modules/engine-adapter/src/main/java/com/b3meter/engine/adapter/cli/B3MeterCli.java`
- `modules/engine-adapter/src/main/java/com/b3meter/engine/adapter/report/HtmlReportGenerator.java`
- `modules/web-api/src/main/resources/application.yml`

**Note**: File paths above use the current `com.b3meter` path. If T-012-008 (package
rename) is done first, paths change to `com/b3meter/...` — update accordingly.

**Acceptance Criteria**:
- [ ] `OpenApiConfig.java` `.title(...)` call → `"b3meter API"`.
- [ ] `B3MeterCli.java` `@Command(name=...)` → `"b3meter"`;
      `description` string updated to reference b3meter.
- [ ] `HtmlReportGenerator.java` `<title>` tag in generated HTML → `"b3meter Test Report"`.
- [ ] `application.yml` `spring.application.name` → `b3meter`.
- [ ] `application.yml` `management.metrics.tags.application` (if present) → `b3meter`.
- [ ] `./gradlew :modules:web-api:build :modules:engine-adapter:build` passes after
      changes.

---

### T-012-006 — Rename Helm chart

**Type**: implementation
**Complexity**: S
**Dependencies**: none
**Spec Ref**: FR-002

**Files / directories to change**:
- `deploy/helm/b3meter/Chart.yaml`
- `deploy/helm/b3meter/values.yaml`
- `deploy/helm/b3meter/` directory → rename to `deploy/helm/b3meter/` via `git mv`
- Any templates under `deploy/helm/b3meter/templates/` referencing "b3meter"

**Acceptance Criteria**:
- [ ] `Chart.yaml` `name` field → `b3meter`.
- [ ] `Chart.yaml` `description` updated (remove "jMeter Next" as product name).
- [ ] `values.yaml` `image.repository` values → `b3meter/controller`, `b3meter/worker`.
- [ ] Directory renamed: `deploy/helm/b3meter/` → `deploy/helm/b3meter/` (`git mv`).
- [ ] No `b3meter` or `b3meter` as a product identifier in any Helm file.
- [ ] Helm lint passes (if `helm` CLI is available): `helm lint deploy/helm/b3meter/`.

---

## Phase 3 — Build System Coordinates

### T-012-007 — Update Gradle group ID and mainClass references

**Type**: implementation
**Complexity**: XS
**Dependencies**: none (can be done independently; must be done before Phase 4 or at
  same time — package paths in mainClass refs must match whichever package name is current)
**Spec Ref**: FR-003

**Files to change**:
- `build.gradle.kts` (root)
- `perf/build.gradle.kts`
- `modules/engine-adapter/build.gradle.kts`
- `gradle.properties` (comment only if it references b3meter)

**Acceptance Criteria**:
- [ ] Root `build.gradle.kts` `group = "com.b3meter"` → `group = "com.b3meter"`.
- [ ] `perf/build.gradle.kts` mainClass fully-qualified name updated from
      `com.b3meter.*` to `com.b3meter.*` (coordinate with T-012-008 package rename).
- [ ] `modules/engine-adapter/build.gradle.kts` mainClass updated similarly.
- [ ] No `com.b3meter` string remains in any `build.gradle.kts` file.
- [ ] `./gradlew :modules:engine-adapter:build :perf:build` passes.

---

## Phase 4 — Source Package Namespace

### T-012-008 — Rename Java/Kotlin packages com.b3meter → com.b3meter

**Type**: implementation
**Complexity**: M
**Dependencies**: T-012-007 (group ID update should match package rename to avoid
  mixed-coordinate state)
**Spec Ref**: FR-004

**Approach**: Single IDE refactor operation.
- IntelliJ IDEA: right-click package `com.b3meter` in Project view →
  Refactor → Rename Package → `com.b3meter`. Enable "Search in comments and strings":
  **no** (handles only declarations and imports). After rename, manually verify the
  string constants in T-012-005, T-012-009, T-012-010 (they are separate tasks).
- After IDE rename: verify with `git grep "com\.b3meter" -- "*.kt" "*.java" "*.proto"`.
- `.proto` files: check `option java_package` and `option java_outer_classname` — update
  manually if the IDE rename does not cover them.

**Files affected** (approximated counts from namespace-scope.md):
- 409 files with `package com.b3meter.*` declarations
- 541 files with `import com.b3meter.*` statements
- Proto files with `option java_package = "com.b3meter.*"` (count TBD — check
  `git grep "java_package" -- "*.proto"`)

**Acceptance Criteria**:
- [ ] `git grep "com\.b3meter" -- "*.kt" "*.java" "*.proto"` returns zero matches.
- [ ] `git grep "com\.b3meter" -- "*.xml" "*.yml" "*.yaml" "*.properties"` returns
      zero matches (resource files and config files updated if applicable).
- [ ] `./gradlew build` passes — all modules compile, all tests pass.

---

### T-012-009 — Rename B3MeterCli source files

**Type**: implementation
**Complexity**: XS
**Dependencies**: T-012-008
**Spec Ref**: FR-004

**Files to rename** (paths below are post-T-012-008 paths):
- `modules/engine-adapter/src/main/java/com/b3meter/engine/adapter/cli/B3MeterCli.java`
  → `B3MeterCli.java`
- `modules/engine-adapter/src/test/java/com/b3meter/engine/adapter/cli/B3MeterCliTest.java`
  → `B3MeterCliTest.java`

**Acceptance Criteria**:
- [ ] Source files renamed with `git mv`.
- [ ] Class declaration inside each file updated: `class B3MeterCli` → `class B3MeterCli`,
      `class B3MeterCliTest` → `class B3MeterCliTest`.
- [ ] All references to `B3MeterCli` and `B3MeterCliTest` in other source files
      updated (there should be very few — check with `git grep "B3MeterCli"`).
- [ ] `./gradlew :modules:engine-adapter:build` passes.

---

## Phase 5 — Infrastructure

### T-012-010 — Update Docker files

**Type**: implementation
**Complexity**: S
**Dependencies**: none (can be parallelised with phases 3 and 4)
**Spec Ref**: FR-005

**Files to change**:
- `Dockerfile.controller`
- `Dockerfile.worker`
- `docker-compose.distributed.yml`
- `docker-compose.test.yml`

**Acceptance Criteria**:
- [ ] `Dockerfile.controller`: OCI label `org.opencontainers.image.title` → `b3meter-controller`.
- [ ] `Dockerfile.controller`: OCI label `org.opencontainers.image.source` updated
      (placeholder `https://github.com/your-org/b3meter` until repo slug is renamed).
- [ ] `Dockerfile.worker`: same label updates for worker.
- [ ] `docker-compose.distributed.yml`: `image:` values → `b3meter/controller:latest`,
      `b3meter/worker:latest`.
- [ ] `docker-compose.test.yml`: service names and/or network name updated — no
      `b3meter` as a product identifier.
- [ ] No `b3meter` or `b3meter` as a product identifier in any Docker-related file.
- [ ] `docker build -f Dockerfile.controller .` succeeds (images still buildable).

---

### T-012-011 — Update CI/CD workflow

**Type**: implementation
**Complexity**: XS
**Dependencies**: T-012-010 (Docker image names must be consistent)
**Spec Ref**: FR-005

**Files to change**:
- `.github/workflows/ci.yml`
- Any other files in `.github/workflows/` that reference `b3meter` Docker tags

**Acceptance Criteria**:
- [ ] Docker image push steps updated to `b3meter/controller`, `b3meter/worker`.
- [ ] Any workflow job or step names containing "jMeter Next" updated.
- [ ] No `b3meter` or `b3meter` as a product identifier in any workflow file.

---

## Phase 6 — Runtime Constants

### T-012-012 — Update runtime constants with migration documentation

**Type**: implementation
**Complexity**: S
**Dependencies**: T-012-008 (package rename; file paths change)
**Spec Ref**: FR-006, NFR-002

**Files to change** (paths are post-T-012-008):
- `modules/web-api/src/main/java/com/b3meter/web/api/security/JwtTokenService.java`
- `web-ui/src/themes/themes.ts`
- `web-ui/src/components/chaos/ChaosLoad.tsx` (or wherever `'b3meter-chaos-config'`
  localStorage key is defined — verify with `git grep "b3meter-chaos"`)
- `UPGRADING.md` (create at repo root) or `docs/upgrading.md`

**Acceptance Criteria**:
- [ ] `JwtTokenService.java` `ISSUER` constant → `"b3meter"`.
- [ ] `themes.ts` localStorage key `'b3meter-theme'` → `'b3meter-theme'`.
- [ ] All other `localStorage` keys containing `b3meter` → `b3meter-*` equivalent.
- [ ] `UPGRADING.md` (or `docs/upgrading.md`) created with a section covering:
  - JWT token invalidation: existing tokens become invalid after first deployment;
    users must re-authenticate.
  - Theme preference loss: stored `b3meter-theme` key is abandoned; users revert
    to default theme.
  - Chaos config loss: stored `b3meter-chaos-config` key is abandoned.
- [ ] `./gradlew :modules:web-api:build` passes.

---

## Phase 7 — Tooling

### T-012-013 — Add Gradle license-report task

**Type**: implementation
**Complexity**: M
**Dependencies**: T-012-007 (group ID change; task references updated coordinates)
**Spec Ref**: FR-007

**Acceptance Criteria**:
- [ ] `com.github.jk1.dependency-license-report` plugin (version ≥ 2.x) applied in root
      `build.gradle.kts` (or the relevant submodule).
- [ ] Task `generateLicenseReport` produces `build/reports/licenses/THIRD-PARTY-LICENSES.txt`
      covering `runtimeClasspath`.
- [ ] Output includes SPDX license identifiers for each dependency.
- [ ] `Dockerfile.controller` and `Dockerfile.worker` updated: `COPY` step includes
      `THIRD-PARTY-LICENSES.txt` at `/opt/app/THIRD-PARTY-LICENSES.txt`.
- [ ] Task documented in `CONTRIBUTING.md` under a "Generating License Report" heading.
- [ ] `./gradlew generateLicenseReport` runs successfully on a clean checkout.
- [ ] (Optional) Task configured to fail if any dependency resolves to a blocked license
      (GPL-2.0-only, AGPL-3.0-only, SSPL-1.0).

---

## Phase 8 — Verification

### T-012-014 — Final rename audit and build verification

**Type**: verification
**Complexity**: S
**Dependencies**: T-012-001 through T-012-013
**Spec Ref**: All

**Acceptance Criteria**:
- [ ] `git grep -i "jmeter.next" -- ':!specs/' ':!*.md'` returns zero matches in source
      and config files. (The trademark attribution line in README is permitted — verify it
      reads "Apache JMeter is a trademark…" and is not a product name use.)
- [ ] `git grep "com\.b3meter" -- "*.kt" "*.java" "*.proto" "*.xml" "*.yml" "*.yaml"`
      returns zero matches.
- [ ] `git grep "com\.b3meter" -- "*.gradle.kts"` returns zero matches.
- [ ] `./gradlew build` — all modules compile, all tests pass.
- [ ] `NOTICE` exists at repo root.
- [ ] `LICENSE` copyright line contains "b3meter Contributors".
- [ ] `UPGRADING.md` exists (JWT + localStorage migration notes).
- [ ] **Manual post-merge step** (not automatable): rename GitHub repository slug from
      `b3meter` to `b3meter`. After rename, update `org.opencontainers.image.source`
      OCI label in Dockerfiles with the real URL.
