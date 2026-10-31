package com.culturalevents.ingestion.application;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RunErrorParserTest {

    @Test
    void mensagemComCodigoSeparaCodigoEDetalhe() {
        List<RunError> errors = RunErrorParser.parse(
                "[{\"external_id\": \"123\", \"message\": \"EXTRACTION_FAILED: sem título\"}]");

        assertThat(errors).containsExactly(new RunError("EXTRACTION_FAILED", "123", "sem título"));
    }

    @Test
    void mensagemSemCodigoMantemATextoNoDetalhe() {
        List<RunError> errors = RunErrorParser.parse(
                "[{\"external_id\": \"-\", \"message\": \"erro inesperado na execução\"}]");

        assertThat(errors).containsExactly(new RunError(null, "-", "erro inesperado na execução"));
    }

    @Test
    void doisPontosSemPrefixoDeCodigoNaoViramCodigo() {
        List<RunError> errors = RunErrorParser.parse("[{\"external_id\": \"9\", \"message\": \"falha: tente de novo\"}]");

        assertThat(errors).containsExactly(new RunError(null, "9", "falha: tente de novo"));
    }

    @Test
    void itemSemMensagemViraTraco() {
        List<RunError> errors = RunErrorParser.parse("[{\"external_id\": \"7\"}, {}, 42, null]");

        assertThat(errors).containsExactly(
                new RunError(null, "7", "-"),
                new RunError(null, "-", "-"),
                new RunError(null, "-", "-"),
                new RunError(null, "-", "-"));
    }

    @Test
    void externalIdNumericoViraTexto() {
        List<RunError> errors = RunErrorParser.parse("[{\"external_id\": 55, \"message\": \"X_Y: z\"}]");

        assertThat(errors).containsExactly(new RunError("X_Y", "55", "z"));
    }

    @Test
    void listaVaziaOuInvalidaNaoFalha() {
        assertThat(RunErrorParser.parse("[]")).isEmpty();
        assertThat(RunErrorParser.parse("{}")).isEmpty();
        assertThat(RunErrorParser.parse("isto não é json")).isEmpty();
        assertThat(RunErrorParser.parse(null)).isEmpty();
    }
}
