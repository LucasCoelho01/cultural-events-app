package com.culturalevents.platform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prova que o mecanismo de aplicação de scripts SQL rejeita SQL inválido, usando uma
 * fixture dedicada em src/test/resources/invalid-migrations — nunca corrompendo
 * database/migrations/V001__enable_extensions.sql.
 */
@Testcontainers
class InvalidMigrationScriptTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Test
    void scriptInvalidoFalhaAoSerAplicado() throws Exception {
        Path invalidScript = Path.of("src", "test", "resources", "invalid-migrations", "V999__invalid.sql");
        String sql = Files.readString(invalidScript);

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            assertThatThrownBy(() -> {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(sql);
                }
            }).isInstanceOf(SQLException.class);
        }
    }
}
