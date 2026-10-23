package com.culturalevents.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Verifica a regra de dependência entre camadas de capability
 * ({@code com.culturalevents.<capability>.{api,application,domain,infrastructure}}):
 * domain não pode depender de api, application ou infrastructure.
 * {@code com.culturalevents.shared} é a exceção — por não casar com o padrão de pacote
 * de nenhuma das camadas, nenhuma regra é aplicada a ele, e qualquer camada pode usá-lo.
 *
 * <p>{@code allowEmptyShould(true)} é necessário porque esta change (platform-foundation)
 * não cria nenhum pacote de capability de negócio concreto ainda — a regra deve valer
 * mesmo sem nenhuma classe casando com o padrão.
 */
@AnalyzeClasses(packages = "com.culturalevents", importOptions = {ImportOption.DoNotIncludeTests.class})
class ArchitectureTest {

    @ArchTest
    public static final ArchRule DOMAIN_SHOULD_NOT_DEPEND_ON_OTHER_LAYERS =
            noClasses()
                    .that().resideInAPackage("com.culturalevents.(*).domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.culturalevents.(*).api..",
                            "com.culturalevents.(*).application..",
                            "com.culturalevents.(*).infrastructure..")
                    .allowEmptyShould(true);
}
