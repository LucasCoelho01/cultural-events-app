package com.culturalevents.ingestion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.culturalevents.AbstractPostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Prova os privilégios da V008. A role não tem senha, então a conexão é a do usuário do container com
 * {@code SET ROLE agent_writer}, que aplica exatamente os privilégios da role. Os SQLs do fluxo de upsert são os
 * mesmos documentados em {@code database/AGENT_CONTRACT.md}.
 */
class AgentWriterRoleIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String INSUFFICIENT_PRIVILEGE = "42501";
    private static final Path MIGRATIONS_DIR = Path.of("..", "database", "migrations");

    private Connection agent;
    private long sp;
    private long shows;
    private long source;

    @BeforeEach
    void connectAsAgent() throws SQLException {
        sp = cityId("SP", "sao-paulo");
        shows = categoryId("shows");
        source = insertFixtureSource("agente", null);

        agent = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        agent.setAutoCommit(true);
        try (Statement statement = agent.createStatement()) {
            statement.execute("SET ROLE agent_writer");
        }
    }

    @AfterEach
    void disconnect() throws SQLException {
        agent.close();
    }

    @Test
    void roleTemLoginESemSenha() {
        Boolean canLogin = jdbcTemplate.queryForObject(
                "SELECT rolcanlogin FROM pg_roles WHERE rolname = 'agent_writer'", Boolean.class);
        String password = jdbcTemplate.queryForObject(
                "SELECT rolpassword FROM pg_authid WHERE rolname = 'agent_writer'", String.class);
        Boolean superuser = jdbcTemplate.queryForObject(
                "SELECT rolsuper OR rolcreatedb OR rolcreaterole FROM pg_roles WHERE rolname = 'agent_writer'",
                Boolean.class);

        assertThat(canLogin).isTrue();
        assertThat(password).isNull();
        assertThat(superuser).isFalse();
    }

    @Test
    void nenhumScriptVersionadoDefineSenha() throws IOException {
        try (Stream<Path> files = Files.list(MIGRATIONS_DIR)) {
            List<Path> scripts = files.filter(path -> path.toString().endsWith(".sql")).toList();
            for (Path script : scripts) {
                assertThat(Files.readString(script).replaceAll("(?m)--.*$", "").toUpperCase())
                        .as("%s não deve definir senha (fora de comentários)", script.getFileName())
                        .doesNotContain("PASSWORD");
            }
        }
    }

    @Test
    void scriptDaRoleRodaDeNovoSemErro() throws IOException, SQLException {
        String script = Files.readString(MIGRATIONS_DIR.resolve("V008__create_agent_writer_role.sql"));
        try (Connection owner = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = owner.createStatement()) {
            statement.execute(script);
        }
        // As permissões continuam as mesmas depois de reaplicar.
        assertThat(sqlState("DELETE FROM category WHERE slug = 'inexistente'")).isEqualTo(INSUFFICIENT_PRIVILEGE);
    }

    @Test
    void leTodasAsTabelas() throws SQLException {
        for (String table : List.of("category", "city", "state", "schema_contract", "event", "occurrence", "venue",
                "source", "event_source_link", "ingestion_run")) {
            try (Statement statement = agent.createStatement();
                    ResultSet rows = statement.executeQuery("SELECT count(*) FROM " + table)) {
                assertThat(rows.next()).as(table).isTrue();
            }
        }
    }

    @Test
    void naoEscreveEmTabelasDeReferencia() {
        assertThat(sqlState("INSERT INTO category (name, slug, sort_order) VALUES ('X', 'teste-x', 1)"))
                .isEqualTo(INSUFFICIENT_PRIVILEGE);
        assertThat(sqlState("UPDATE category SET name = 'X'")).isEqualTo(INSUFFICIENT_PRIVILEGE);
        assertThat(sqlState("DELETE FROM category")).isEqualTo(INSUFFICIENT_PRIVILEGE);

        assertThat(sqlState("INSERT INTO city (ibge_code, state_uf, name, slug, timezone) "
                + "VALUES (9999999, 'SP', 'X', 'x', 'America/Sao_Paulo')")).isEqualTo(INSUFFICIENT_PRIVILEGE);
        assertThat(sqlState("UPDATE city SET active = true")).isEqualTo(INSUFFICIENT_PRIVILEGE);
        assertThat(sqlState("DELETE FROM city")).isEqualTo(INSUFFICIENT_PRIVILEGE);

        assertThat(sqlState("UPDATE state SET name = 'X'")).isEqualTo(INSUFFICIENT_PRIVILEGE);
        assertThat(sqlState("INSERT INTO schema_contract (version) VALUES ('9.9')")).isEqualTo(INSUFFICIENT_PRIVILEGE);
        assertThat(sqlState("UPDATE schema_contract SET version = '9.9'")).isEqualTo(INSUFFICIENT_PRIVILEGE);
        assertThat(sqlState("DELETE FROM schema_contract")).isEqualTo(INSUFFICIENT_PRIVILEGE);
    }

    @Test
    void naoFazDdl() {
        assertThat(sqlState("CREATE TABLE agente_nao_cria (id int)")).isEqualTo(INSUFFICIENT_PRIVILEGE);
        assertThat(sqlState("ALTER TABLE event ADD COLUMN agente_nao_altera int")).isEqualTo(INSUFFICIENT_PRIVILEGE);
        assertThat(sqlState("DROP TABLE event")).isEqualTo(INSUFFICIENT_PRIVILEGE);
        assertThat(sqlState("TRUNCATE event CASCADE")).isEqualTo(INSUFFICIENT_PRIVILEGE);
        assertThat(sqlState("CREATE INDEX agente_nao_indexa ON event (title)")).isEqualTo(INSUFFICIENT_PRIVILEGE);
    }

    @Test
    void fluxoCompletoDeUpsertDeUmEventoComSessoesEVinculo() throws SQLException {
        long run = scalar("INSERT INTO ingestion_run (source_id) VALUES (" + source + ") RETURNING id");

        // --- criação (uma transação por evento)
        agent.setAutoCommit(false);
        long venue = scalar("INSERT INTO venue (city_id, name, slug) VALUES (" + sp
                + ", 'Casa do Agente', 'teste-casa-agente') RETURNING id");
        long event = scalar("INSERT INTO event (city_id, category_id, venue_id, title, slug, status, origin, "
                + "price_min_cents, price_max_cents) VALUES (" + sp + ", " + shows + ", " + venue
                + ", 'Show do Agente', 'teste-show-agente', 'PUBLISHED', 'AGENT', 5000, 12000) RETURNING id");
        exec("INSERT INTO occurrence (event_id, starts_at, ends_at) VALUES (" + event
                + ", '2031-05-01T20:00:00-03', '2031-05-01T22:00:00-03')");
        exec("INSERT INTO occurrence (event_id, starts_at) VALUES (" + event + ", '2031-05-02T20:00:00-03')");
        exec("INSERT INTO event_source_link (event_id, source_id, external_id, external_url, content_hash, last_run_id) "
                + "VALUES (" + event + ", " + source + ", 'teste-ext-agente', 'https://exemplo.test/e/1', 'hash-1', " + run + ")");
        agent.commit();

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM occurrence WHERE event_id = ?", Integer.class, event))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM event WHERE id = ?", Integer.class, event)).isZero();

        // --- reconhecimento idempotente pelo vínculo, com lock da linha
        agent.setAutoCommit(false);
        long found = scalar("SELECT event_id FROM event_source_link WHERE source_id = " + source
                + " AND external_id = 'teste-ext-agente' FOR UPDATE");
        assertThat(found).isEqualTo(event);

        // --- atualização com controle de versão e verificação de locked_fields
        int updated = exec("UPDATE event SET title = 'Show do Agente 2', description = 'Nova', version = version + 1 "
                + "WHERE id = " + event + " AND version = 0");
        assertThat(updated).isEqualTo(1);
        int stale = exec("UPDATE event SET title = 'Outro', version = version + 1 WHERE id = " + event + " AND version = 0");
        assertThat(stale).as("versão desatualizada não atualiza nenhuma linha").isZero();
        exec("DELETE FROM occurrence WHERE event_id = " + event);
        exec("INSERT INTO occurrence (event_id, starts_at) VALUES (" + event + ", '2031-06-01T20:00:00-03')");
        exec("UPDATE event_source_link SET content_hash = 'hash-2', last_seen_at = now(), last_run_id = " + run
                + " WHERE source_id = " + source + " AND external_id = 'teste-ext-agente'");
        agent.commit();

        // --- fechamento da execução
        agent.setAutoCommit(true);
        exec("UPDATE ingestion_run SET status = 'SUCCESS', finished_at = now(), created = 1, updated = 1 WHERE id = " + run);

        assertThat(jdbcTemplate.queryForObject("SELECT title || '/' || version FROM event WHERE id = ?", String.class, event))
                .isEqualTo("Show do Agente 2/1");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM occurrence WHERE event_id = ?", Integer.class, event))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM ingestion_run WHERE id = ?", String.class, run)).isEqualTo("SUCCESS");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT content_hash FROM event_source_link WHERE event_id = ?", String.class, event)).isEqualTo("hash-2");
    }

    @Test
    void agenteDetectaLapideEDeduplicaComSimilarity() throws SQLException {
        long event = insertFixtureEvent(sp, shows, null, "lapide-agente", "Festival de Jazz de São Paulo");
        jdbcTemplate.update("INSERT INTO event_source_link (event_id, source_id, external_id) VALUES (?, ?, ?)",
                event, source, FIXTURE_SOURCE_PREFIX + "ext-lapide-agente");
        jdbcTemplate.update("DELETE FROM event WHERE id = ?", event);

        try (Statement statement = agent.createStatement();
                ResultSet rows = statement.executeQuery("SELECT event_id FROM event_source_link WHERE source_id = "
                        + source + " AND external_id = 'teste-ext-lapide-agente'")) {
            assertThat(rows.next()).as("o vínculo existe").isTrue();
            assertThat(rows.getObject(1)).as("lápide: event_id nulo").isNull();
        }

        long other = insertFixtureEvent(sp, shows, null, "similar-agente", "Festival de Jazz São Paulo");
        insertFixtureOccurrence(other, "2031-07-10T20:00:00Z");
        try (PreparedStatement statement = agent.prepareStatement(
                "SELECT e.id FROM event e WHERE e.city_id = ? AND similarity(e.title, ?) >= 0.6 "
                        + "AND EXISTS (SELECT 1 FROM occurrence o WHERE o.event_id = e.id "
                        + "AND o.starts_at BETWEEN ?::timestamptz - interval '1 day' AND ?::timestamptz + interval '1 day')")) {
            statement.setLong(1, sp);
            statement.setString(2, "Festival de Jazz de São Paulo");
            statement.setString(3, "2031-07-10T20:00:00Z");
            statement.setString(4, "2031-07-10T20:00:00Z");
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isEqualTo(other);
            }
        }
    }

    @Test
    void excluirEventoComoAgenteDeixaLapide() throws SQLException {
        long event = insertFixtureEvent(sp, shows, null, "del-agente", "Del");
        jdbcTemplate.update("INSERT INTO event_source_link (event_id, source_id, external_id) VALUES (?, ?, ?)",
                event, source, FIXTURE_SOURCE_PREFIX + "ext-del");

        assertThat(exec("DELETE FROM event WHERE id = " + event)).isEqualTo(1);

        assertThat(jdbcTemplate.queryForList("SELECT event_id FROM event_source_link WHERE external_id = ?", Long.class,
                FIXTURE_SOURCE_PREFIX + "ext-del")).hasSize(1).containsOnlyNulls();
    }

    @Test
    void sqlsDoAgentContractFuncionam() throws SQLException {
        // Versão do contrato (seção 2 do documento): a maior por ordem numérica.
        assertThat(stringScalar("SELECT version FROM schema_contract "
                + "ORDER BY string_to_array(version, '.')::int[] DESC LIMIT 1")).isEqualTo("1.0");

        // Criação (seção 5): slug nulo gerado pelo banco; local reaproveitado pelo ON CONFLICT.
        String venueSql = "INSERT INTO venue (city_id, name, slug) VALUES (" + sp
                + ", 'Teste Local Doc', NULL) ON CONFLICT (city_id, slug) DO UPDATE SET name = venue.name RETURNING id";
        long venue = scalar(venueSql);
        assertThat(scalar(venueSql)).as("segundo upsert reaproveita o local").isEqualTo(venue);

        long event = scalar("INSERT INTO event (city_id, category_id, venue_id, title, slug, description, status, origin) "
                + "VALUES (" + sp + ", " + shows + ", " + venue
                + ", 'Teste Evento Doc', NULL, 'd', 'PUBLISHED', 'AGENT') RETURNING id");
        assertThat(stringScalar("SELECT slug FROM event WHERE id = " + event)).isEqualTo("teste-evento-doc");

        // Sessões (seção 7): upsert por (event_id, starts_at) e remoção das ausentes.
        String upsert = "INSERT INTO occurrence (event_id, starts_at, ends_at, status) VALUES (" + event
                + ", '2031-08-01T20:00:00Z', %s, 'SCHEDULED') ON CONFLICT (event_id, starts_at) "
                + "DO UPDATE SET ends_at = EXCLUDED.ends_at, status = EXCLUDED.status";
        exec(upsert.formatted("NULL"));
        exec(upsert.formatted("'2031-08-01T22:00:00Z'"));
        exec("INSERT INTO occurrence (event_id, starts_at) VALUES (" + event + ", '2031-08-02T20:00:00Z')");
        assertThat(exec("DELETE FROM occurrence WHERE event_id = " + event
                + " AND starts_at <> ALL(ARRAY['2031-08-01T20:00:00Z']::timestamptz[])")).isEqualTo(1);
        assertThat(scalar("SELECT count(*) FROM occurrence WHERE event_id = " + event)).isEqualTo(1);

        // Leitura de version e locked_fields antes de atualizar (seção 7), com FOR UPDATE.
        agent.setAutoCommit(false);
        assertThat(stringScalar("SELECT version || '/' || cardinality(locked_fields) FROM event WHERE id = " + event
                + " FOR UPDATE")).isEqualTo("0/0");
        agent.commit();
    }

    // --- helpers

    private String sqlState(String sql) {
        SQLException failure = catchThrowableOfType(SQLException.class, () -> {
            try (Statement statement = agent.createStatement()) {
                statement.execute(sql);
            }
        });
        assertThat(failure != null).as("%s deveria ser negado", sql).isTrue();
        return failure.getSQLState();
    }

    private int exec(String sql) throws SQLException {
        try (Statement statement = agent.createStatement()) {
            return statement.executeUpdate(sql);
        }
    }

    private String stringScalar(String sql) throws SQLException {
        try (Statement statement = agent.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertThat(rows.next()).isTrue();
            return rows.getString(1);
        }
    }

    private long scalar(String sql) throws SQLException {
        try (Statement statement = agent.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertThat(rows.next()).isTrue();
            return rows.getLong(1);
        }
    }
}
