package com.culturalevents.events;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataAccessException;

import com.culturalevents.AbstractPostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** Verifica o schema, as constraints nomeadas e os triggers da migration V005 diretamente por SQL. */
class EventsSchemaIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final String CHECK_VIOLATION = "23514";
    private static final String RESTRICT_VIOLATION = "23001";

    private long sp;
    private long rj;
    private long shows;

    @BeforeEach
    void cities() {
        insertFixtureCity(51, "RJ", "Cidade Evento", "cidade-evento", true);
        sp = cityId("SP", "sao-paulo");
        rj = cityId("RJ", "cidade-evento");
        shows = categoryId("shows");
    }

    @Test
    void tabelasEColunasCriadas() {
        assertThat(columns("event")).containsExactlyInAnyOrder("id", "public_id", "city_id", "title", "slug",
                "description", "category_id", "venue_id", "status", "age_rating", "price_min_cents", "price_max_cents",
                "is_free", "ticket_url", "image_url", "origin", "created_at", "updated_at", "version", "search_vector", "locked_fields");
        assertThat(columns("occurrence")).containsExactlyInAnyOrder("id", "event_id", "starts_at", "ends_at", "status");
    }

    @Test
    void padroesDoEvento() {
        insertFixtureEvent(sp, shows, null, "padroes", "Padrões");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, origin, version, is_free, public_id, created_at, updated_at FROM event WHERE slug = ?",
                FIXTURE_EVENT_PREFIX + "padroes");
        assertThat(row.get("status")).isEqualTo("DRAFT");
        assertThat(row.get("origin")).isEqualTo("MANUAL");
        assertThat(row.get("version")).isEqualTo(0);
        assertThat(row.get("is_free")).isEqualTo(false);
        assertThat(row.get("public_id")).isNotNull();
        assertThat(row.get("created_at")).isNotNull();
        assertThat(row.get("updated_at")).isNotNull();
    }

    @Test
    void padraoDaSessaoESCHEDULED() {
        long event = insertFixtureEvent(sp, shows, null, "sessao-padrao", "Sessão");
        insertFixtureOccurrence(event, "2030-01-01T20:00:00Z");

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM occurrence WHERE event_id = ?", String.class, event);
        assertThat(status).isEqualTo("SCHEDULED");
    }

    @Test
    void referenciasInexistentesSaoRejeitadas() {
        assertThat(violation(() -> rawInsert(-1L, shows, null, "t-city", "T", "DRAFT"))).isEqualTo(FOREIGN_KEY_VIOLATION);
        assertThat(violation(() -> rawInsert(sp, -1L, null, "t-cat", "T", "DRAFT"))).isEqualTo(FOREIGN_KEY_VIOLATION);
        assertThat(violation(() -> rawInsert(sp, shows, -1L, "t-venue", "T", "DRAFT"))).isEqualTo(FOREIGN_KEY_VIOLATION);
    }

    @Test
    void slugDuplicadoNaCidadeERejeitadoEAceitoEmOutra() {
        insertFixtureEvent(sp, shows, null, "dup", "Dup");

        assertThat(violation(() -> insertFixtureEvent(sp, shows, null, "dup", "Dup 2"))).isEqualTo(UNIQUE_VIOLATION);
        assertThat(constraint(() -> insertFixtureEvent(sp, shows, null, "dup", "Dup 2")))
                .isEqualTo("event_city_slug_key");
        insertFixtureEvent(rj, shows, null, "dup", "Dup em outra cidade");
    }

    @Test
    void sessaoDuplicadaNoEventoERejeitada() {
        long event = insertFixtureEvent(sp, shows, null, "sessao-dup", "Sessão dup");
        insertFixtureOccurrence(event, "2030-01-01T20:00:00Z");

        assertThat(constraint(() -> insertFixtureOccurrence(event, "2030-01-01T20:00:00Z")))
                .isEqualTo("occurrence_event_starts_at_key");
        insertFixtureOccurrence(event, "2030-01-02T20:00:00Z");
    }

    @Test
    void checksDoEventoInformamOsNomesDasConstraints() {
        assertThat(constraint(() -> rawInsert(sp, shows, null, "teste-s", "S", "FOO"))).isEqualTo("event_status_valid");
        assertThat(constraint(() -> jdbcTemplate.update(
                "INSERT INTO event (city_id, category_id, title, slug, origin) VALUES (?, ?, 'O', 'teste-o', 'BOT')",
                sp, shows))).isEqualTo("event_origin_valid");
        assertThat(constraint(() -> insertWithPrices("teste-min", -1, null, false))).isEqualTo("event_price_min_nonneg");
        assertThat(constraint(() -> insertWithPrices("teste-rng", 1000, 500, false))).isEqualTo("event_price_range");
        assertThat(violation(() -> insertWithPrices("teste-rng2", 1000, 500, false))).isEqualTo(CHECK_VIOLATION);
    }

    @Test
    void eventoGratuitoNaoTemPrecoPositivo() {
        assertThat(constraint(() -> insertWithPrices("teste-free-min", 100, null, true))).isEqualTo("event_free_no_price");
        assertThat(constraint(() -> insertWithPrices("teste-free-max", null, 100, true))).isEqualTo("event_free_no_price");

        insertWithPrices("teste-free-nulo", null, null, true);
        insertWithPrices("teste-free-zero", 0, 0, true);
        insertWithPrices("teste-pago", 1000, 2000, false);
    }

    @Test
    void atualizarParaGratuitoComPrecoPositivoERejeitado() {
        insertWithPrices("teste-vira-free", 1000, 2000, false);

        assertThat(constraint(() -> jdbcTemplate.update("UPDATE event SET is_free = true WHERE slug = 'teste-vira-free'")))
                .isEqualTo("event_free_no_price");
    }

    @Test
    void checksDaSessao() {
        long event = insertFixtureEvent(sp, shows, null, "checks-sessao", "Checks");

        assertThat(constraint(() -> jdbcTemplate.update(
                "INSERT INTO occurrence (event_id, starts_at, ends_at) VALUES (?, '2030-01-01T20:00:00Z', '2030-01-01T19:00:00Z')",
                event))).isEqualTo("occurrence_time_order");
        assertThat(constraint(() -> jdbcTemplate.update(
                "INSERT INTO occurrence (event_id, starts_at, status) VALUES (?, '2030-01-01T20:00:00Z', 'FOO')",
                event))).isEqualTo("occurrence_status_valid");

        jdbcTemplate.update("INSERT INTO occurrence (event_id, starts_at) VALUES (?, '2030-01-02T20:00:00Z')", event);
        jdbcTemplate.update(
                "INSERT INTO occurrence (event_id, starts_at, ends_at) VALUES (?, '2030-01-03T20:00:00Z', '2030-01-03T20:00:00Z')",
                event);
    }

    @Test
    void slugGeradoDoTitulo() {
        jdbcTemplate.update(
                "INSERT INTO event (city_id, category_id, title, slug) VALUES (?, ?, 'Festival de Jazz de São Paulo (Teste)', NULL)",
                sp, shows);
        try {
            String slug = jdbcTemplate.queryForObject(
                    "SELECT slug FROM event WHERE title = 'Festival de Jazz de São Paulo (Teste)'", String.class);
            assertThat(slug).isEqualTo("festival-de-jazz-de-sao-paulo-teste");
        } finally {
            jdbcTemplate.update("DELETE FROM event WHERE title = 'Festival de Jazz de São Paulo (Teste)'");
        }
    }

    @Test
    void tituloSemAlfanumericosNaoGeraSlug() {
        assertThat(constraint(() -> jdbcTemplate.update(
                "INSERT INTO event (city_id, category_id, title, slug) VALUES (?, ?, '!!!', NULL)", sp, shows)))
                .isEqualTo("event_slug_not_empty");
    }

    @Test
    void updatedAtMudaNoUpdateECreatedAtNao() throws Exception {
        insertFixtureEvent(sp, shows, null, "upd", "Upd");
        Map<String, Object> before = jdbcTemplate.queryForMap(
                "SELECT created_at, updated_at FROM event WHERE slug = ?", FIXTURE_EVENT_PREFIX + "upd");

        Thread.sleep(20);
        jdbcTemplate.update("UPDATE event SET description = 'x' WHERE slug = ?", FIXTURE_EVENT_PREFIX + "upd");

        Map<String, Object> after = jdbcTemplate.queryForMap(
                "SELECT created_at, updated_at FROM event WHERE slug = ?", FIXTURE_EVENT_PREFIX + "upd");
        assertThat((Timestamp) after.get("updated_at")).isAfter((Timestamp) before.get("updated_at"));
        assertThat(after.get("created_at")).isEqualTo(before.get("created_at"));
    }

    @Test
    void localDeOutraCidadeERejeitadoNoInsertEAceitoNaMesma() {
        long venueRj = venue(rj, "local-rj");
        long venueSp = venue(sp, "local-sp");

        assertThat(constraint(() -> insertFixtureEvent(sp, shows, venueRj, "ev-outra", "Outra")))
                .isEqualTo("event_venue_city_match");
        assertThat(violation(() -> insertFixtureEvent(sp, shows, venueRj, "ev-outra", "Outra"))).isEqualTo(CHECK_VIOLATION);
        insertFixtureEvent(sp, shows, venueSp, "ev-mesma", "Mesma");
        insertFixtureEvent(sp, shows, null, "ev-sem-local", "Sem local");
    }

    @Test
    void trocarCidadeOuLocalDoEventoRevalidaACidadeDoLocal() {
        long venueSp = venue(sp, "local-sp-upd");
        long venueRj = venue(rj, "local-rj-upd");
        insertFixtureEvent(sp, shows, venueSp, "ev-upd", "Upd local");

        assertThat(constraint(() -> jdbcTemplate.update(
                "UPDATE event SET venue_id = ? WHERE slug = ?", venueRj, FIXTURE_EVENT_PREFIX + "ev-upd")))
                .isEqualTo("event_venue_city_match");
        assertThat(constraint(() -> jdbcTemplate.update(
                "UPDATE event SET city_id = ? WHERE slug = ?", rj, FIXTURE_EVENT_PREFIX + "ev-upd")))
                .isEqualTo("event_venue_city_match");
    }

    @Test
    void localComEventosNaoMudaDeCidade() {
        long venueSp = venue(sp, "local-guarda");
        insertFixtureEvent(sp, shows, venueSp, "ev-guarda", "Guarda");

        assertThat(violation(() -> jdbcTemplate.update("UPDATE venue SET city_id = ? WHERE id = ?", rj, venueSp)))
                .isEqualTo(RESTRICT_VIOLATION);
        assertThat(constraint(() -> jdbcTemplate.update("UPDATE venue SET city_id = ? WHERE id = ?", rj, venueSp)))
                .isEqualTo("venue_city_in_use");
        assertThat(cityOfVenue(venueSp)).isEqualTo(sp);
    }

    @Test
    void localSemEventosMudaDeCidadeEOutrosCamposNaoSaoBloqueados() {
        long venueSp = venue(sp, "local-livre");
        insertFixtureEvent(sp, shows, venueSp, "ev-livre", "Livre");

        jdbcTemplate.update("UPDATE venue SET neighborhood = 'Centro' WHERE id = ?", venueSp);
        jdbcTemplate.update("UPDATE venue SET city_id = city_id WHERE id = ?", venueSp);
        long semEventos = venue(sp, "local-sem-eventos");
        jdbcTemplate.update("UPDATE venue SET city_id = ? WHERE id = ?", rj, semEventos);
        assertThat(cityOfVenue(semEventos)).isEqualTo(rj);
    }

    @Test
    void removerLocalReferenciadoPorEventoERejeitado() {
        long venueSp = venue(sp, "local-fk");
        insertFixtureEvent(sp, shows, venueSp, "ev-fk", "FK");

        assertThat(violation(() -> jdbcTemplate.update("DELETE FROM venue WHERE id = ?", venueSp)))
                .isEqualTo(FOREIGN_KEY_VIOLATION);
        assertThat(constraint(() -> jdbcTemplate.update("DELETE FROM venue WHERE id = ?", venueSp)))
                .isEqualTo("event_venue_id_fkey");
    }

    @Test
    void trigerDeSlugDeVenueInformaOnomeDaConstraint() {
        assertThat(constraint(() -> jdbcTemplate.update(
                "INSERT INTO venue (city_id, name, slug) VALUES (?, '!!!', NULL)", sp)))
                .isEqualTo("venue_slug_not_empty");
    }

    @Test
    void removerEventoApagaSuasSessoesEPoupaAsDeOutros() {
        long a = insertFixtureEvent(sp, shows, null, "cascata-a", "A");
        long b = insertFixtureEvent(sp, shows, null, "cascata-b", "B");
        insertFixtureOccurrence(a, "2030-01-01T20:00:00Z");
        insertFixtureOccurrence(a, "2030-01-02T20:00:00Z");
        insertFixtureOccurrence(b, "2030-01-01T20:00:00Z");

        jdbcTemplate.update("DELETE FROM event WHERE id = ?", a);

        assertThat(count("SELECT count(*) FROM occurrence WHERE event_id = ?", a)).isZero();
        assertThat(count("SELECT count(*) FROM occurrence WHERE event_id = ?", b)).isEqualTo(1);
    }

    @Test
    void fixtureDeUmTesteNaoVazaParaOutro() {
        Integer leftovers = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM event WHERE slug = ?", Integer.class, FIXTURE_EVENT_PREFIX + "vazamento");
        insertFixtureEvent(sp, shows, null, "vazamento", "Vazamento");

        assertThat(leftovers).isZero();
    }

    @Test
    void fixtureDeUmTesteNaoVazaParaOutroSegundaVia() {
        Integer leftovers = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM event WHERE slug = ?", Integer.class, FIXTURE_EVENT_PREFIX + "vazamento");
        insertFixtureEvent(sp, shows, null, "vazamento", "Vazamento");

        assertThat(leftovers).isZero();
    }

    // --- helpers

    private List<String> columns(String table) {
        return jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ?",
                String.class, table);
    }

    private long venue(long cityId, String slug) {
        insertFixtureVenue(cityId, slug, "Local " + slug, null);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM venue WHERE slug = ?", Long.class, FIXTURE_VENUE_PREFIX + slug);
    }

    private long cityOfVenue(long venueId) {
        return jdbcTemplate.queryForObject("SELECT city_id FROM venue WHERE id = ?", Long.class, venueId);
    }

    private int count(String sql, long id) {
        return jdbcTemplate.queryForObject(sql, Integer.class, id);
    }

    private void rawInsert(Long cityId, Long categoryId, Long venueId, String slug, String title, String status) {
        jdbcTemplate.update(
                "INSERT INTO event (city_id, category_id, venue_id, title, slug, status) VALUES (?, ?, ?, ?, ?, ?)",
                cityId, categoryId, venueId, title, FIXTURE_EVENT_PREFIX + slug.replaceFirst("^teste-", ""), status);
    }

    private void insertWithPrices(String slug, Integer min, Integer max, boolean free) {
        jdbcTemplate.update(
                "INSERT INTO event (city_id, category_id, title, slug, price_min_cents, price_max_cents, is_free) "
                        + "VALUES (?, ?, 'Preço', ?, ?, ?, ?)", sp, shows, slug, min, max, free);
    }

    private static PSQLException psql(Runnable write) {
        DataAccessException failure = catchThrowableOfType(DataAccessException.class, write::run);
        assertThat(failure).as("a escrita deveria ser rejeitada pelo banco").isNotNull();
        Throwable cause = failure;
        while (cause != null && !(cause instanceof PSQLException)) {
            cause = cause.getCause();
        }
        assertThat(cause).as("causa SQL").isNotNull();
        return (PSQLException) cause;
    }

    private static String violation(Runnable write) {
        return psql(write).getSQLState();
    }

    private static String constraint(Runnable write) {
        return psql(write).getServerErrorMessage().getConstraint();
    }
}
