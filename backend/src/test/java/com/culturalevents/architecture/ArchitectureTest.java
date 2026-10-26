package com.culturalevents.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Verifica a regra de dependência entre camadas de capability
 * ({@code com.culturalevents.<capability>.{api,application,domain,infrastructure}}):
 * api -&gt; application -&gt; domain &lt;- infrastructure. Cada camada só pode depender das
 * camadas "abaixo" dela nessa cadeia; nenhuma dependência "de volta" é permitida.
 * {@code com.culturalevents.shared} é a exceção — por não casar com o padrão de pacote
 * de nenhuma das camadas, nenhuma regra é aplicada a ele, e qualquer camada pode usá-lo.
 *
 * <p>{@code allowEmptyShould(true)} é necessário porque esta change (platform-foundation)
 * não cria nenhum pacote de capability de negócio concreto ainda — cada regra deve valer
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

    @ArchTest
    public static final ArchRule API_SHOULD_NOT_DEPEND_ON_INFRASTRUCTURE =
            noClasses()
                    .that().resideInAPackage("com.culturalevents.(*).api..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.culturalevents.(*).infrastructure..")
                    .allowEmptyShould(true);

    @ArchTest
    public static final ArchRule APPLICATION_SHOULD_NOT_DEPEND_ON_API =
            noClasses()
                    .that().resideInAPackage("com.culturalevents.(*).application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.culturalevents.(*).api..")
                    .allowEmptyShould(true);

    @ArchTest
    public static final ArchRule INFRASTRUCTURE_SHOULD_NOT_DEPEND_ON_API_OR_APPLICATION =
            noClasses()
                    .that().resideInAPackage("com.culturalevents.(*).infrastructure..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.culturalevents.(*).api..",
                            "com.culturalevents.(*).application..")
                    .allowEmptyShould(true);

    /** venues referencia a cidade só por id e acessa locations apenas pelo caso de uso em application. */
    @ArchTest
    public static final ArchRule VENUES_SHOULD_NOT_DEPEND_ON_LOCATIONS_INTERNALS =
            noClasses()
                    .that().resideInAPackage("..venues..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.culturalevents.locations.domain..",
                            "com.culturalevents.locations.api..",
                            "com.culturalevents.locations.infrastructure..")
                    .allowEmptyShould(true);

    /** events acessa locations, categories e venues apenas pelos casos de uso da camada application. */
    @ArchTest
    public static final ArchRule EVENTS_SHOULD_NOT_DEPEND_ON_OTHER_MODULES_INTERNALS =
            noClasses()
                    .that().resideInAPackage("..events..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.culturalevents.locations.domain..",
                            "com.culturalevents.locations.api..",
                            "com.culturalevents.locations.infrastructure..",
                            "com.culturalevents.categories.domain..",
                            "com.culturalevents.categories.api..",
                            "com.culturalevents.categories.infrastructure..",
                            "com.culturalevents.venues.domain..",
                            "com.culturalevents.venues.api..",
                            "com.culturalevents.venues.infrastructure..")
                    .allowEmptyShould(true);
}
