package com.culturalevents.ingestion;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataAccessException;

import com.culturalevents.AbstractPostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** Verifica por SQL as tabelas, constraints e triggers da migration V007 (schema de ingestão). */
class IngestionSchemaIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final String NOT_NULL_VIOLATION = "23502";

    private long sp;
    private long shows;
    private long source;

    @BeforeEach
    void fixtures() {
        sp = cityId("SP", "sao-paulo");
        shows = categoryId("shows");
        source = insertFixtureSource("fonte", null);
    }

    @Test
    void fonteSymplaCadastrada() {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT base_url, city_id, active FROM source WHERE code = 'sympla'");
        assertThat(row.get("base_url")).isEqualTo("https://www.sympla.com.br");
        assertThat(row.get("city_id")).isNull();
        assertThat(row.get("active")).isEqualTo(true);
    }

    @Test
    void versaoInicialDoContrato() {
        assertThat(jdbcTemplate.queryForList("SELECT version FROM schema_contract", String.class)).containsExactly("1.0");
        assertThat(jdbcTemplate.queryForObject("SELECT applied_at IS NOT NULL FROM schema_contract", Boolean.class))
                .isTrue();
    }

    @Test
    void colunasDasTabelas() {
        assertThat(columns("source")).containsExactlyInAnyOrder(
                "id", "code", "name", "base_url", "city_id", "active", "created_at", "updated_at");
        assertThat(columns("event_source_link")).containsExactlyInAnyOrder("id", "event_id", "source_id",
                "external_id", "external_url", "content_hash", "last_run_id", "last_seen_at", "created_at", "updated_at");
        assertThat(columns("ingestion_run")).containsExactlyInAnyOrder("id", "source_id", "started_at", "finished_at",
                "status", "created", "updated", "unchanged", "failed", "errors");
        assertThat(columns("schema_contract")).containsExactlyInAnyOrder("version", "applied_at");
    }

    @Test
    void codeDeFonteDuplicadoERejeitado() {
        assertThat(constraint(() -> jdbcTemplate.update(
                "INSERT INTO source (code, name, base_url) VALUES ('sympla', 'Outra', 'https://x.test')")))
                .isEqualTo("source_code_key");
    }

    @Test
    void cidadeDaFonteDeveExistir() {
        assertThat(violation(() -> jdbcTemplate.update(
                "INSERT INTO source (code, name, base_url, city_id) VALUES ('teste-sem-cidade', 'X', 'https://x.test', -1)")))
                .isEqualTo(FOREIGN_KEY_VIOLATION);
    }

    @Test
    void fonteDeUmaCidadeEAceita() {
        long id = insertFixtureSource("local", sp);
        assertThat(jdbcTemplate.queryForObject("SELECT city_id FROM source WHERE id = ?", Long.class, id)).isEqualTo(sp);
    }

    @Test
    void updatedAtDaFonteAvancaPorTrigger() {
        jdbcTemplate.update("UPDATE source SET name = 'Renomeada' WHERE id = ?", source);
        Boolean advanced = jdbcTemplate.queryForObject(
                "SELECT updated_at > created_at FROM source WHERE id = ?", Boolean.class, source);
        assertThat(advanced).isTrue();
    }

    @Test
    void updatedAtDoVinculoAvancaPorTrigger() {
        long event = insertFixtureEvent(sp, shows, null, "link-upd", "Upd");
        link(event, "ext-upd");
        jdbcTemplate.update("UPDATE event_source_link SET content_hash = 'abc' WHERE external_id = ?",
                FIXTURE_SOURCE_PREFIX + "ext-upd");
        Boolean advanced = jdbcTemplate.queryForObject(
                "SELECT updated_at > created_at FROM event_source_link WHERE external_id = ?", Boolean.class,
                FIXTURE_SOURCE_PREFIX + "ext-upd");
        assertThat(advanced).isTrue();
    }

    @Test
    void mesmoItemExternoNaoPodeSerVinculadoDuasVezes() {
        long a = insertFixtureEvent(sp, shows, null, "link-a", "A");
        long b = insertFixtureEvent(sp, shows, null, "link-b", "B");
        link(a, "ext-1");

        assertThat(constraint(() -> link(b, "ext-1"))).isEqualTo("event_source_link_source_external_key");

        long other = insertFixtureSource("outra", null);
        link(other, b, "ext-1");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM event_source_link WHERE external_id = ?", Integer.class,
                FIXTURE_SOURCE_PREFIX + "ext-1")).isEqualTo(2);
    }

    @Test
    void vinculoComEventoInexistenteERejeitado() {
        assertThat(violation(() -> link(-1L, "ext-fk"))).isEqualTo(FOREIGN_KEY_VIOLATION);
    }

    @Test
    void excluirEventoDeixaLapideComEventIdNulo() {
        long event = insertFixtureEvent(sp, shows, null, "lapide", "Lápide");
        link(event, "ext-lapide");

        jdbcTemplate.update("DELETE FROM event WHERE id = ?", event);

        assertThat(jdbcTemplate.queryForList(
                "SELECT event_id FROM event_source_link WHERE source_id = ? AND external_id = ?",
                Long.class, source, FIXTURE_SOURCE_PREFIX + "ext-lapide")).hasSize(1).containsOnlyNulls();
    }

    @Test
    void lapideImpedeNovoVinculoDoMesmoPar() {
        long event = insertFixtureEvent(sp, shows, null, "lapide-2", "Lápide 2");
        link(event, "ext-lapide-2");
        jdbcTemplate.update("DELETE FROM event WHERE id = ?", event);
        long novo = insertFixtureEvent(sp, shows, null, "lapide-3", "Recriado");

        assertThat(constraint(() -> link(novo, "ext-lapide-2"))).isEqualTo("event_source_link_source_external_key");
    }

    @Test
    void excluirExecucaoZeraLastRunIdDoVinculo() {
        long event = insertFixtureEvent(sp, shows, null, "run-link", "Run");
        long run = insertFixtureRun(source);
        jdbcTemplate.update(
                "INSERT INTO event_source_link (event_id, source_id, external_id, last_run_id) VALUES (?, ?, ?, ?)",
                event, source, FIXTURE_SOURCE_PREFIX + "run", run);

        jdbcTemplate.update("DELETE FROM ingestion_run WHERE id = ?", run);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT last_run_id FROM event_source_link WHERE external_id = ?", Long.class,
                FIXTURE_SOURCE_PREFIX + "run")).isNull();
    }

    @Test
    void execucaoTemPadroes() {
        long run = insertFixtureRun(source);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, created, updated, unchanged, failed, errors::text AS errors, started_at, finished_at "
                        + "FROM ingestion_run WHERE id = ?", run);
        assertThat(row.get("status")).isEqualTo("RUNNING");
        assertThat(List.of(row.get("created"), row.get("updated"), row.get("unchanged"), row.get("failed")))
                .containsOnly(0);
        assertThat(row.get("errors")).isEqualTo("[]");
        assertThat(row.get("started_at")).isNotNull();
        assertThat(row.get("finished_at")).isNull();
    }

    @Test
    void execucaoExigeFonteExistente() {
        assertThat(violation(() -> jdbcTemplate.update("INSERT INTO ingestion_run (source_id) VALUES (-1)")))
                .isEqualTo(FOREIGN_KEY_VIOLATION);
    }

    @Test
    void statusDeExecucaoInvalidoERejeitado() {
        long run = insertFixtureRun(source);
        assertThat(constraint(() -> jdbcTemplate.update("UPDATE ingestion_run SET status = 'DONE' WHERE id = ?", run)))
                .isEqualTo("ingestion_run_status_valid");
        for (String valid : List.of("SUCCESS", "PARTIAL", "FAILED", "RUNNING")) {
            jdbcTemplate.update("UPDATE ingestion_run SET status = ? WHERE id = ?", valid, run);
        }
    }

    @Test
    void contadorNegativoERejeitado() {
        long run = insertFixtureRun(source);
        assertThat(constraint(() -> jdbcTemplate.update("UPDATE ingestion_run SET failed = -1 WHERE id = ?", run)))
                .isEqualTo("ingestion_run_counters_nonneg");
    }

    @Test
    void fimAntesDoInicioERejeitado() {
        long run = insertFixtureRun(source);
        assertThat(constraint(() -> jdbcTemplate.update(
                "UPDATE ingestion_run SET finished_at = started_at - interval '1 minute' WHERE id = ?", run)))
                .isEqualTo("ingestion_run_time_order");
    }

    @Test
    void formatoEUnicidadeDaVersaoDoContrato() {
        assertThat(constraint(() -> jdbcTemplate.update("INSERT INTO schema_contract (version) VALUES ('abc')")))
                .isEqualTo("schema_contract_version_format");
        assertThat(constraint(() -> jdbcTemplate.update("INSERT INTO schema_contract (version) VALUES ('1')")))
                .isEqualTo("schema_contract_version_format");
        assertThat(violation(() -> jdbcTemplate.update("INSERT INTO schema_contract (version) VALUES ('1.0')")))
                .isEqualTo(UNIQUE_VIOLATION);
    }

    @Test
    void lockedFieldsComecaVazioENaoAceitaNulo() {
        long event = insertFixtureEvent(sp, shows, null, "locks", "Locks");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT locked_fields = '{}' FROM event WHERE id = ?", Boolean.class, event)).isTrue();
        assertThat(violation(() -> jdbcTemplate.update("UPDATE event SET locked_fields = NULL WHERE id = ?", event)))
                .isEqualTo(NOT_NULL_VIOLATION);
    }

    // --- helpers

    private void link(long eventId, String externalId) {
        link(source, eventId, externalId);
    }

    private void link(long sourceId, long eventId, String externalId) {
        jdbcTemplate.update("INSERT INTO event_source_link (event_id, source_id, external_id) VALUES (?, ?, ?)",
                eventId, sourceId, FIXTURE_SOURCE_PREFIX + externalId);
    }

    private List<String> columns(String table) {
        return jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ?",
                String.class, table);
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
