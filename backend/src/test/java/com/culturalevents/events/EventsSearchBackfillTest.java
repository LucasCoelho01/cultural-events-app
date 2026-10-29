package com.culturalevents.events;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/** A V006 preenche o vetor de eventos criados antes dela, num banco só com V001 a V005. */
class EventsSearchBackfillTest {

    private static final Path MIGRATIONS_DIR = Path.of("..", "database", "migrations");

    @Test
    void v006PreencheEventosExistentes() throws IOException, SQLException {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")) {
            postgres.start();
            try (Connection connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                    Statement statement = connection.createStatement()) {
                for (Path script : scripts()) {
                    if (script.getFileName().toString().startsWith("V006")) {
                        statement.execute("INSERT INTO event (city_id, category_id, title, slug, description) "
                                + "SELECT c.id, (SELECT id FROM category WHERE slug = 'shows'), "
                                + "'Festival de Música', 'antigo', 'Ao ar livre' FROM city c LIMIT 1");
                    }
                    statement.execute(Files.readString(script));
                }

                try (ResultSet rs = statement.executeQuery(
                        "SELECT search_vector @@ websearch_to_tsquery('portuguese', unaccent('musica')) AS titulo, "
                                + "search_vector @@ websearch_to_tsquery('portuguese', 'livre') AS descricao "
                                + "FROM event WHERE slug = 'antigo'")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getBoolean("titulo")).isTrue();
                    assertThat(rs.getBoolean("descricao")).isTrue();
                }
            }
        }
    }

    private static List<Path> scripts() throws IOException {
        try (var files = Files.list(MIGRATIONS_DIR)) {
            return files.filter(path -> path.toString().endsWith(".sql")).sorted().toList();
        }
    }
}
