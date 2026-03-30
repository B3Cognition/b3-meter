import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    alias(libs.plugins.owasp.depcheck)
    alias(libs.plugins.license.report)
    alias(libs.plugins.spotless)
}

// Root project — no sources; configuration is applied to subprojects only
group = "com.b3meter"
version = "0.1.0"

// Java 21 toolchain applied to all Java subprojects
subprojects {
    apply(plugin = "java")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    dependencies {
        val testImplementation by configurations
        val testRuntimeOnly by configurations
        // JUnit 5 platform on every module
        testImplementation(platform(rootProject.libs.junit.bom))
        testImplementation(rootProject.libs.junit.jupiter)
        testRuntimeOnly(rootProject.libs.junit.jupiter.engine)
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events(
                TestLogEvent.PASSED,
                TestLogEvent.FAILED,
                TestLogEvent.SKIPPED
            )
            exceptionFormat = TestExceptionFormat.FULL
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Werror"))
    }
}

// OWASP Dependency-Check configuration (root-level aggregate scan)
dependencyCheck {
    failBuildOnCVSS = 7.0f
    suppressionFile = "${rootDir}/config/owasp-suppressions.xml"
    formats = listOf("HTML", "JSON")
    outputDirectory = "${layout.buildDirectory.asFile.get()}/reports/dependency-check"
}

// License report — generates THIRD-PARTY-LICENSES.txt from all runtime dependencies
// Run: ./gradlew generateLicenseReport
// Output: build/reports/licenses/THIRD-PARTY-LICENSES.txt
licenseReport {
    outputDir = "${layout.buildDirectory.asFile.get()}/reports/licenses"
    renderers = arrayOf<com.github.jk1.license.render.ReportRenderer>(
        com.github.jk1.license.render.TextReportRenderer("THIRD-PARTY-LICENSES.txt")
    )
    configurations = arrayOf("runtimeClasspath")
    excludeGroups = arrayOf("com.b3meter")
    filters = arrayOf<com.github.jk1.license.filter.DependencyFilter>(
        com.github.jk1.license.filter.LicenseBundleNormalizer()
    )
    // Uncomment to fail build on blocked licenses:
    // allowedLicensesFile = file("config/allowed-licenses.json")
}

// Spotless — license header enforcement
// Run: ./gradlew spotlessApply  (add/fix headers)
//      ./gradlew spotlessCheck  (verify — used in CI)
spotless {
    // Spotless is not wired into the `check` lifecycle — run explicitly via
    // `./gradlew spotlessCheck` (CI) or `./gradlew spotlessApply` (local fix).
    isEnforceCheck = false
    java {
        target("modules/**/*.java", "perf/**/*.java")
        targetExclude("**/build/**")
        licenseHeaderFile(rootProject.file("config/license-header.java"))
    }
    kotlin {
        target("modules/**/src/**/*.kt", "perf/**/src/**/*.kt")
        licenseHeaderFile(rootProject.file("config/license-header.java"))
    }
    format("proto") {
        target("modules/**/*.proto")
        targetExclude("**/build/**")
        licenseHeaderFile(rootProject.file("config/license-header.java"),
            "(syntax|package|import|option|service|message|enum)")
    }
    format("typescript") {
        target("web-ui/src/**/*.ts", "web-ui/src/**/*.tsx")
        licenseHeaderFile(rootProject.file("config/license-header.ts"),
            "(import |export |const |let |var |function |class |interface |type |declare |@|describe|it |test )")
    }
}

// Convenience task to list all modules
tasks.register("listModules") {
    group = "help"
    description = "Lists all modules in this multi-module project"
    doLast {
        subprojects.forEach { println("  :${it.path}") }
    }
}
