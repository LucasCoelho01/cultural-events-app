package com.culturalevents;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base dos testes de integração: um único container Postgres por JVM (singleton estático,
 * iniciado uma vez), com {@code database/migrations} aplicadas uma única vez. O container é
 * encerrado pelo Ryuk ao fim da execução; por isso não usa {@code @Container}, que o pararia
 * ao fim de cada classe.
 *
 * <p>Como o banco é compartilhado, dados de fixture inseridos por um teste devem ser
 * removidos ao final dele ({@code @AfterEach} ou {@code @Sql} pós-teste), para que nenhum
 * teste dependa de ordem nem de dados deixados por outro. Os dados da carga inicial das
 * migrations não são fixture e nunca devem ser removidos.
 *
 * <p>Cidades de fixture usam {@code ibge_code >= FIXTURE_IBGE_BASE} (faixa que não colide com
 * códigos IBGE reais) e categorias de fixture usam slug com prefixo {@link #FIXTURE_CATEGORY_PREFIX};
 * ambas são removidas automaticamente após cada teste.
 */
@SpringBootTest
public abstract class AbstractPostgresIntegrationTest {

    private static final Path MIGRATIONS_DIR = Path.of("..", "database", "migrations");

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    /** Primeiro código IBGE reservado a cidades de fixture de teste. */
    protected static final int FIXTURE_IBGE_BASE = 9_000_000;

    /** Prefixo do slug das categorias de fixture, para distingui-las da carga inicial. */
    protected static final String FIXTURE_CATEGORY_PREFIX = "teste-";

    /** Prefixo do slug dos locais de fixture, para distingui-los de dados reais. */
    protected static final String FIXTURE_VENUE_PREFIX = "teste-";

    /** Prefixo do slug dos eventos de fixture. */
    protected static final String FIXTURE_EVENT_PREFIX = "teste-";

    /** Prefixo do code das fontes e do external_id dos vínculos de fixture. */
    protected static final String FIXTURE_SOURCE_PREFIX = "teste-";

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    static {
        POSTGRES.start();
        try {
            applyMigrations();
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Falha ao aplicar database/migrations no container de teste", e);
        }
    }

    private static void applyMigrations() throws IOException, SQLException {
        List<Path> scripts;
        try (var files = Files.list(MIGRATIONS_DIR)) {
            scripts = files.filter(path -> path.toString().endsWith(".sql")).sorted().toList();
        }
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            for (Path script : scripts) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(Files.readString(script));
                }
            }
        }
    }

    @AfterEach
    void removeFixtures() {
        // Ordem das FKs, num só método: sessões e eventos, locais, cidades, subcategorias e categorias.
        // Vínculos e execuções antes dos eventos: excluir o evento só deixaria uma lápide (event_id nulo).
        jdbcTemplate.update("DELETE FROM event_source_link WHERE external_id LIKE ? "
                + "OR source_id IN (SELECT id FROM source WHERE code LIKE ?)",
                FIXTURE_SOURCE_PREFIX + "%", FIXTURE_SOURCE_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM ingestion_run WHERE source_id IN (SELECT id FROM source WHERE code LIKE ?)",
                FIXTURE_SOURCE_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM source WHERE code LIKE ?", FIXTURE_SOURCE_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM event WHERE slug LIKE ?", FIXTURE_EVENT_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM venue WHERE slug LIKE ?", FIXTURE_VENUE_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM city WHERE ibge_code >= ?", FIXTURE_IBGE_BASE);
        jdbcTemplate.update("DELETE FROM category WHERE slug LIKE ? AND parent_id IS NOT NULL",
                FIXTURE_CATEGORY_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM category WHERE slug LIKE ?", FIXTURE_CATEGORY_PREFIX + "%");
    }

    /** Insere uma cidade de fixture ({@code ibgeOffset} é somado a {@link #FIXTURE_IBGE_BASE}). */
    protected void insertFixtureCity(int ibgeOffset, String uf, String name, String slug, boolean active) {
        jdbcTemplate.update(
                "INSERT INTO city (ibge_code, state_uf, name, slug, timezone, active) VALUES (?, ?, ?, ?, 'America/Sao_Paulo', ?)",
                FIXTURE_IBGE_BASE + ibgeOffset, uf, name, slug, active);
    }

    /**
     * Insere uma categoria de fixture; o slug recebe o prefixo {@link #FIXTURE_CATEGORY_PREFIX}.
     *
     * @param parentSlug slug completo do pai (da carga inicial ou de outra fixture), ou {@code null} para raiz
     * @return o slug completo da categoria inserida
     */
    protected String insertFixtureCategory(String slug, String name, String parentSlug, int sortOrder) {
        String fullSlug = FIXTURE_CATEGORY_PREFIX + slug;
        jdbcTemplate.update(
                "INSERT INTO category (parent_id, name, slug, sort_order) "
                        + "VALUES ((SELECT id FROM category WHERE slug = ?), ?, ?, ?)",
                parentSlug, name, fullSlug, sortOrder);
        return fullSlug;
    }

    /**
     * Insere um local de fixture; o slug recebe o prefixo {@link #FIXTURE_VENUE_PREFIX}.
     *
     * @param cityId id da cidade (de {@code city.id})
     * @return o slug completo do local inserido
     */
    protected String insertFixtureVenue(long cityId, String slug, String name, String neighborhood) {
        String fullSlug = FIXTURE_VENUE_PREFIX + slug;
        jdbcTemplate.update(
                "INSERT INTO venue (city_id, name, slug, neighborhood) VALUES (?, ?, ?, ?)",
                cityId, name, fullSlug, neighborhood);
        return fullSlug;
    }

    /** Id de {@code city} pelo par UF e slug. */
    protected long cityId(String uf, String slug) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM city WHERE state_uf = ? AND slug = ?", Long.class, uf, slug);
    }

    /**
     * Insere um evento de fixture (status {@code DRAFT}); o slug recebe o prefixo {@link #FIXTURE_EVENT_PREFIX}.
     *
     * @param venueId id interno do local, ou {@code null}
     * @return o id interno do evento
     */
    protected long insertFixtureEvent(long cityId, long categoryId, Long venueId, String slug, String title) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO event (city_id, category_id, venue_id, title, slug) VALUES (?, ?, ?, ?, ?) RETURNING id",
                Long.class, cityId, categoryId, venueId, title, FIXTURE_EVENT_PREFIX + slug);
    }

    /** Insere uma sessão de fixture (status {@code SCHEDULED}) para o evento de id interno {@code eventId}. */
    protected void insertFixtureOccurrence(long eventId, String startsAt) {
        jdbcTemplate.update("INSERT INTO occurrence (event_id, starts_at) VALUES (?, ?::timestamptz)", eventId, startsAt);
    }

    /**
     * Insere uma fonte de fixture; o code recebe o prefixo {@link #FIXTURE_SOURCE_PREFIX}.
     *
     * @param cityId id da cidade, ou {@code null} para fonte nacional
     * @return o id da fonte
     */
    protected long insertFixtureSource(String code, Long cityId) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO source (code, name, base_url, city_id) VALUES (?, ?, 'https://exemplo.test', ?) RETURNING id",
                Long.class, FIXTURE_SOURCE_PREFIX + code, "Fonte " + code, cityId);
    }

    /** Insere uma execução de fixture ({@code RUNNING}) da fonte e devolve o id. */
    protected long insertFixtureRun(long sourceId) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO ingestion_run (source_id) VALUES (?) RETURNING id", Long.class, sourceId);
    }

    /** Id interno de uma categoria da carga inicial pelo slug. */
    protected long categoryId(String slug) {
        return jdbcTemplate.queryForObject("SELECT id FROM category WHERE slug = ?", Long.class, slug);
    }
}
