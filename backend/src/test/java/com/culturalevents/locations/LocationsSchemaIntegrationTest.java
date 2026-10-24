package com.culturalevents.locations;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.culturalevents.AbstractPostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifica o schema e a carga inicial da migration V002 diretamente por SQL. */
class LocationsSchemaIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String INSERT_CITY =
            "INSERT INTO city (ibge_code, state_uf, name, slug, timezone) VALUES (?, ?, ?, ?, 'America/Sao_Paulo')";

    @Test
    void tabelasEColunasCriadas() {
        List<String> stateColumns = columnsOf("state");
        List<String> cityColumns = columnsOf("city");

        assertThat(stateColumns).containsExactlyInAnyOrder("uf", "name");
        assertThat(cityColumns).containsExactlyInAnyOrder(
                "id", "ibge_code", "state_uf", "name", "slug", "timezone", "latitude", "longitude", "active");
    }

    @Test
    void cargaInicialTem27UfsEUmaCidadeAtiva() {
        Integer states = jdbcTemplate.queryForObject("SELECT count(*) FROM state", Integer.class);
        Boolean active = jdbcTemplate.queryForObject(
                "SELECT active FROM city WHERE ibge_code = 3550308", Boolean.class);
        Integer cities = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM city WHERE ibge_code < ?", Integer.class, FIXTURE_IBGE_BASE);

        assertThat(states).isEqualTo(27);
        assertThat(active).isTrue();
        assertThat(cities).isEqualTo(1);
    }

    @Test
    void cidadeComUfInexistenteERejeitada() {
        assertThatThrownBy(() -> jdbcTemplate.update(INSERT_CITY, FIXTURE_IBGE_BASE + 1, "ZZ", "Cidade Teste", "cidade-teste"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void ibgeCodeDuplicadoERejeitado() {
        jdbcTemplate.update(INSERT_CITY, FIXTURE_IBGE_BASE + 2, "RJ", "Cidade A", "cidade-a");

        assertThatThrownBy(() -> jdbcTemplate.update(INSERT_CITY, FIXTURE_IBGE_BASE + 2, "RJ", "Cidade B", "cidade-b"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void slugDuplicadoNaMesmaUfERejeitadoMasEmOutraUfEAceito() {
        jdbcTemplate.update(INSERT_CITY, FIXTURE_IBGE_BASE + 3, "RJ", "Cidade A", "cidade-a");

        assertThatThrownBy(() -> jdbcTemplate.update(INSERT_CITY, FIXTURE_IBGE_BASE + 4, "RJ", "Cidade A2", "cidade-a"))
                .isInstanceOf(DataIntegrityViolationException.class);

        int inserted = jdbcTemplate.update(INSERT_CITY, FIXTURE_IBGE_BASE + 5, "MG", "Cidade A", "cidade-a");
        assertThat(inserted).isEqualTo(1);
    }

    @Test
    void cidadeSemFlagActiveFicaInativa() {
        jdbcTemplate.update(INSERT_CITY, FIXTURE_IBGE_BASE + 6, "RJ", "Cidade A", "cidade-a");

        Boolean active = jdbcTemplate.queryForObject(
                "SELECT active FROM city WHERE ibge_code = ?", Boolean.class, FIXTURE_IBGE_BASE + 6);
        assertThat(active).isFalse();
    }

    @Test
    void fixtureDeUmTesteNaoVazaParaOutro() {
        // O teste anterior (qualquer ordem) insere cidades de fixture; nenhuma pode sobrar.
        Integer leftovers = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM city WHERE ibge_code >= ? AND ibge_code <> ?", Integer.class,
                FIXTURE_IBGE_BASE, FIXTURE_IBGE_BASE + 7);
        jdbcTemplate.update(INSERT_CITY, FIXTURE_IBGE_BASE + 7, "RJ", "Cidade A", "cidade-a");

        assertThat(leftovers).isZero();
    }

    private List<String> columnsOf(String table) {
        return jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ?",
                String.class, table);
    }
}
