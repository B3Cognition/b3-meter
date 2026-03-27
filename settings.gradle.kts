pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Foojay toolchain resolver: auto-downloads JDK 21 if not installed locally
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
    // libs.versions.toml at gradle/libs.versions.toml is auto-discovered by Gradle 8.x
}

rootProject.name = "jmeter-next"

// Module: API interfaces — UIBridge, TestRunContext
include("modules:engine-service")
project(":modules:engine-service").projectDir = file("modules/engine-service")

// Module: adapts existing JMeter engine to new interfaces
include("modules:engine-adapter")
project(":modules:engine-adapter").projectDir = file("modules/engine-adapter")

// Module: gRPC .proto definitions for distributed mode
include("modules:worker-proto")
project(":modules:worker-proto").projectDir = file("modules/worker-proto")

// Module: controller node for distributed testing
include("modules:distributed-controller")
project(":modules:distributed-controller").projectDir = file("modules/distributed-controller")

// Module: worker node process
include("modules:worker-node")
project(":modules:worker-node").projectDir = file("modules/worker-node")

// Module: Spring Boot REST + SSE backend
include("modules:web-api")
project(":modules:web-api").projectDir = file("modules/web-api")

// Module: React 19 + Vite frontend — placeholder only
include("modules:web-ui")
project(":modules:web-ui").projectDir = file("modules/web-ui")

// Module: performance regression benchmarks
include("perf")
project(":perf").projectDir = file("perf")
