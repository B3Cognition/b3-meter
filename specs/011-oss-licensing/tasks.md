# Tasks 011 — OSS Licensing Preparation

**Status**: Open
**Date**: 2026-03-29
**Spec**: spec.md

All tasks in this file implement the requirements defined in `specs/011-oss-licensing/spec.md`.

---

### T-011-001 — Select new project name

**Type**: decision
**Complexity**: S
**Status**: DONE — 2026-03-29
**Dependencies**: none
**Spec Ref**: FR-002, NFR-002

**Decision**: **b3meter** (stylised "be free meter"). Package root: `com.b3meter`.
- Does not contain "JMeter" or "jMeter".
- Not on ASF Trademark List.
- Implementation plan: `specs/012-rename-b3meter/` (tasks T-012-001 through T-012-014).

**Acceptance Criteria**:
- [x] A final name is selected and recorded with the rationale for selection.
- [x] The selected name does not contain "JMeter" or "jMeter" as a product identifier in any casing.
- [ ] Formal trademark check against USPTO and EUIPO databases — **pending** (user to verify before public launch; date to be recorded).
- [ ] ASF Trademark List check documented with date.

---

### T-011-002 — Create NOTICE file

**Type**: implementation
**Complexity**: XS
**Dependencies**: T-011-001 (for new project name in copyright line)
**Spec Ref**: FR-003

**Acceptance Criteria**:
- [ ] `NOTICE` file exists at repository root.
- [ ] First line is the new project name; second line is the copyright statement: `Copyright 2024-2026 [New Project Name] Contributors`.
- [ ] File includes attribution for: Spring Framework, gRPC Authors, FasterXML (Jackson), JetBrains (Kotlin), jOOQ / Data Geekery, Redgate Software (Flyway), Apache Software Foundation (HttpClient5), Micrometer contributors, jsonwebtoken.io (jjwt), picocli contributors.
- [ ] File notes Logback as LGPL 2.1 / EPL 1.0 dual-license, dynamically linked.
- [ ] File notes H2 Database as MPL-2.0 / EPL-1.0 dual-license, dynamically linked.
- [ ] File is plain text, no binary content.

---

### T-011-003 — Update LICENSE copyright holder name

**Type**: implementation
**Complexity**: XS
**Dependencies**: T-011-001
**Spec Ref**: FR-001

**Acceptance Criteria**:
- [ ] The copyright holder line in `LICENSE` reads: `Copyright 2024-2026 [New Project Name] Contributors` (or equivalent).
- [ ] The Apache 2.0 license body text is otherwise unmodified.
- [ ] No occurrence of "jMeter Next", "b3meter", or "JMeter Next" remains in the LICENSE file.

---

### T-011-004 — Update CONTRIBUTING.md copyright reference and dependency workflow

**Type**: implementation
**Complexity**: XS
**Dependencies**: T-011-001, T-011-002
**Spec Ref**: NFR-001

**Acceptance Criteria**:
- [ ] CONTRIBUTING.md copyright line references the new project name.
- [ ] CONTRIBUTING.md contains a section titled "Adding Dependencies" that instructs contributors to update `NOTICE` when adding a new third-party library.
- [ ] If a GitHub PR template (`.github/PULL_REQUEST_TEMPLATE.md`) exists or is created, it includes a checklist item: "If adding a new dependency, `NOTICE` has been updated."
- [ ] No occurrence of "jMeter Next" or "b3meter" as a product identifier remains in CONTRIBUTING.md.

---

### T-011-005 — Update all Java/Kotlin package names

**Type**: implementation
**Complexity**: M *(was L — IDE Refactor → Rename Package does 409 package declarations + 541 imports in one operation; 1–2 hours not 1–2 days. Low risk: compile will catch any miss.)*
**Scope note**: Not legally required by trademark law (internal packages are not consumer-facing at runtime). Strongly recommended — permanent contributor confusion if skipped. One IDE operation: right-click `com.b3meter` → Refactor → Rename.
**Dependencies**: T-011-001
**Spec Ref**: FR-002

**Acceptance Criteria**:
- [ ] All source files under `com.b3meter.*` are moved to `com.{newname}.*` (or the agreed-upon package root).
- [ ] All `import` statements updated accordingly.
- [ ] All `package` declarations updated accordingly.
- [ ] gRPC proto files and generated stubs reference the new package if applicable.
- [ ] Application compiles and all tests pass after the rename.
- [ ] No `com.b3meter` string remains in any `.kt`, `.java`, or `.proto` source file.
- [ ] No `com.b3meter` string remains in any resource or configuration file (application.yml, logback.xml, etc.).

---

### T-011-006 — Update build.gradle.kts group ID

**Type**: implementation
**Complexity**: XS
**Dependencies**: T-011-001
**Spec Ref**: FR-004

**Acceptance Criteria**:
- [ ] `group` property in root `build.gradle.kts` is updated to the new value (e.g., `io.github.<org>` or the project's controlled domain).
- [ ] All sub-module `build.gradle.kts` files (if any declare a `group` override) are updated.
- [ ] The new group ID does not contain "jmeter" or "b3meter".
- [ ] `./gradlew build` completes without errors after the change.
- [ ] Published artifact coordinates in any README or documentation examples are updated.

---

### T-011-007 — Update Docker image labels and registry path

**Type**: implementation
**Complexity**: S
**Dependencies**: T-011-001, T-011-006
**Spec Ref**: FR-002

**Acceptance Criteria**:
- [ ] `Dockerfile` (and any Docker Compose files) updated to use the new image name / tag.
- [ ] `org.opencontainers.image.title` and `org.opencontainers.image.source` labels reference the new project name and repository URL.
- [ ] CI/CD pipeline scripts (GitHub Actions workflows, Makefile targets, etc.) updated to push to the new registry path.
- [ ] No `b3meter` or `b3meter` string remains in any Docker-related file as a product identifier.
- [ ] Image builds successfully and the new labels are verifiable with `docker inspect`.

---

### T-011-008 — Add Gradle license-report task

**Type**: implementation
**Complexity**: M
**Dependencies**: T-011-006
**Spec Ref**: FR-005

**Acceptance Criteria**:
- [ ] A Gradle task (using a license plugin such as `com.github.jk1.dependency-license-report` or equivalent) is configured and produces `build/reports/licenses/THIRD-PARTY-LICENSES.txt` (or equivalent path).
- [ ] The task covers all `runtimeClasspath` dependencies and outputs SPDX license identifiers.
- [ ] The Dockerfile `COPY` step (or equivalent) includes the generated file at `/opt/app/THIRD-PARTY-LICENSES.txt` inside the image.
- [ ] The task is documented in CONTRIBUTING.md or a developer guide.
- [ ] Optionally: the task fails (with a clear error message) if any dependency resolves to a blocked license (GPL-2.0-only, AGPL-3.0-only, SSPL-1.0, Commons Clause).
- [ ] `./gradlew generateLicenseReport` (or the chosen task name) runs successfully in a clean checkout after `./gradlew dependencies` has populated the cache.

---

### T-011-009 — Update README with trademark-safe JMX compatibility statement

**Type**: implementation
**Complexity**: XS
**Dependencies**: T-011-001
**Spec Ref**: FR-006

**Acceptance Criteria**:
- [ ] README describes JMX format compatibility using approved phrasing, e.g.: "Supports Apache JMeter JMX test plan format."
- [ ] The trademark attribution line appears near any JMX compatibility mention: "Apache JMeter is a trademark of the Apache Software Foundation."
- [ ] No H1 heading, page title, or marketing tagline in the README contains "JMeter" or "jMeter" as part of the new project's name.
- [ ] No other documentation file (docs/, wiki pages) uses "JMeter" as a primary product name identifier.

---

### T-011-010 — Final OSS launch checklist

**Type**: implementation
**Complexity**: S
**Dependencies**: T-011-001 through T-011-009
**Spec Ref**: All

**Acceptance Criteria**:
- [ ] T-011-001 through T-011-009 are all marked complete.
- [ ] `LICENSE` file exists at repository root with correct copyright holder and Apache 2.0 text.
- [ ] `NOTICE` file exists at repository root.
- [ ] No occurrence of "jMeter Next", "JMeter Next", "b3meter" as a product identifier exists in any tracked file (`git grep -i "jmeter.next"` returns no source files, only this spec and research directory).
- [ ] `build.gradle.kts` group ID does not contain "jmeter" or "b3meter".
- [ ] Docker image builds and contains `THIRD-PARTY-LICENSES.txt`.
- [ ] `./gradlew build` passes all tests on a clean checkout.
- [ ] README trademark-safe language is in place.
- [ ] Repository is set to public (or a date is agreed for the flip).
- [ ] A git tag `v0.1.0` (or equivalent first public release tag) is created after all above criteria are met.
