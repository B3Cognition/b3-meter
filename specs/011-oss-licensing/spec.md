# Spec 011 — OSS Licensing Preparation

**Status**: Draft
**Date**: 2026-03-29
**Author**: CARTOGRAPHER

---

## Background

The project is being prepared for public OSS release. A licensing audit has been completed by INVESTIGATOR. The primary findings are:

1. Apache License 2.0 is already in place and is the correct choice — no license change is needed.
2. The current project name "jMeter Next" / "b3meter" contains a trademark held by the Apache Software Foundation (ASF) and must be replaced before any public launch.
3. A NOTICE file is required by the Apache License 2.0 before any binary distribution.
4. Several housekeeping items (group ID, license reporting, docs language) should be addressed to complete the OSS-ready state.

---

## Functional Requirements

### FR-001 — Apache 2.0 License Retention

**User Story**: As a contributor or enterprise adopter, I want the project to use Apache License 2.0 so that I receive an explicit patent grant and can use the software without fear of downstream IP claims.

**Context**: The LICENSE file already exists and contains a valid Apache 2.0 text. No change to the license file itself is required. This FR documents the intentional retention decision.

**Acceptance Criteria**:
- [ ] `LICENSE` file exists at repository root and contains unmodified Apache 2.0 text.
- [ ] Copyright year range in LICENSE reads `2024-2026` (or current year if later).
- [ ] Copyright holder placeholder is updated to the new project name once FR-002 is resolved.
- [ ] No dependency introduces a license whose outbound terms are incompatible with Apache 2.0 (GPL-2.0-only, SSPL, Commons Clause, etc.).

---

### FR-002 — Project Rename (Trademark Compliance)

**User Story**: As a project maintainer preparing for public OSS release, I want to rename the project so that it does not infringe the Apache Software Foundation's "JMeter" trademark and can be published without legal risk.

**Context**: The name "JMeter" and logo are registered trademarks of the ASF. Use of "jMeter Next" or "JMeter Next" as a product name — even for a clean reimplementation — violates the ASF Trademark Policy. The project must adopt a distinct name before any public repository, artifact, or Docker image is published.

**Acceptance Criteria**:
- [ ] A new project name is selected that does not contain "JMeter" or "jMeter" as a product identifier (FR-002 is the gate for all subsequent rename tasks).
- [ ] The new name has been checked against the [ASF Trademark List](https://www.apache.org/foundation/marks/list/) and found clear.
- [ ] The new name has been checked against common-law trademark databases (USPTO, EUIPO) and no obvious conflicts identified.
- [ ] All occurrences of "jMeter Next", "b3meter", and "JMeter Next" as a product identifier are replaced across the codebase, documentation, and build scripts.
- [ ] The repository is renamed or a canonical new repository URL is established.

---

### FR-003 — NOTICE File Creation

**User Story**: As a redistributor packaging the software as a binary (JAR, Docker image, etc.), I want a NOTICE file at the repository root so that I satisfy the attribution requirements of the Apache License 2.0 and the licenses of bundled third-party components.

**Context**: Apache 2.0 Section 4(d) requires that any NOTICE file included with the work be preserved in redistributions. The project currently has no NOTICE file; one must be created before any binary distribution occurs.

**Acceptance Criteria**:
- [ ] A `NOTICE` file exists at repository root.
- [ ] The file begins with the project name and a copyright line: `Copyright 2024-2026 [New Project Name] Contributors`.
- [ ] The file includes attribution for all Apache 2.0 dependencies that themselves include a NOTICE file or require attribution: Spring Framework, gRPC, Jackson, Kotlin stdlib, jOOQ, Flyway, Apache HttpClient5, Micrometer, jjwt, picocli.
- [ ] The file notes Logback (LGPL 2.1 / EPL 1.0 dual-license, dynamically linked).
- [ ] The file notes H2 Database (MPL-2.0 / EPL-1.0 dual-license, dynamically linked).
- [ ] The file is updated whenever a new third-party dependency is added (see NFR-001).

---

### FR-004 — Group ID / Maven Coordinates Update

**User Story**: As a build system consumer or artifact registry user, I want the published artifacts to use a group ID that the project maintainers actually control so that there is no namespace squatting or confusion with unrelated packages.

**Context**: `build.gradle.kts` currently declares `group = "com.b3meter"`. This domain is unlikely to be controlled by the project, and the name will be invalid after FR-002.

**Acceptance Criteria**:
- [ ] `group` in `build.gradle.kts` (and any sub-module `build.gradle.kts` files) is updated to a domain the project controls or a GitHub-namespaced coordinate (e.g., `io.github.<org>`).
- [ ] The new group ID does not contain "jmeter" or "b3meter".
- [ ] All internal module cross-references (if any use explicit group:artifact notation) are updated.

---

### FR-005 — Gradle License-Report Task for Docker Distributions

**User Story**: As a release engineer building Docker images for distribution, I want an automated task that generates a `THIRD-PARTY-LICENSES.txt` file so that the image includes machine-readable third-party attribution without manual effort.

**Context**: Several bundled dependencies (Logback, H2, javax.annotation-api) use licenses other than Apache 2.0. Their license texts must accompany binary distributions. Manually maintaining this list is error-prone.

**Acceptance Criteria**:
- [ ] A Gradle task (or plugin configuration) exists that produces `THIRD-PARTY-LICENSES.txt` in the build output.
- [ ] The task is wired into the Docker image build pipeline so that the file is included in the image at a documented path (e.g., `/opt/app/THIRD-PARTY-LICENSES.txt`).
- [ ] The output covers all runtime-scoped dependencies and their SPDX license identifiers.
- [ ] The task fails the build if any dependency resolves to a license on the project's block-list (GPL-2.0-only, AGPL-3.0-only, SSPL-1.0, Commons Clause).
- [ ] Running `./gradlew generateLicenseReport` (or equivalent) produces the file without network access beyond initial dependency resolution.

---

### FR-006 — Trademark-Safe Descriptive Language in Documentation

**User Story**: As a user evaluating the tool, I want documentation that accurately describes JMX format compatibility without implying ASF endorsement or confusing the tool with Apache JMeter itself.

**Context**: The project supports loading `.jmx` files produced by Apache JMeter. This compatibility is a selling point but must be described in a way that acknowledges the trademark correctly.

**Acceptance Criteria**:
- [ ] README and any marketing copy use language such as: "supports Apache JMeter JMX test plan format" rather than implying the product is JMeter or a JMeter product.
- [ ] The Apache JMeter trademark attribution line appears where JMX compatibility is described: "Apache JMeter is a trademark of the Apache Software Foundation."
- [ ] No page title, tagline, or H1 heading contains "JMeter" or "jMeter" as part of the new project's name.

---

## Non-Functional Requirements

### NFR-001 — NOTICE File Update Workflow

The NOTICE file must not be a one-time artifact. It must be treated as a living document.

**Acceptance Criteria**:
- [ ] The project CONTRIBUTING.md (or equivalent developer guide) includes a section titled "Adding Dependencies" that instructs contributors to update `NOTICE` when adding a new third-party library.
- [ ] A PR checklist item (GitHub PR template or CONTRIBUTING checklist) includes: "If adding a new dependency, `NOTICE` has been updated."
- [ ] Optionally, the Gradle license-report task (FR-005) is configured to diff against the existing NOTICE file and warn on drift.

---

### NFR-002 — New Name ASF Trademark Policy Check

The new project name must be verified to be clear of ASF trademark conflicts before any public use.

**Acceptance Criteria**:
- [ ] The name check is documented in `specs/011-oss-licensing/` (or ADR equivalent) with a record of the databases searched and the date of the check.
- [ ] The name does not use any ASF project name (Kafka, Spark, Flink, Tomcat, Maven, Gradle-adjacent ASF projects, etc.) as a primary identifier.
- [ ] If the project name is similar to any existing OSS project, a written rationale explains why confusion is unlikely.

---

## Out of Scope

- Choosing a source-available or commercial license (Apache 2.0 retention is decided).
- CLA (Contributor License Agreement) tooling — deferred to a future spec.
- SBOM (Software Bill of Materials / CycloneDX / SPDX) generation — related but separate concern.
- Dual-licensing for commercial add-ons — not planned.
