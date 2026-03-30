# Spec 013 — OSS Publication Readiness

**Feature:** Open-source publication verification and hardening
**Status:** Planning
**Created:** 2026-03-29
**Codebase:** b3meter (formerly b3meter), `git@github.com:b3-cognition/b3-meter.git`

---

## Background

b3meter is ready to be published as an open-source project. The rename from b3meter to b3meter is complete (spec 012), Apache 2.0 licensing was selected (spec 011). Before making the repository public, a final verification pass is required to ensure the project meets open-source community standards.

Gap analysis (2026-03-29) identified one **BLOCKER**, several **HIGH/MEDIUM** items, and a set of recommended improvements.

---

## Requirements

### FR-001 — Apache 2.0 License Headers (BLOCKER)

All source files must carry an Apache 2.0 copyright header.

**Scope:** 388 Java/Kotlin files in `modules/`, 102 TypeScript/TSX files in `web-ui/src/`, and proto files in `modules/worker-proto/src/main/proto/`.

**Header format (Java/Kotlin/Proto):**
```
/*
 * Copyright 2024-2026 b3meter Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
```

**Header format (TypeScript/TSX):**
```
// Copyright 2024-2026 b3meter Contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
```

**Delivery:** Via Gradle Spotless plugin (`com.diffplug.spotless`) configured with `licenseHeader` for Java/Kotlin/Kotlin-script, and a custom header for TypeScript. A CI check (`./gradlew spotlessCheck`) must pass on every PR.

**Acceptance criteria:**
- `./gradlew spotlessCheck` exits 0 on the full codebase
- A new CI step "License Headers" runs `spotlessCheck` and blocks merge if it fails
- All `.java`, `.kt`, `.kts` files in `modules/` and `perf/` carry the header
- All `.ts`, `.tsx` files in `web-ui/src/` carry the header
- All `.proto` files carry the header

---

### FR-002 — Git History Secret Scan

The git history must be scanned for accidentally committed secrets before the repo is made public.

**Tool:** `gitleaks` (open-source, SAST-oriented, well-maintained)

**Scan scope:** Full git history from the initial commit.

**Expected result:** Zero findings at HIGH or CRITICAL severity. Any findings must be investigated; if real secrets are found, the history must be rewritten with `git-filter-repo` before publishing.

**Acceptance criteria:**
- `gitleaks detect --source . --log-opts="--all"` exits 0 (no leaks found)
- Result documented in `specs/013-oss-readiness/gitleaks-report.txt`
- If any findings: remediation plan documented and executed before publishing

---

### FR-003 — Dependency Vulnerability Scan

Runtime dependencies must be checked for known CVEs.

**Tool:** OWASP Dependency-Check Gradle plugin (`org.owasp.dependencycheck`)

**Scan scope:** `runtimeClasspath` configuration of all modules.

**Threshold:** No CVSS >= 7.0 (HIGH) unfixed vulnerabilities in the final published version. Findings must either be resolved (version upgrade) or formally accepted with documented rationale.

**Acceptance criteria:**
- `./gradlew dependencyCheckAggregate` completes and generates `build/reports/dependency-check-report.html`
- Zero unaccepted HIGH/CRITICAL CVEs
- Report committed to `specs/013-oss-readiness/dependency-check-report.md` (summary)

---

### FR-004 — Version Stabilization

The project version must not be `SNAPSHOT` at the time of first public release.

**Current state:** `build.gradle.kts` line 11: `version = "0.1.0-SNAPSHOT"`

**Target:** `version = "0.1.0"` for the initial public release tag `v0.1.0`.

**Acceptance criteria:**
- `build.gradle.kts` version is `"0.1.0"` (no `-SNAPSHOT` suffix)
- A git tag `v0.1.0` is created at the publish commit
- Release workflow (`release.yml`) successfully builds and pushes `v0.1.0` Docker images to GHCR

---

### FR-005 — Community Health Files

Standard GitHub community health files must be present and complete.

**Required files:**

| File | Status | Notes |
|------|--------|-------|
| `LICENSE` | ✓ Present | Apache 2.0 |
| `README.md` | ✓ Present | Comprehensive |
| `CONTRIBUTING.md` | ✓ Present | Complete |
| `NOTICE` | ✓ Present | Third-party attributions |
| `UPGRADING.md` | ✓ Present | JWT / localStorage migration notes |
| `SECURITY.md` | ✗ Missing | **Required** |
| `CODE_OF_CONDUCT.md` | ✗ Missing | Recommended (Contributor Covenant) |
| `.github/ISSUE_TEMPLATE/bug_report.md` | ✗ Missing | Recommended |
| `.github/ISSUE_TEMPLATE/feature_request.md` | ✗ Missing | Recommended |
| `.github/PULL_REQUEST_TEMPLATE.md` | ✓ Present | — |

**SECURITY.md must contain:**
- Supported versions table
- Vulnerability reporting process (private email or GitHub private advisory)
- Response timeline commitment (e.g., acknowledge within 72 hours)
- Public disclosure policy

**Acceptance criteria:**
- `SECURITY.md` exists at repo root with the above sections
- `CODE_OF_CONDUCT.md` exists (Contributor Covenant 2.1 is acceptable)
- Both issue templates exist under `.github/ISSUE_TEMPLATE/`

---

### FR-006 — .gitignore Hardening

The `.gitignore` must cover common secret file patterns.

**Currently missing patterns:**
```
# Environment / secrets
.env
.env.local
.env.*.local
*.key
*.pem
*.p12
*.jks
secrets/
```

**Acceptance criteria:**
- The above patterns are added to `.gitignore`
- `git status` on a fresh clone with no staged files is clean

---

### FR-007 — web-ui TODO Resolution

A TODO comment references an unimplemented Gradle plugin task:

**File:** `modules/web-ui/build.gradle.kts:14`
```kotlin
// TODO(T-web-ui-scaffold): apply node-gradle plugin and configure Vite build.
```

The actual React UI lives in `/web-ui/` (root), not `modules/web-ui/`. The `modules/web-ui` placeholder module serves as a dependency placeholder in the multi-module build. This TODO creates confusion for new contributors.

**Acceptance criteria:**
- The TODO is removed and replaced with a clear comment explaining the module's role:
  ```kotlin
  // This module is a Gradle placeholder that declares the web-ui build artifact
  // dependency. The actual React/TypeScript source lives in /web-ui/ at the
  // repository root and is built separately with `npm run build`.
  ```
- OR: The module is removed from settings.gradle.kts if it serves no dependency role

---

### FR-008 — Final Clean Build Verification

A clean build from a fresh clone must succeed with no external access to internal artifact repositories.

**Test procedure:**
1. Clone into a new directory
2. Run `./gradlew clean build --no-daemon` with only the Gradle plugin portal and Maven Central/Google as repositories
3. Verify `BUILD SUCCESSFUL`
4. Run `./gradlew test` — all tests must pass

**Acceptance criteria:**
- `BUILD SUCCESSFUL` from a directory with no prior Gradle caches
- Zero test failures
- No reference to internal Maven repositories in any `build.gradle.kts` or `settings.gradle.kts`

---

### FR-009 — Spotless CI Gate

A dedicated CI step must enforce license headers and code formatting on every PR.

**Acceptance criteria:**
- `.github/workflows/ci.yml` includes a `spotless-check` job that runs `./gradlew spotlessCheck`
- The job runs on all pull requests targeting `b3meter` branch
- Failure of `spotlessCheck` blocks merge

---

### FR-010 — GitHub Repository Configuration Checklist

Before making the repository public, the following GitHub settings must be configured.

**Acceptance criteria (manual verification):**
- [ ] Repository description: "Modern load testing platform — Apache JMeter JMX compatible"
- [ ] Topics: `load-testing`, `jmeter`, `performance-testing`, `java`, `kotlin`, `spring-boot`, `grpc`
- [ ] Default branch: `b3meter` (main development branch)
- [ ] Branch protection on `b3meter`: require PR, require CI to pass, no force push
- [ ] Discussions enabled (for community Q&A)
- [ ] Issues enabled
- [ ] Wiki disabled (documentation is in `/docs/`)
- [ ] `GHCR` packages visibility set to public after first release push

---

## Non-Functional Requirements

### NFR-001 — Automation Over Manual

License header addition (FR-001) must be automated via Spotless, not a manual one-time script. Future files must be covered automatically.

### NFR-002 — No History Rewrite Unless Required

FR-002 (git history scan) should be a clean pass. History rewrite is a last resort (disruptive, requires force-push, invalidates any existing forks/PRs).

### NFR-003 — Speed

The Spotless check CI job must complete in under 60 seconds. The OWASP dependency check may cache the NVD database; initial run may take longer but should be < 10 minutes.

---

## Out of Scope

- Adding new features
- Expanding test coverage (acceptable as-is for OSS launch)
- Creating architecture.md (LOW priority, post-launch)
- Javadoc generation / publishing
- Maven Central / Gradle Plugin Portal publication (future spec)
