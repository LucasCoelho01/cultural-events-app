package com.culturalevents.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova que {@link ArchitectureTest#DOMAIN_SHOULD_NOT_DEPEND_ON_OTHER_LAYERS} de fato
 * detecta uma violação, usando classes de fixture em {@code com.culturalevents.archfixture}
 * (src/test apenas) que violam deliberadamente a regra.
 */
class ArchitectureRuleViolationTest {

    @Test
    void regraDetectaViolacaoNoFixture() {
        JavaClasses fixtureClasses = new ClassFileImporter()
                .importPackages("com.culturalevents.archfixture");

        EvaluationResult result = ArchitectureTest.DOMAIN_SHOULD_NOT_DEPEND_ON_OTHER_LAYERS.evaluate(fixtureClasses);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().toString())
                .contains("ViolatingDomainClass")
                .contains("FixtureInfrastructureClass");
    }
}
