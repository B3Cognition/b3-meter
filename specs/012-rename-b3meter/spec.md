# Spec 012 — Rename to b3meter

**Status**: Open
**Date**: 2026-03-29
**Research base**: `specs/011-oss-licensing/` (licensing spec + namespace-scope.md)

## Overview

Rename all product identifiers from "jMeter Next" / "b3meter" / `com.b3meter` to
**b3meter** (stylised "be free meter") / `com.b3meter`. This resolves the Apache Software
Foundation trademark violation (ASF marks include "JMeter"; using it as a product name
requires ASF permission we do not have) and prepares the repository for public open-source
launch under Apache License 2.0.

The change is in two layers:

1. **User-facing surface** (~15 files, 2–4 hours): product name in docs, UI, Helm chart,
   Docker image labels, OpenAPI title, CLI binary name, HTML report title. Legally required.
2. **Source namespace** (~950 file-lines, 1–2 hours via IDE refactor): Java/Kotlin package
   declarations and imports `com.b3meter.*` → `com.b3meter.*`. Not legally required
   now (no Maven Central publishing) but strongly recommended for contributor clarity.

Both layers are included here so the rename is done once, cleanly.

---

## Target State

After all tasks complete:

- `git grep -i "jmeter.next" -- ':!specs/'` returns **zero matches** in source files.
- Product name "b3meter" (or "B3Meter") appears in all user-facing locations.
- Java/Kotlin package root is `com.b3meter.*`.
- Build artifact group is `com.b3meter`.
- Docker images are tagged `b3meter/controller` and `b3meter/worker`.
- Helm chart name is `b3meter`.
- `./gradlew build` passes (all tests green).
- `NOTICE` file exists at repo root with correct copyright and upstream attributions.
- `LICENSE` copyright line reads `Copyright 2024-2026 b3meter Contributors`.

---

## Functional Requirements

### FR-001 — Legal / compliance files

- `NOTICE` file exists at repository root listing the new project name, copyright, and all
  required upstream attributions (Spring Framework, gRPC, FasterXML/Jackson, Kotlin stdlib,
  jOOQ, Flyway, Apache HttpComponents 5, Micrometer, jjwt, picocli; Logback noted as
  LGPL-2.1/EPL-1.0 dynamically linked; H2 noted as MPL-2.0/EPL-1.0 dynamically linked).
- `LICENSE` copyright holder line updated to `Copyright 2024-2026 b3meter Contributors`.
- `CONTRIBUTING.md` references b3meter and includes an "Adding Dependencies" section
  instructing contributors to update `NOTICE` when adding a third-party library.

### FR-002 — User-facing product name

- **Docs**: `README.md`, `docs/getting-started.md`, `docs/configuration.md` — no occurrence
  of "jMeter Next", "JMeter Next", or "b3meter" as a product name identifier.
- **Web UI**: `web-ui/index.html` `<title>`, `web-ui/src/App.tsx` toolbar + About dialog,
  `web-ui/src/components/MenuBar/MenuBar.tsx` About menu item, `web-ui/package.json`
  `"name"` field — all reference b3meter.
- **Backend user-facing strings**:
  - `OpenApiConfig.java` `.title(...)` → `"b3meter API"` (Swagger UI is consumer-visible).
  - `B3MeterCli.java` `@Command(name=..., description=...)` → references b3meter.
  - `HtmlReportGenerator.java` `<title>` tag in generated HTML report → `"b3meter Test Report"`.
  - `modules/web-api/src/main/resources/application.yml` `spring.application.name` →
    `b3meter`; `management.metrics.tags.application` → `b3meter`.
- **Helm chart**: `deploy/helm/b3meter/Chart.yaml` `name` → `b3meter`;
  `deploy/helm/b3meter/values.yaml` image repositories → `b3meter/controller`,
  `b3meter/worker`; chart directory renamed `deploy/helm/b3meter/`.
- **README trademark-safe JMX statement**: per FR-006 of spec 011 — JMX compatibility
  described as "Supports Apache JMeter JMX test plan format" with attribution line
  "Apache JMeter is a trademark of the Apache Software Foundation."

### FR-003 — Build artifact coordinates

- Root `build.gradle.kts` `group` property → `"com.b3meter"`.
- `perf/build.gradle.kts` mainClass fully-qualified reference → updated to `com.b3meter.*`.
- `modules/engine-adapter/build.gradle.kts` mainClass reference → updated to `com.b3meter.*`.

### FR-004 — Source package namespace

- All `package com.b3meter.*` declarations → `com.b3meter.*` (409 files).
- All `import com.b3meter.*` statements → `com.b3meter.*` (541 files).
- All `.proto` option `java_package` values (if any) → `com.b3meter.*`.
- Source files `B3MeterCli.java` → `B3MeterCli.java`;
  `B3MeterCliTest.java` → `B3MeterCliTest.java`.
- All internal class name references (`B3MeterCli`, `B3MeterCliTest`) updated to
  `B3MeterCli`, `B3MeterCliTest`.

### FR-005 — Docker / container artifacts

- `Dockerfile.controller` and `Dockerfile.worker`: OCI label
  `org.opencontainers.image.title` → `b3meter-controller` / `b3meter-worker`; label
  `org.opencontainers.image.source` → new repo URL (placeholder until repo slug is renamed).
- `docker-compose.distributed.yml`: image names → `b3meter/controller`, `b3meter/worker`.
- `docker-compose.test.yml`: service names and network name updated; no `b3meter`
  string as a product identifier.
- `.github/workflows/ci.yml`: Docker image push targets → `b3meter/controller`,
  `b3meter/worker`.

### FR-006 — Runtime constants (with migration notes)

- `JwtTokenService.java` `ISSUER = "b3meter"` → `"b3meter"`.
  **Migration note**: existing issued JWT tokens become invalid on first deployment after
  this change; document in release notes / upgrade guide.
- `web-ui/src/themes/themes.ts` localStorage key `'b3meter-theme'` → `'b3meter-theme'`.
- Any other `localStorage` key containing `b3meter` (e.g., `ChaosLoad.tsx`
  `'b3meter-chaos-config'`) → `'b3meter-*'`.
  **Migration note**: existing browser-stored user preferences (theme, chaos config) are
  lost after this change for existing users; document in release notes.

### FR-007 — Gradle license-report task

- `com.github.jk1.dependency-license-report` plugin (or equivalent) configured; task
  `generateLicenseReport` produces `build/reports/licenses/THIRD-PARTY-LICENSES.txt`
  covering all `runtimeClasspath` dependencies with SPDX identifiers.
- `Dockerfile.controller` / `Dockerfile.worker` `COPY` step includes the generated file
  at `/opt/app/THIRD-PARTY-LICENSES.txt`.
- Task documented in `CONTRIBUTING.md`.

---

## Non-Functional Requirements

### NFR-001 — Zero regressions

- `./gradlew build` (all modules) passes with all existing tests green after every task.
- The package rename (FR-004) must not be merged until `./gradlew build` is confirmed green.

### NFR-002 — Migration notes documented

- JWT issuer change and localStorage key change are documented in a `UPGRADING.md` or
  `docs/upgrading.md` file or as a section in the release notes before the first public tag.

### NFR-003 — Spec docs excluded from audit

- The `git grep` audit in T-012-013 explicitly excludes `specs/` directory — those files
  are historical records and may legitimately contain "b3meter".

---

## Out of Scope

- GitHub repository slug rename (`b3meter` → `b3meter`) — manual operation on GitHub,
  cannot be scripted; noted in T-012-013 as a post-merge manual step.
- `specs/` markdown content — excluded from rename (historical record).
- Java/Kotlin Javadoc comments mentioning "b3meter" — excluded (developer docs only,
  not consumer-facing).
- Maven Central publishing setup — separate concern; not blocking OSS launch.
