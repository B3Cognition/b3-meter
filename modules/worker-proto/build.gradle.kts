/**
 * worker-proto — protobuf + gRPC definitions.
 *
 * Contains .proto service definitions for the controller↔worker wire protocol
 * used in distributed test mode. Generates Java stubs via the protobuf plugin.
 */
plugins {
    `java-library`
    alias(libs.plugins.protobuf)
}

dependencies {
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.stub)
    implementation(libs.protobuf.java)
    // Required by gRPC generated code (@javax.annotation.Generated)
    implementation("javax.annotation:javax.annotation-api:1.3.2")
    runtimeOnly(libs.grpc.netty.shaded)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.grpc.testing)
}

// Version strings must stay in sync with gradle/libs.versions.toml
// Using string literals here because protobuf plugin's artifact DSL does not
// accept Provider<String> — version catalog provider resolution is not applicable
// inside the protobuf {} configuration block.
val protobufArtifactVersion = "3.25.3"   // libs.versions.protobuf
val grpcArtifactVersion = "1.63.0"        // libs.versions.grpc

// Fail fast if these literals drift from the version catalog (CODE REVIEW ISSUE-4)
val catalogProtobufVersion = rootProject.extensions
    .getByType<VersionCatalogsExtension>().named("libs")
    .findVersion("protobuf").get().requiredVersion
val catalogGrpcVersion = rootProject.extensions
    .getByType<VersionCatalogsExtension>().named("libs")
    .findVersion("grpc").get().requiredVersion
check(protobufArtifactVersion == catalogProtobufVersion) {
    "worker-proto: protobufArtifactVersion ('$protobufArtifactVersion') does not match " +
    "libs.versions.toml ('$catalogProtobufVersion'). Update build.gradle.kts."
}
check(grpcArtifactVersion == catalogGrpcVersion) {
    "worker-proto: grpcArtifactVersion ('$grpcArtifactVersion') does not match " +
    "libs.versions.toml ('$catalogGrpcVersion'). Update build.gradle.kts."
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufArtifactVersion"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcArtifactVersion"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                create("grpc")
            }
        }
    }
}

description = "Worker-proto: gRPC .proto definitions for distributed mode"
