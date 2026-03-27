/**
 * engine-service — plain Java library.
 *
 * Defines the core API interfaces (UIBridge, TestRunContext) that all other
 * modules depend on. Must NOT depend on any concrete implementation or framework
 * to enforce the Engine-First Decoupling principle (Principle I of the constitution).
 */
plugins {
    `java-library`
}

dependencies {
    // No framework dependencies — pure API interfaces only (Principle I)
    testImplementation(platform(rootProject.libs.junit.bom))
    testImplementation(rootProject.libs.junit.jupiter)
    testImplementation(rootProject.libs.archunit)
}

description = "Engine-service: API interfaces (UIBridge, TestRunContext)"
