/**
 * web-api — Spring Boot REST + SSE backend.
 *
 * Exposes REST endpoints and Server-Sent Events (SSE) streams for the
 * React frontend. Depends on engine-service interfaces only — never on
 * engine-adapter directly (enforced by ArchUnit in T002).
 */
plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    `java`
}

// Override Spring Boot BOM's JUnit pins.
// Spring Boot 3.3.0 manages junit-platform-* to 1.10.2 and junit-jupiter-* to 5.10.2,
// both of which are incompatible with the junit-bom:5.11.0 used by the rest of this
// multi-module project. The dependencyManagement block takes precedence over the
// imported BOM constraint, so all JUnit modules are pinned to the 5.11.0 family here.
dependencyManagement {
    dependencies {
        dependency("org.junit.platform:junit-platform-commons:1.11.0")
        dependency("org.junit.platform:junit-platform-engine:1.11.0")
        dependency("org.junit.platform:junit-platform-launcher:1.11.0")
        dependency("org.junit.jupiter:junit-jupiter-api:5.11.0")
        dependency("org.junit.jupiter:junit-jupiter-engine:5.11.0")
        dependency("org.junit.jupiter:junit-jupiter-params:5.11.0")
        dependency("org.junit.jupiter:junit-jupiter:5.11.0")
    }
}

dependencies {
    implementation(project(":modules:engine-service"))
    implementation(project(":modules:engine-adapter"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.micrometer.registry.prometheus)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.jackson.databind)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")
    implementation("ch.qos.logback.contrib:logback-json-classic:0.1.5")
    implementation("ch.qos.logback.contrib:logback-jackson:0.1.5")
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(platform(rootProject.libs.junit.bom))
}

description = "Web-api: Spring Boot REST + SSE backend"

// Auto-kill any process on port 8080 before bootRun
// Prevents "Port 8080 was already in use" failures
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    doFirst {
        val port = 8080
        try {
            val process = ProcessBuilder("lsof", "-ti", ":$port")
                .redirectErrorStream(true)
                .start()
            val pids = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            if (pids.isNotBlank()) {
                logger.lifecycle("Port $port in use by PID(s): $pids — killing...")
                pids.split("\\s+".toRegex()).forEach { pid ->
                    ProcessBuilder("kill", "-9", pid.trim()).start().waitFor()
                }
                Thread.sleep(2000) // wait for port to free
                logger.lifecycle("Port $port freed. Starting bootRun...")
            }
        } catch (e: Exception) {
            logger.warn("Could not check port $port: ${e.message}")
        }
    }
}
