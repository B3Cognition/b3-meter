# Tasks — 013 OSS Publication Readiness

**Spec:** [spec.md](spec.md)
**Total tasks:** 10
**Estimated effort:** ~1.5 days

---

## Phase 1 — Blockers (must complete before publishing)

### T-001 — Add Spotless Gradle plugin and configure license headers
**FR:** FR-001, FR-009
**Effort:** M (2–3 hours)
**Files:**
- `build.gradle.kts` (root) — add `com.diffplug.spotless` plugin + `licenseHeader` config for Java/Kotlin/KotlinScript
- `gradle/libs.versions.toml` — add spotless version
- `web-ui/package.json` or `web-ui/.eslintrc` — add license header lint rule (optional; can use a simple Node script)

**Acceptance criteria:**
- [ ] `com.diffplug.spotless` plugin applied in root `build.gradle.kts` with `licenseHeader` configured for `.java`, `.kt`, `.kts`, `.proto` extensions using the b3meter Apache 2.0 header
- [ ] `./gradlew spotlessApply` runs without error
- [ ] `./gradlew spotlessCheck` exits 0 after apply

**Dependencies:** none

---

### T-002 — Apply license headers to all Java/Kotlin/Proto source files
**FR:** FR-001
**Effort:** S (30 minutes — automated via T-001)
**Files:** All 388 Java/Kotlin files in `modules/` and `perf/`, all `.proto` files

**Acceptance criteria:**
- [ ] `./gradlew spotlessApply` adds the Apache 2.0 header to every `.java`, `.kt`, `.kts`, `.proto` file that is missing it
- [ ] `./gradlew spotlessCheck` exits 0 on the full set of modules
- [ ] No source file in `modules/` or `perf/` is missing the header

**Dependencies:** T-001

---

### T-003 — Apply license headers to TypeScript/TSX source files
**FR:** FR-001
**Effort:** S (1 hour)
**Files:** All 102 `.ts`/`.tsx` files in `web-ui/src/`

**Approach:** Either extend Spotless to cover TypeScript (via `prettier` + `licenseHeader`) or use a small Node script (`node scripts/add-license-header.js`) run as part of the frontend lint step.

**Acceptance criteria:**
- [ ] All `.ts` and `.tsx` files in `web-ui/src/` carry the `// Copyright 2024-2026 b3meter Contributors` Apache 2.0 header
- [ ] Mechanism is automated (not a one-time manual edit) so new files are covered
- [ ] Frontend CI job enforces the check

**Dependencies:** T-001

---

### T-004 — Git history secret scan
**FR:** FR-002
**Effort:** S (1 hour including setup)
**Files:** No code changes; produces `specs/013-oss-readiness/gitleaks-report.txt`

**Steps:**
1. Install gitleaks: `brew install gitleaks` (or download binary)
2. Run: `gitleaks detect --source . --log-opts="--all" --report-path specs/013-oss-readiness/gitleaks-report.txt --report-format json`
3. Review findings
4. If zero findings: task complete
5. If findings: triage — are they real secrets or false positives?
   - False positive → add to `.gitleaks.toml` allowlist with comment
   - Real secret → follow remediation in NFR-002

**Acceptance criteria:**
- [ ] Scan has been run against full git history
- [ ] `gitleaks-report.txt` exists in `specs/013-oss-readiness/` (empty = no findings)
- [ ] Zero HIGH/CRITICAL findings in the report (or all findings triaged and documented)

**Dependencies:** none

---

### T-005 — Version bump: 0.1.0-SNAPSHOT → 0.1.0
**FR:** FR-004
**Effort:** XS (15 minutes)
**Files:** `build.gradle.kts` (root)

**Acceptance criteria:**
- [ ] `version = "0.1.0"` in root `build.gradle.kts` (no `-SNAPSHOT` suffix)
- [ ] `./gradlew bootJar` produces `app-0.1.0.jar` and `worker-0.1.0.jar`
- [ ] Release workflow tag `v0.1.0` triggers correctly

**Dependencies:** none (can be done last, just before tagging)

---

## Phase 2 — High Priority (complete before or shortly after publishing)

### T-006 — Add SECURITY.md
**FR:** FR-005
**Effort:** S (1 hour)
**Files:** `SECURITY.md` (new, repo root)

**Content must include:**
- Supported versions table (v0.1.x = supported)
- How to report a vulnerability (GitHub Security Advisory: `github.com/b3-cognition/b3-meter/security/advisories/new`)
- Response timeline (acknowledge within 72 hours, patch within 30 days for HIGH/CRITICAL)
- Public disclosure policy (coordinated disclosure, 90 days)
- What NOT to report via public issues (active exploits, unpatched CVEs)

**Acceptance criteria:**
- [ ] `SECURITY.md` exists at repo root
- [ ] All four sections are present and complete
- [ ] GitHub picks up the file and shows "Security policy" tab in Insights

**Dependencies:** none

---

### T-007 — Add CODE_OF_CONDUCT.md and GitHub issue templates
**FR:** FR-005
**Effort:** S (45 minutes)
**Files:**
- `CODE_OF_CONDUCT.md` (new, repo root) — Contributor Covenant 2.1
- `.github/ISSUE_TEMPLATE/bug_report.md` (new)
- `.github/ISSUE_TEMPLATE/feature_request.md` (new)
- `.github/ISSUE_TEMPLATE/config.yml` (new — disable blank issues, link to discussions)

**Acceptance criteria:**
- [ ] `CODE_OF_CONDUCT.md` is Contributor Covenant 2.1 with contact email filled in
- [ ] `bug_report.md` template has: description, reproduction steps, expected vs actual, environment (OS, Java version, b3meter version)
- [ ] `feature_request.md` template has: problem statement, proposed solution, alternatives considered
- [ ] `config.yml` directs general questions to GitHub Discussions

**Dependencies:** none

---

### T-008 — .gitignore hardening
**FR:** FR-006
**Effort:** XS (15 minutes)
**Files:** `.gitignore`

**Add patterns:**
```
# Environment / secrets
.env
.env.local
.env.*.local

# Key material
*.key
*.pem
*.p12
*.jks
secrets/

# gitleaks config (not secrets, but development tool)
.gitleaks.toml
```

**Acceptance criteria:**
- [ ] All listed patterns are present in `.gitignore`
- [ ] `git status` on a fresh clone is clean

**Dependencies:** none

---

### T-009 — Resolve web-ui Gradle TODO
**FR:** FR-007
**Effort:** XS (15 minutes)
**Files:** `modules/web-ui/build.gradle.kts`

**Acceptance criteria:**
- [ ] The TODO comment on line 14 is removed
- [ ] A clear comment explains that this is a Gradle placeholder and the real React source is in `/web-ui/` at repo root
- [ ] Build still succeeds after the comment change

**Dependencies:** none

---

### T-010 — Add Spotless + OWASP steps to CI; final clean build verification
**FR:** FR-008, FR-009, FR-003
**Effort:** M (2–3 hours)
**Files:**
- `.github/workflows/ci.yml` — add `spotless-check` job and `dependency-check` job
- Optional: add OWASP Dependency-Check Gradle plugin to `build.gradle.kts`

**CI additions:**

```yaml
# Add to ci.yml jobs:
spotless-check:
  runs-on: ubuntu-latest
  steps:
    - uses: actions/checkout@v4
    - uses: actions/setup-java@v4
      with: { distribution: temurin, java-version: '21' }
    - run: ./gradlew spotlessCheck

dependency-check:
  runs-on: ubuntu-latest
  steps:
    - uses: actions/checkout@v4
    - uses: actions/setup-java@v4
      with: { distribution: temurin, java-version: '21' }
    - run: ./gradlew dependencyCheckAggregate
    - uses: actions/upload-artifact@v4
      with:
        name: dependency-check-report
        path: build/reports/dependency-check-report.html
```

**Clean build verification steps (manual, once):**
1. `git clone git@github.com:b3-cognition/b3-meter.git /tmp/b3meter-verify`
2. `cd /tmp/b3meter-verify && ./gradlew clean build --no-daemon`
3. `./gradlew test`
4. Document result in `specs/013-oss-readiness/clean-build-result.txt`

**Acceptance criteria:**
- [ ] CI has a `spotless-check` job that runs on PRs
- [ ] CI has a `dependency-check` job (can be weekly scheduled, not per-PR due to NVD rate limits)
- [ ] Clean build from fresh clone succeeds: `BUILD SUCCESSFUL`
- [ ] All tests pass on the fresh clone
- [ ] `clean-build-result.txt` exists with the build output summary
- [ ] Zero HIGH/CRITICAL CVEs in OWASP report (or all accepted with rationale)

**Dependencies:** T-001, T-002, T-003, T-005

---

## Phase 3 — GitHub Repository Configuration (manual)

### T-011 — GitHub repository settings
**FR:** FR-010
**Effort:** XS (20 minutes, manual)

**Manual checklist (performed in GitHub.com UI):**
- [ ] Repository description set: "Modern load testing platform — compatible with Apache JMeter JMX test plans"
- [ ] Topics added: `load-testing`, `jmeter`, `performance-testing`, `java`, `kotlin`, `spring-boot`, `grpc`, `open-source`
- [ ] Default branch confirmed as `b3meter`
- [ ] Branch protection rule on `b3meter`: require PR review (1 approver), require status checks (`build`, `spotless-check`), disallow force push
- [ ] Discussions tab enabled
- [ ] Wiki tab disabled
- [ ] Security advisories enabled (auto-enabled once SECURITY.md is present)
- [ ] Repository made **Public** (final step — do this LAST after all other tasks complete)

**Dependencies:** T-001 through T-010 (all must be complete before making public)

---

## Dependency Graph

```
T-001 ──► T-002 ──┐
     └──► T-003 ──┼──► T-010 ──► T-011 (go public)
T-004 ────────────┤
T-005 ────────────┤
T-006 ────────────┤
T-007 ────────────┤
T-008 ────────────┤
T-009 ────────────┘
```

## Critical Path

`T-001 → T-002 → T-010 → T-011`

License headers (T-001/T-002) are the longest path because Spotless must be configured before headers can be applied and CI must be updated to enforce them (T-010). Everything else is parallelizable.

## Suggested Work Order

| Order | Task | Can Parallelize With |
|-------|------|---------------------|
| 1 | T-001 — Spotless setup | — |
| 2 | T-002 — Java/Kotlin headers | T-003, T-004, T-006, T-007, T-008, T-009 |
| 3 | T-003 — TypeScript headers | T-002 (parallel) |
| 4 | T-004 — gitleaks scan | T-002, T-003 (parallel) |
| 5 | T-005 — version bump | last, just before tag |
| 6 | T-006 — SECURITY.md | T-002, T-003 (parallel) |
| 7 | T-007 — CoC + issue templates | T-002, T-003 (parallel) |
| 8 | T-008 — .gitignore | T-002, T-003 (parallel) |
| 9 | T-009 — web-ui TODO | T-002, T-003 (parallel) |
| 10 | T-010 — CI + clean build | after T-001, T-002, T-003 done |
| 11 | T-011 — GitHub settings + go public | ALL complete |
