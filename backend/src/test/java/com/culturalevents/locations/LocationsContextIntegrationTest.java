package com.culturalevents.locations;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.culturalevents.AbstractPostgresIntegrationTest;
import com.culturalevents.BackendApplication;
import com.culturalevents.locations.domain.CityRepository;
import com.culturalevents.locations.domain.StateRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Garante que as entidades de locations batem com o V002 sob ddl-auto=validate. */
class LocationsContextIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private StateRepository states;

    @Autowired
    private CityRepository cities;

    @Test
    void contextoSobeComSchemaV002EOsAdaptadoresLeemAsTabelas() {
        assertThat(states.findAllOrderedByName()).hasSize(27);
        assertThat(cities.findByUfAndSlug("SP", "sao-paulo")).isPresent();
    }

    @Test
    void contextoFalhaQuandoATabelaDeCidadesNaoExiste() {
        try (PostgreSQLContainer emptyDatabase = new PostgreSQLContainer("postgres:16")) {
            emptyDatabase.start();

            assertThatThrownBy(() -> new SpringApplicationBuilder(BackendApplication.class)
                    .web(WebApplicationType.NONE)
                    .run(
                            "--spring.datasource.url=" + emptyDatabase.getJdbcUrl(),
                            "--spring.datasource.username=" + emptyDatabase.getUsername(),
                            "--spring.datasource.password=" + emptyDatabase.getPassword()))
                    .hasStackTraceContaining("missing table");
        }
    }
}
