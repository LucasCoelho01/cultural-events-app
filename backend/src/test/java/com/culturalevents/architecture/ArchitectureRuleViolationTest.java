package com.culturalevents.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova que cada {@code ArchRule} de {@link ArchitectureTest} de fato detecta uma
 * violação, usando classes de fixture em {@code com.culturalevents.archfixture}
 * (src/test apenas) que violam deliberadamente cada regra.
 */
class ArchitectureRuleViolationTest {

    private static JavaClasses fixtureClasses;

    @BeforeAll
    static void importFixtures() {
        fixtureClasses = new ClassFileImporter().importPackages("com.culturalevents.archfixture");
    }

    @Test
    void domainRuleDetectaViolacaoNoFixture() {
        EvaluationResult result = ArchitectureTest.DOMAIN_SHOULD_NOT_DEPEND_ON_OTHER_LAYERS.evaluate(fixtureClasses);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().toString())
                .contains("ViolatingDomainClass")
                .contains("FixtureInfrastructureClass");
    }

    @Test
    void apiRuleDetectaViolacaoNoFixture() {
        EvaluationResult result = ArchitectureTest.API_SHOULD_NOT_DEPEND_ON_INFRASTRUCTURE.evaluate(fixtureClasses);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().toString())
                .contains("ViolatingApiClass")
                .contains("FixtureInfrastructureClass");
    }

    @Test
    void applicationRuleDetectaViolacaoNoFixture() {
        EvaluationResult result = ArchitectureTest.APPLICATION_SHOULD_NOT_DEPEND_ON_API.evaluate(fixtureClasses);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().toString())
                .contains("ViolatingApplicationClass")
                .contains("FixtureApiClass");
    }

    @Test
    void infrastructureRuleDetectaViolacaoNoFixture() {
        EvaluationResult result =
                ArchitectureTest.INFRASTRUCTURE_SHOULD_NOT_DEPEND_ON_API_OR_APPLICATION.evaluate(fixtureClasses);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().toString())
                .contains("ViolatingInfrastructureClass")
                .contains("FixtureApiClass");
    }
}
