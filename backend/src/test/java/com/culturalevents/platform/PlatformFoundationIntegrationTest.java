package com.culturalevents.platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integração base: aplica os scripts de database/migrations num container
 * Postgres do zero e sobe o contexto Spring completo com ddl-auto=validate, provando
 * que o schema e o mapeamento JPA (ainda sem entidades) continuam compatíveis.
 */
@SpringBootTest
@Testcontainers
class PlatformFoundationIntegrationTest {

    private static final Path MIGRATIONS_DIR = Path.of("..", "database", "migrations");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @BeforeAll
    static void aplicarMigrations() throws IOException, Exception {
        List<Path> scripts;
        try (var files = Files.list(MIGRATIONS_DIR)) {
            scripts = files.filter(path -> path.toString().endsWith(".sql")).sorted().toList();
        }

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            for (Path script : scripts) {
                String sql = Files.readString(script);
                try (Statement statement = connection.createStatement()) {
                    statement.execute(sql);
                }
            }
        }
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextoSobeComMigrationsAplicadasEDdlAutoValidate() {
        assertThat(applicationContext).isNotNull();
    }
}
