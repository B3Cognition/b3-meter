/**
 * perf — standalone benchmark module.
 *
 * Provides micro-/macro-benchmarks for engine throughput, virtual-thread
 * scalability, and API latency. Produces a results.json file that is compared
 * against the checked-in .baseline.json to detect performance regressions in CI.
 *
 * This module is intentionally kept framework-free: it depends only on
 * engine-service (interfaces) and Jackson (JSON serialisation), plus JUnit 5
 * for the benchmark harness.
 */
plugins {
    application
}

dependencies {
    // Engine interface (no Spring, no JMeter internals)
    implementation(project(":modules:engine-service"))

    // JSON serialisation for results.json / .baseline.json
    implementation(rootProject.libs.jackson.databind)
    implementation(rootProject.libs.jackson.annotations)

    testImplementation(platform(rootProject.libs.junit.bom))
    testImplementation(rootProject.libs.junit.jupiter)
}

application {
    mainClass.set("com.jmeternext.perf.BenchmarkRunner")
}

// Fat/shadow jar for `java -jar perf/benchmark-runner.jar`
tasks.register<Jar>("benchmarkJar") {
    group = "build"
    description = "Assembles a self-contained benchmark runner JAR"
    archiveFileName.set("benchmark-runner.jar")
    destinationDirectory.set(file("${projectDir}"))

    manifest {
        attributes("Main-Class" to "com.jmeternext.perf.BenchmarkRunner")
    }

    // Include runtime classpath in fat jar
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    from(sourceSets.main.get().output)

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

description = "Perf: benchmark runner and performance regression suite"
