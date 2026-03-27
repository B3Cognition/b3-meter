/**
 * engine-adapter — Java library.
 *
 * Adapts the existing JMeter engine to the new engine-service interfaces.
 * This is the anti-corruption layer between legacy JMeter internals and
 * the clean architecture of jmeter-next.
 */
plugins {
    `java-library`
    application
}

application {
    mainClass.set("com.jmeternext.engine.adapter.cli.JMeterNextCli")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

dependencies {
    api(project(":modules:engine-service"))

    implementation(rootProject.libs.xstream)
    implementation(rootProject.libs.picocli)

    // Apache HttpComponents 5 — HTTP/2 async client (HC5) and classic blocking client
    implementation(rootProject.libs.httpclient5)

    testImplementation(platform(rootProject.libs.junit.bom))
    testImplementation(rootProject.libs.junit.jupiter)
    // WireMock for HTTP integration tests (GET/POST, timeouts, protocol negotiation)
    testImplementation(rootProject.libs.wiremock)
}

description = "Engine-adapter: adapts JMeter engine to engine-service interfaces"
