import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    alias(libs.plugins.owasp.depcheck)
}

// Root project — no sources; configuration is applied to subprojects only
group = "com.jmeternext"
version = "0.1.0-SNAPSHOT"

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

// Convenience task to list all modules
tasks.register("listModules") {
    group = "help"
    description = "Lists all modules in this multi-module project"
    doLast {
        subprojects.forEach { println("  :${it.path}") }
    }
}
