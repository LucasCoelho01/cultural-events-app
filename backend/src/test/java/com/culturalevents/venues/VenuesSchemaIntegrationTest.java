package com.culturalevents.venues;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import com.culturalevents.AbstractPostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** Verifica o schema, as funções e os triggers da migration V004 diretamente por SQL. */
class VenuesSchemaIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final String CHECK_VIOLATION = "23514";

    @Test
    void tabelaEColunasCriadas() {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'venue'",
                String.class);

        assertThat(columns).containsExactlyInAnyOrder("id", "public_id", "city_id", "name", "slug", "address_line",
                "neighborhood", "postal_code", "latitude", "longitude", "website_url", "created_at", "updated_at");
    }

    @Test
    void publicIdEDatasSaoGeradosPeloBanco() {
        insertFixtureVenue(saoPaulo(), "defaults", "Defaults", null);

        var row = jdbcTemplate.queryForMap(
                "SELECT public_id, created_at, updated_at FROM venue WHERE slug = ?", FIXTURE_VENUE_PREFIX + "defaults");

        assertThat(row.get("public_id")).isNotNull();
        assertThat(row.get("created_at")).isNotNull();
        assertThat(row.get("updated_at")).isNotNull();
    }

    @Test
    void cidadeInexistenteERejeitada() {
        assertThat(sqlStateOf(() -> insertVenue(-1L, "Sem Cidade", "teste-sem-cidade", null, null, null, null)))
                .isEqualTo(FOREIGN_KEY_VIOLATION);
    }

    @Test
    void slugDuplicadoNaMesmaCidadeERejeitadoEAceitoEmOutra() {
        insertFixtureCity(21, "RJ", "Cidade Venue", "cidade-venue", true);
        long other = cityId("RJ", "cidade-venue");
        insertFixtureVenue(saoPaulo(), "dup", "Dup", null);

        assertThat(sqlStateOf(() -> insertFixtureVenue(saoPaulo(), "dup", "Dup 2", null))).isEqualTo(UNIQUE_VIOLATION);
        insertFixtureVenue(other, "dup", "Dup em outra cidade", null);
    }

    @Test
    void checksRejeitamCoordenadasECepForaDoFormato() {
        long city = saoPaulo();

        assertThat(sqlStateOf(() -> insertVenue(city, "A", "teste-lat", 90.5, null, null, null))).isEqualTo(CHECK_VIOLATION);
        assertThat(sqlStateOf(() -> insertVenue(city, "B", "teste-lon", null, -180.5, null, null))).isEqualTo(CHECK_VIOLATION);
        assertThat(sqlStateOf(() -> insertVenue(city, "C", "teste-cep", null, null, "01310100", null))).isEqualTo(CHECK_VIOLATION);
    }

    @Test
    void checksAceitamLimitesENulos() {
        long city = saoPaulo();

        insertVenue(city, "L1", "teste-l1", -90.0, -180.0, "01310-100", null);
        insertVenue(city, "L2", "teste-l2", 90.0, 180.0, null, null);
        insertVenue(city, "L3", "teste-l3", null, null, null, null);

        Integer total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM venue WHERE slug IN ('teste-l1','teste-l2','teste-l3')", Integer.class);
        assertThat(total).isEqualTo(3);
    }

    @Test
    void slugifyRemoveAcentosPontuacaoEHifensNasPontas() {
        assertThat(slugify("Teatro Municipal de São Paulo")).isEqualTo("teatro-municipal-de-sao-paulo");
        assertThat(slugify("  --Casa  do   Çafé!!  ")).isEqualTo("casa-do-cafe");
        assertThat(slugify("Rock'n'Roll 2000")).isEqualTo("rock-n-roll-2000");
        assertThat(slugify("!!!")).isEmpty();
    }

    @Test
    void slugGeradoQuandoNulo() {
        jdbcTemplate.update("INSERT INTO venue (city_id, name, slug) VALUES (?, 'Sala São Paulo Teste', NULL)", saoPaulo());
        try {
            String slug = jdbcTemplate.queryForObject(
                    "SELECT slug FROM venue WHERE name = 'Sala São Paulo Teste'", String.class);
            assertThat(slug).isEqualTo("sala-sao-paulo-teste");
        } finally {
            jdbcTemplate.update("DELETE FROM venue WHERE name = 'Sala São Paulo Teste'");
        }
    }

    @Test
    void slugGeradoQuandoVazio() {
        jdbcTemplate.update("INSERT INTO venue (city_id, name, slug) VALUES (?, 'Vazio Teste', '')", saoPaulo());
        try {
            String slug = jdbcTemplate.queryForObject("SELECT slug FROM venue WHERE name = 'Vazio Teste'", String.class);
            assertThat(slug).isEqualTo("vazio-teste");
        } finally {
            jdbcTemplate.update("DELETE FROM venue WHERE name = 'Vazio Teste'");
        }
    }

    @Test
    void slugInformadoEPreservado() {
        insertFixtureVenue(saoPaulo(), "meu-slug", "Nome Qualquer", null);

        String slug = jdbcTemplate.queryForObject(
                "SELECT slug FROM venue WHERE slug = ?", String.class, FIXTURE_VENUE_PREFIX + "meu-slug");
        assertThat(slug).isEqualTo("teste-meu-slug");
    }

    @Test
    void nomeSemAlfanumericosNaoGeraSlug() {
        long city = saoPaulo();

        assertThat(sqlStateOf(() -> jdbcTemplate.update(
                "INSERT INTO venue (city_id, name, slug) VALUES (?, '!!!', NULL)", city))).isEqualTo(CHECK_VIOLATION);
        Integer total = jdbcTemplate.queryForObject("SELECT count(*) FROM venue WHERE name = '!!!'", Integer.class);
        assertThat(total).isZero();
    }

    @Test
    void updatedAtMudaNoUpdateECreatedAtNao() throws Exception {
        insertFixtureVenue(saoPaulo(), "upd", "Upd", null);
        var before = jdbcTemplate.queryForMap(
                "SELECT created_at, updated_at FROM venue WHERE slug = ?", FIXTURE_VENUE_PREFIX + "upd");

        Thread.sleep(20);
        jdbcTemplate.update("UPDATE venue SET neighborhood = 'Centro' WHERE slug = ?", FIXTURE_VENUE_PREFIX + "upd");

        var after = jdbcTemplate.queryForMap(
                "SELECT created_at, updated_at FROM venue WHERE slug = ?", FIXTURE_VENUE_PREFIX + "upd");
        assertThat((Timestamp) after.get("updated_at")).isAfter((Timestamp) before.get("updated_at"));
        assertThat(after.get("created_at")).isEqualTo(before.get("created_at"));
    }

    @Test
    void setUpdatedAtEReutilizavelPorOutraTabela() {
        jdbcTemplate.execute("CREATE TEMP TABLE reuse_probe (id INT, updated_at TIMESTAMPTZ NOT NULL DEFAULT '2000-01-01')");
        jdbcTemplate.execute("CREATE TRIGGER reuse_probe_updated BEFORE UPDATE ON reuse_probe "
                + "FOR EACH ROW EXECUTE FUNCTION set_updated_at()");
        try {
            jdbcTemplate.update("INSERT INTO reuse_probe (id) VALUES (1)");
            jdbcTemplate.update("UPDATE reuse_probe SET id = 2");

            Boolean changed = jdbcTemplate.queryForObject(
                    "SELECT updated_at > '2000-01-01' FROM reuse_probe", Boolean.class);
            assertThat(changed).isTrue();
        } finally {
            jdbcTemplate.execute("DROP TABLE IF EXISTS pg_temp.reuse_probe");
        }
    }

    @Test
    void fixtureDeUmTesteNaoVazaParaOutro() {
        // Qualquer ordem: cada teste insere a mesma fixture e nenhuma pode sobrar do outro.
        Integer leftovers = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM venue WHERE slug = ?", Integer.class, FIXTURE_VENUE_PREFIX + "vazamento");
        insertFixtureVenue(saoPaulo(), "vazamento", "Vazamento", null);

        assertThat(leftovers).isZero();
    }

    @Test
    void fixtureDeUmTesteNaoVazaParaOutroSegundaVia() {
        Integer leftovers = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM venue WHERE slug = ?", Integer.class, FIXTURE_VENUE_PREFIX + "vazamento");
        insertFixtureVenue(saoPaulo(), "vazamento", "Vazamento", null);

        assertThat(leftovers).isZero();
    }

    private long saoPaulo() {
        return cityId("SP", "sao-paulo");
    }

    private String slugify(String value) {
        return jdbcTemplate.queryForObject("SELECT slugify(?)", String.class, value);
    }

    private void insertVenue(long cityId, String name, String slug, Double lat, Double lon, String cep, String site) {
        jdbcTemplate.update(
                "INSERT INTO venue (city_id, name, slug, latitude, longitude, postal_code, website_url) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                cityId, name, slug, lat, lon, cep, site);
    }

    private static String sqlStateOf(Runnable write) {
        DataAccessException failure = catchThrowableOfType(DataAccessException.class, write::run);
        assertThat(failure).as("a escrita deveria ser rejeitada pelo banco").isNotNull();
        Throwable cause = failure;
        while (cause != null && !(cause instanceof SQLException)) {
            cause = cause.getCause();
        }
        assertThat(cause).as("causa SQL").isNotNull();
        return ((SQLException) cause).getSQLState();
    }
}
