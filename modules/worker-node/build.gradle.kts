/**
 * worker-node — Spring Boot application.
 *
 * Worker node process that receives test plan fragments from the controller,
 * executes them via the engine-adapter, and streams results back via gRPC.
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
// grpc-testing also pulls junit:junit:4.13.2 which would otherwise further downgrade
// junit-platform-commons — the explicit pins below prevent that regression.
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

    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.grpc.netty.shaded)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.protobuf)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.grpc.testing)
    testImplementation("io.grpc:grpc-inprocess:1.63.0")
}

description = "Worker-node: worker node process for distributed testing"
