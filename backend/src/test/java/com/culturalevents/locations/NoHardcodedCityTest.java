package com.culturalevents.locations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nenhum código de produção pode citar a cidade da carga inicial: a cidade padrão vem de
 * {@code app.default-city} e dos dados do banco. Os literais proibidos ficam só aqui, em
 * {@code src/test}, fora da área varrida.
 */
class NoHardcodedCityTest {

    private static final Path MAIN_SOURCES = Path.of("src", "main", "java");

    private static final List<String> FORBIDDEN = List.of("são paulo", "sao paulo", "sao-paulo", "3550308");

    @Test
    void codigoDeProducaoNaoCitaCidadeEspecifica() throws IOException {
        assertThat(MAIN_SOURCES).isDirectory();

        assertThat(findViolations(MAIN_SOURCES)).isEmpty();
    }

    @Test
    void varreduraFalhaAoEncontrarReferenciaEIndicaArquivoELiteral(@TempDir Path dir) throws IOException {
        Path violator = dir.resolve("Violator.java");
        Files.writeString(violator, "class Violator { String city = \"São Paulo\"; }");
        Files.writeString(dir.resolve("Clean.java"), "class Clean { String city = \"outra\"; }");

        List<String> violations = findViolations(dir);

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0)).contains("Violator.java").contains("são paulo");
    }

    private static List<String> findViolations(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> violationsIn(path).stream())
                    .toList();
        }
    }

    private static List<String> violationsIn(Path file) {
        try {
            String content = Files.readString(file).toLowerCase(Locale.ROOT);
            return FORBIDDEN.stream()
                    .filter(content::contains)
                    .map(literal -> file.getFileName() + " contém \"" + literal + "\"")
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler " + file, e);
        }
    }
}
