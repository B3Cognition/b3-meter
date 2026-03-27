/**
 * distributed-controller — Spring Boot application.
 *
 * Controller node for distributed load testing. Coordinates worker nodes,
 * distributes test plans, and aggregates results via gRPC.
 */
plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    `java`
}

// See worker-node/build.gradle.kts: override Spring Boot BOM JUnit pins so they stay
// aligned with the junit-bom:5.11.0 used across this project.  grpc-testing pulls
// junit:junit:4.13.2 which would otherwise downgrade junit-platform-commons.
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
    implementation(project(":modules:worker-proto"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.grpc.netty.shaded)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.protobuf)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.grpc.testing)
    testImplementation("io.grpc:grpc-inprocess:1.63.0")
}

description = "Distributed-controller: controller node for distributed testing"
