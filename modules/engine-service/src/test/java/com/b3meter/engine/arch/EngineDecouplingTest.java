package com.jmeternext.engine.arch;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Tag;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit rules that enforce GUI/Engine decoupling.
 *
 * <p>These rules implement Constitution Principle I: the engine must be completely
 * decoupled from any GUI framework (Swing, AWT) and from Spring, so it can run
 * headlessly in CI and in distributed worker-node deployments.
 *
 * <p>Rules are tagged {@code @Tag("arch")} so they can be run selectively:
 * {@code ./gradlew :modules:engine-service:test --tests '*EngineDecouplingTest*'}
 */
@Tag("arch")
@AnalyzeClasses(packages = "com.jmeternext")
public class EngineDecouplingTest {

    /**
     * Engine classes must never import GUI packages.
     *
     * <p>Enforces Constitution Principle I: engine-first decoupling.
     * The test engine runs the plan; it must not know whether a GUI exists.
     */
    @ArchTest
    static final ArchRule engineMustNotDependOnGui =
        noClasses().that().resideInAPackage("..engine..")
            .should().dependOnClassesThat().resideInAPackage("..gui..")
            .orShould().dependOnClassesThat().resideInAPackage("javax.swing..")
            .orShould().dependOnClassesThat().resideInAPackage("java.awt..")
            .because("Engine must be decoupled from GUI (Constitution Principle I)");

    /**
     * Thread-management classes must never import GUI packages.
     *
     * <p>Thread groups, virtual user scheduling, and ramp-up logic must work
     * without a display — e.g., inside a headless worker-node container.
     *
     * <p>{@code allowEmptyShould(true)} is required because the {@code ..threads..}
     * package does not exist yet in this clean-slate module; it will be created in
     * later phases and this rule will start enforcing once classes appear.
     */
    @ArchTest
    static final ArchRule threadsMustNotDependOnGui =
        noClasses().that().resideInAPackage("..threads..")
            .should().dependOnClassesThat().resideInAPackage("..gui..")
            .orShould().dependOnClassesThat().resideInAPackage("javax.swing..")
            .orShould().dependOnClassesThat().resideInAPackage("java.awt..")
            .because("Thread management must be GUI-independent")
            .allowEmptyShould(true);

    /**
     * Web API layer must never import Swing or AWT.
     *
     * <p>The REST/SSE layer serves browser clients. It must not carry any
     * desktop-GUI dependency that would prevent running in a server JRE.
     *
     * <p>{@code allowEmptyShould(true)} is required because the {@code ..web..}
     * package lives in the web-api module, not here. The rule is declared here
     * as part of the central architecture policy; the web-api module will import
     * and apply it once it is created.
     */
    @ArchTest
    static final ArchRule webApiMustNotDependOnSwing =
        noClasses().that().resideInAPackage("..web..")
            .should().dependOnClassesThat().resideInAPackage("javax.swing..")
            .orShould().dependOnClassesThat().resideInAPackage("java.awt..")
            .because("Web API layer must never import Swing")
            .allowEmptyShould(true);

    /**
     * engine-service module must have zero Spring dependencies.
     *
     * <p>engine-service is a plain Java library (see build.gradle.kts). Spring
     * annotations or context references here would couple the clean API to a
     * specific DI framework, violating the portability requirement.
     */
    @ArchTest
    static final ArchRule engineServiceMustBeFrameworkFree =
        noClasses().that().resideInAPackage("..engine.service..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework..")
            .because("engine-service module must have zero Spring dependencies");
}
