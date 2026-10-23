package com.culturalevents.platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garante a convenção de nomes de database/migrations: prefixo numérico de 3 dígitos
 * (V###__descricao.sql), nunca editados após o merge (essa segunda parte não é
 * verificável por um teste automatizado e fica documentada na convenção do time).
 */
class MigrationNamingConventionTest {

    private static final Pattern VALID_MIGRATION_NAME = Pattern.compile("^V\\d{3}__[a-z0-9_]+\\.sql$");

    private static final Path MIGRATIONS_DIR = Path.of("..", "database", "migrations");

    @Test
    void todosOsScriptsExistentesSeguemAConvencao() throws IOException {
        List<String> fileNames;
        try (Stream<Path> files = Files.list(MIGRATIONS_DIR)) {
            fileNames = files
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".sql"))
                    .toList();
        }

        assertThat(fileNames).isNotEmpty();
        assertThat(fileNames).allSatisfy(name ->
                assertThat(VALID_MIGRATION_NAME.matcher(name).matches())
                        .as("'%s' deve seguir o padrão V###__descricao.sql", name)
                        .isTrue());
    }

    @Test
    void primeiraMigrationSeguiuAConvencao() throws IOException {
        try (Stream<Path> files = Files.list(MIGRATIONS_DIR)) {
            assertThat(files.map(path -> path.getFileName().toString()))
                    .contains("V001__enable_extensions.sql");
        }
    }

    @Test
    void nomeSemPrefixoDeTresDigitosNaoAtendeAoPadrao() {
        assertThat(VALID_MIGRATION_NAME.matcher("enable_extensions.sql").matches()).isFalse();
        assertThat(VALID_MIGRATION_NAME.matcher("V1__enable_extensions.sql").matches()).isFalse();
        assertThat(VALID_MIGRATION_NAME.matcher("v001__enable_extensions.sql").matches()).isFalse();
    }
}
