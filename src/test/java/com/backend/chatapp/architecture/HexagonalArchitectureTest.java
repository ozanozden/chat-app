package com.backend.chatapp.architecture;


import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

class HexagonalArchitectureTest {

    private static JavaClasses importedClasses;

    @BeforeAll
    static void setup() {
        importedClasses = new ClassFileImporter()
                .importPackages("com.backend.chatapp.domain",
                                 "com.backend.chatapp.application",
                                 "com.backend.chatapp.infrastructure",
                                 "com.backend.chatapp.web");
    }

    @Test
    void domainLayerShouldNotDependOnInfrastructureLayer() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .because("Domain layer must be independent of infrastructure");

        rule.check(importedClasses);
    }

    @Test
    void applicationLayerShouldNotDependOnWebLayer() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..web..")
                .because("Application layer must be independent of web");

        rule.check(importedClasses);
    }

    @Test
    void applicationLayerShouldNotDependOnInfrastructureLayer() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .because("Application layer should only depend on domain (ports pattern)");

        rule.check(importedClasses);
    }

    @Test
    void domainShouldNotUseSpringFramework() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "com.datastax.."
                )
                .because("Domain must be framework-agnostic");

        rule.check(importedClasses);
    }

    @Test
    void servicesShouldFollowNamingConvention() {
        ArchRule rule = classes()
                .that().areAnnotatedWith(org.springframework.stereotype.Service.class)
                .should().haveSimpleNameEndingWith("Service")
                .because("Service classes must end with 'Service' suffix");

        rule.check(importedClasses);
    }

    @Test
    void controllerShouldFollowNamingConvention() {
        ArchRule rule = classes()
                .that().areAnnotatedWith(RestController.class)
                .should().haveSimpleNameEndingWith("Controller")
                .because("Controllers must have @RestController and end with \"Controller");

        rule.check(importedClasses);
    }

    @Test
    void repositoryAdaptersShouldImplementPortInterfaces() {
        ArchRule rule = classes()
                .that().resideInAPackage("..infrastructure.adapter..")
                .and().haveSimpleNameEndingWith("Impl")
                .should().beAssignableTo(com.backend.chatapp.application.port.MessageRepository.class)
                .orShould().beAssignableTo(com.backend.chatapp.application.port.UserRepository.class)
                .because("Adapters must implement port interfaces (ports & adapters pattern)");

        rule.check(importedClasses);
    }

    @Test
    void portInterfacesShouldResideInApplicationLayer() {
        ArchRule rule = classes()
                .that().areInterfaces()
                .and().haveSimpleNameEndingWith("Repository")
                .should().resideInAPackage("..application.port..")
                .because("Repository ports must be defined in application layer");

        rule.check(importedClasses);
    }
}