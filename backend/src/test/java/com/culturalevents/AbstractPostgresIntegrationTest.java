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
 * códigos IBGE reais) e são removidas automaticamente após cada teste.
 */
@SpringBootTest
public abstract class AbstractPostgresIntegrationTest {

    private static final Path MIGRATIONS_DIR = Path.of("..", "database", "migrations");

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    /** Primeiro código IBGE reservado a cidades de fixture de teste. */
    protected static final int FIXTURE_IBGE_BASE = 9_000_000;

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
    void removeFixtureCities() {
        jdbcTemplate.update("DELETE FROM city WHERE ibge_code >= ?", FIXTURE_IBGE_BASE);
    }

    /** Insere uma cidade de fixture ({@code ibgeOffset} é somado a {@link #FIXTURE_IBGE_BASE}). */
    protected void insertFixtureCity(int ibgeOffset, String uf, String name, String slug, boolean active) {
        jdbcTemplate.update(
                "INSERT INTO city (ibge_code, state_uf, name, slug, timezone, active) VALUES (?, ?, ?, ?, 'America/Sao_Paulo', ?)",
                FIXTURE_IBGE_BASE + ibgeOffset, uf, name, slug, active);
    }
}
