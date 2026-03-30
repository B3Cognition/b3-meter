# Spec 014 — Complete b3meter → b3meter String Rename

**Feature:** Eliminate all remaining `b3meter` / `b3meter` string references
**Status:** Ready for implementation
**Created:** 2026-03-30
**Codebase:** b3meter, `git@github.com:B3Cognition/b3-meter.git`

---

## Background

Spec 012 renamed Java packages (`com.b3meter` → `com.b3meter`) and most file names.
The proto package was also updated (`b3meter.worker` → `b3meter.worker`).
However, 352 string occurrences remain across 60 files.

Source code is 100% migrated. Remaining work:
- Operational config (Makefile, FTP mock server, package-lock.json)
- Documentation and spec files
- Branch name in CI workflow (decision required)

---

## Requirements

### FR-001 — Operational source/config files renamed
All non-documentation files referencing the old name are updated.

**Scope:**
- `Makefile` lines 19–20: Docker image tags `b3meter-controller/worker` → `b3meter-controller/worker`
- `test-servers/ftp-mock/server.js:62`: FTP greeting string
- `web-ui/package-lock.json`: stale name `b3meter-ui` → `b3meter-ui`

**Acceptance criteria:**
- [ ] `grep -r "b3meter" Makefile` → 0 hits
- [ ] `grep -r "b3meter" test-servers/` → 0 hits
- [ ] `grep "b3meter-ui" web-ui/package-lock.json` → 0 hits
- [ ] `./gradlew build` still passes

### FR-002 — Documentation updated
All `b3meter` / `b3meter` references in `docs/`, `modules/*/`, `deploy/`, `specs/` updated.

**Preserve intentionally:**
- `UPGRADING.md` — migration guide FROM b3meter, references are contextual and must stay
- Historical commit messages (in git history, not source files)

**Acceptance criteria:**
- [ ] `grep -rn "b3meter" modules/worker-proto/VERSIONING.md` → 0 hits (proto package name was already updated in .proto file)
- [ ] `grep -rn "b3meter\|b3meter" docs/ deploy/` → 0 hits (excluding UPGRADING.md)
- [ ] `specs/` docs updated (old package references `com.b3meter` → `com.b3meter`)

### FR-003 — Branch name decision
Current default branch is `b3meter`. This name appears in:
- `.github/workflows/ci.yml` (branch trigger filters)
- GitHub branch protection rules

**Decision required from user:**
- Option A: Rename branch `b3meter` → `main` (standard OSS convention)
- Option B: Rename branch `b3meter` → `b3meter`
- Option C: Keep branch name as `b3meter` (only update string refs elsewhere)

---

## Out of Scope
- Java package names (already done in spec 012)
- Proto file package declarations (already `b3meter.worker`)
- UPGRADING.md intentional b3meter references
- Git history (immutable)
