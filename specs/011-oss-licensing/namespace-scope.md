# Namespace Change Scope Analysis

## Summary

The product name "jMeter Next" and group ID `com.b3meter` are embedded at
two distinct layers: a thin set of user-facing surfaces (README, docs, Docker
images, OpenAPI title, CLI `--version` output, HTML title) that are legally
required to change, and a large internal layer of 409 Java package declarations
plus 541 import statements that are technically optional but strongly recommended
for contributor clarity. No Maven Central publishing plugin is configured, so
`com.b3meter` is **not** currently published as a public artifact — but if
Central publishing is ever added the group ID becomes legally required to change
as well.

## Counts

| Item | Count |
|------|-------|
| Java/Kotlin files with `package com.b3meter` | 409 |
| Java/Kotlin files with `import com.b3meter` | 541 |
| Gradle files setting `group = "com.b3meter"` | 1 (`build.gradle.kts` root) |
| Gradle files referencing `com.b3meter` main class | 2 (`perf/build.gradle.kts`, `modules/engine-adapter/build.gradle.kts`) |
| User-facing name locations (docs/UI/CLI/Docker) | ~20 distinct locations across 10+ files |

## Legally Required Changes (trademark)

| Location | Change Needed | Files |
|----------|---------------|-------|
| `README.md` | Rename product throughout | 1 |
| `CONTRIBUTING.md` | Rename product throughout | 1 |
| `docs/getting-started.md` | Rename product throughout | 1 |
| `docs/configuration.md` | Rename product throughout | 1 |
| `deploy/helm/b3meter/README.md` | Rename product throughout | 1 |
| `deploy/helm/b3meter/Chart.yaml` | `name: b3meter` → new name | 1 |
| `deploy/helm/b3meter/values.yaml` | `image.repository: b3meter/controller` and `b3meter/worker` | 1 |
| `web-ui/index.html` | `<title>jMeter Next</title>` → new name | 1 |
| `web-ui/src/App.tsx` | `"jMeter Next"` toolbar title + About dialog | 1 |
| `web-ui/src/components/MenuBar/MenuBar.tsx` | `'About jMeter Next'` menu item | 1 |
| `web-ui/package.json` | `"name": "b3meter-ui"` | 1 |
| `modules/web-api/src/main/java/.../OpenApiConfig.java` | `.title("jMeter Next API")` — appears in Swagger UI visible to API consumers | 1 |
| `.github/workflows/ci.yml` | Docker image tags `b3meter/controller`, `b3meter/worker` | 1 |
| GitHub repository slug | `b3meter` → new name | external/manual |

## Recommended Changes (consistency)

| Location | Change Needed | Files | Effort |
|----------|---------------|-------|--------|
| All Java `package com.b3meter.*` declarations | Rename to `com.{newname}.*` | 409 | Medium — IDE rename refactor |
| All Java `import com.b3meter.*` statements | Update to match new packages | 541 | Auto-resolved by IDE rename |
| Root `build.gradle.kts` `group = "com.b3meter"` | Update group ID | 1 | Trivial |
| `perf/build.gradle.kts` mainClass reference | Update fully-qualified class name | 1 | Trivial |
| `modules/engine-adapter/build.gradle.kts` mainClass | Update fully-qualified class name | 1 | Trivial |
| `B3MeterCli.java` — `@Command(name="b3meter", ...)` string constants | Rename CLI binary name in version/description strings | 1 | Trivial |
| `HtmlReportGenerator.java` — `<title>b3meter Test Report</title>` | Rename in generated HTML report | 1 | Trivial |
| `JwtTokenService.java` — `ISSUER = "b3meter"` | Rename JWT issuer claim (breaks existing tokens on deploy) | 1 | Trivial (with migration note) |
| `modules/web-api/src/main/resources/application.yml` | `spring.application.name: b3meter` and metrics tag | 1 | Trivial |
| `gradle.properties` comment | Update comment | 1 | Trivial |
| `docker-compose.test.yml` network/service name `b3meter` | Rename | 1 | Trivial |
| `web-ui/src/themes/themes.ts` + `ChaosLoad.tsx` | `localStorage` keys `'b3meter-theme'`, `'b3meter-chaos-config'` (breaks existing user preferences in browser) | 2 | Trivial (with migration note) |
| Source file `B3MeterCli.java`, `B3MeterCliTest.java` | File/class rename | 2 | Trivial |
| `specs/` markdown files | Update internal spec docs | 30+ | Low priority |

## Not Required (internal only)

| Item | Reason it's optional |
|------|----------------------|
| Test class package names (`com.b3meter.*.test`) | Never published; invisible at runtime |
| Java/Kotlin package names generally | Not consumer-facing at runtime; no Maven Central publishing configured |
| `gradle.properties` comment text | Developer documentation only |
| `docker-compose.test.yml` internal network name | Used only in CI, not shipped to users |
| Javadoc comments containing "b3meter" | Developer-facing only |
| `XStreamSecurityPolicy.java` inline comment about "b3meter own types" | Code comment, not published |

## Effort Assessment

- **Legally required only** (docs, Docker images, UI title, OpenAPI title, Helm chart, repo slug): 2–4 hours, low risk, purely text substitution across ~15 files.
- **With package rename included**: 1–2 days. The 409 package declarations + 541 imports form a single IDE-level refactor: in IntelliJ, Refactor → Rename Package on `com.b3meter` updates all declarations and imports atomically. The build system has only 3 mainClass references to update manually after the rename. Risk is low because the Java compiler will catch any missed reference.
- **IDE rename refactoring**: Easy — IntelliJ and VS Code (Kotlin) both support package-level rename with full cross-reference update. No reflection-based class loading was observed that would silently break.
- **Risk level**: Low for docs/UI changes. Medium for the package rename (purely due to volume; the tooling handles it well, but a large diff requires careful PR review and CI must pass before merge).

## Recommendation

**Minimum to satisfy trademark concern:** rename the ~15 user-facing locations
(README, docs, HTML title, UI toolbar, Helm chart name, Docker image names,
OpenAPI title, package.json name, GitHub repo slug). The Java packages
`com.b3meter.*` are **internal only** — they are not published to Maven
Central and do not appear in any artifact consumers would reference — so they
are **not legally required to change right now**.

**Practical recommendation:** do both in the same PR. The package rename is a
single IDE operation that takes minutes of mechanical work. Leaving
`com.b3meter` in place after renaming the product creates permanent confusion
for contributors and will be a bigger lift later (especially if Maven Central
publishing is ever added). The legal-only change is trivial; the full rename is
still easy and finishes the job cleanly.
