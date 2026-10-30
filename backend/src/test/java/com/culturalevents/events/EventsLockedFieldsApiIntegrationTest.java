package com.culturalevents.events;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.culturalevents.AbstractPostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Correção manual vence": o PUT em evento do agente preenche {@code locked_fields}; o contrato HTTP não o expõe. */
@AutoConfigureMockMvc
class EventsLockedFieldsApiIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String START_1 = "2099-03-01T20:00:00Z";
    private static final String START_2 = "2099-04-01T20:00:00Z";

    @Autowired
    private MockMvc mockMvc;

    private long sp;
    private long shows;

    @BeforeEach
    void ids() {
        sp = cityId("SP", "sao-paulo");
        shows = categoryId("shows");
    }

    @Test
    void putEmEventoDoAgentePreencheAsColunasAlteradas() throws Exception {
        UUID id = agentEvent("lock-colunas");

        putEvent(id, body(0, "Teste Lock Novo", 5000, "teste-lock-colunas", START_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        assertThat(lockedFields(id)).containsExactly("title", "price_min_cents");
    }

    @Test
    void mudarSoAsSessoesTravaOccurrences() throws Exception {
        UUID id = agentEvent("lock-sessoes");

        putEvent(id, body(0, "Teste Lock Original", null, "teste-lock-sessoes", START_1, START_2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        assertThat(lockedFields(id)).containsExactly("occurrences");
    }

    @Test
    void mudarSoOTituloNaoTravaOccurrences() throws Exception {
        UUID id = agentEvent("lock-so-titulo");

        putEvent(id, body(0, "Teste Lock Outro", null, "teste-lock-so-titulo", START_1)).andExpect(status().isOk());

        assertThat(lockedFields(id)).containsExactly("title");
    }

    @Test
    void putSemMudancasNaoTravaNada() throws Exception {
        UUID id = agentEvent("lock-igual");

        putEvent(id, body(0, "Teste Lock Original", null, "teste-lock-igual", START_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        assertThat(lockedFields(id)).isEmpty();
    }

    @Test
    void campoJaTravadoNaoDuplicaENovosSeAcumulam() throws Exception {
        UUID id = agentEvent("lock-acumula");

        putEvent(id, body(0, "Teste Lock A", null, "teste-lock-acumula", START_1)).andExpect(status().isOk());
        putEvent(id, body(1, "Teste Lock B", 100, "teste-lock-acumula", START_1)).andExpect(status().isOk());

        assertThat(lockedFields(id)).containsExactly("title", "price_min_cents");
    }

    @Test
    void trocarOSlugTravaOSlug() throws Exception {
        UUID id = agentEvent("lock-slug");

        putEvent(id, body(0, "Teste Lock Original", null, "teste-lock-slug-novo", START_1)).andExpect(status().isOk());

        assertThat(lockedFields(id)).containsExactly("slug");
    }

    @Test
    void versionDesatualizadaEValidacaoInvalidaNaoAlteramOsLocks() throws Exception {
        UUID id = agentEvent("lock-rejeitado");

        putEvent(id, body(7, "Teste Lock Rejeitado", null, "teste-lock-rejeitado", START_1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        putEvent(id, body(0, "", null, "teste-lock-rejeitado", START_1))
                .andExpect(status().isUnprocessableEntity());

        assertThat(lockedFields(id)).isEmpty();
    }

    @Test
    void eventoManualMantemLockedFieldsVazio() throws Exception {
        long event = insertFixtureEvent(sp, shows, null, "lock-manual", "Teste Lock Manual");
        insertFixtureOccurrence(event, START_1);
        UUID id = publicId(event);

        putEvent(id, body(0, "Teste Lock Alterado", 900, "teste-lock-manual", START_1, START_2)).andExpect(status().isOk());

        assertThat(lockedFields(id)).isEmpty();
    }

    @Test
    void respostaDaApiNaoExpoeLockedFields() throws Exception {
        UUID id = agentEvent("lock-resposta");
        putEvent(id, body(0, "Teste Lock Visivel", null, "teste-lock-resposta", START_1))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("ocked"))));

        mockMvc.perform(get("/api/v1/events/" + id))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("ocked"))));
        mockMvc.perform(get("/api/v1/events"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("ocked"))));
    }

    @Test
    void lockedFieldsNoCorpoEIgnorado() throws Exception {
        UUID id = agentEvent("lock-corpo");
        String withLocks = body(0, "Teste Lock Original", null, "teste-lock-corpo", START_1)
                .replaceFirst("\\{", "{\"lockedFields\": [\"title\", \"description\"], \"locked_fields\": [\"slug\"], ");

        putEvent(id, withLocks).andExpect(status().isOk());
        assertThat(lockedFields(id)).isEmpty();

        mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lockedFields\": [\"title\"], \"cityId\": %d, \"categoryId\": %d, \"title\": \"Teste Lock Post\", \"slug\": \"teste-lock-post\"}"
                                .formatted(sp, shows)))
                .andExpect(status().isCreated());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT cardinality(locked_fields) FROM event WHERE slug = 'teste-lock-post'", Integer.class)).isZero();
    }

    @Test
    void excluirEventoVinculadoPelaApiDeixaLapide() throws Exception {
        UUID id = agentEvent("lock-lapide");
        long source = insertFixtureSource("lapide-api", null);
        jdbcTemplate.update("INSERT INTO event_source_link (event_id, source_id, external_id) "
                + "SELECT id, ?, ? FROM event WHERE public_id = ?", source, FIXTURE_SOURCE_PREFIX + "ext-api", id);

        mockMvc.perform(delete("/api/v1/events/" + id)).andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForList(
                "SELECT event_id FROM event_source_link WHERE source_id = ? AND external_id = ?", Long.class, source,
                FIXTURE_SOURCE_PREFIX + "ext-api")).hasSize(1).containsOnlyNulls();
    }

    // --- helpers

    /** Evento {@code AGENT} (inserido por SQL, como o agente faria) com título "Teste Lock Original" e uma sessão. */
    private UUID agentEvent(String slug) {
        long event = insertFixtureEvent(sp, shows, null, slug, "Teste Lock Original");
        jdbcTemplate.update("UPDATE event SET origin = 'AGENT' WHERE id = ?", event);
        insertFixtureOccurrence(event, START_1);
        return publicId(event);
    }

    private UUID publicId(long event) {
        return jdbcTemplate.queryForObject("SELECT public_id FROM event WHERE id = ?", UUID.class, event);
    }

    private List<String> lockedFields(UUID id) {
        return jdbcTemplate.queryForList(
                "SELECT unnest(locked_fields) FROM event WHERE public_id = ?", String.class, id);
    }

    private ResultActions putEvent(UUID id, String json) throws Exception {
        return mockMvc.perform(put("/api/v1/events/" + id).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String body(int version, String title, Integer priceMin, String slug, String... startsAt) {
        StringBuilder sessions = new StringBuilder();
        for (String start : startsAt) {
            sessions.append(sessions.isEmpty() ? "" : ",").append("{\"startsAt\": \"").append(start).append("\"}");
        }
        String price = priceMin == null ? "" : ", \"priceMinCents\": " + priceMin;
        return "{\"version\": %d, \"status\": \"DRAFT\", \"cityId\": %d, \"categoryId\": %d, \"title\": \"%s\", \"slug\": \"%s\"%s, \"occurrences\": [%s]}"
                .formatted(version, sp, shows, title, slug, price, sessions);
    }
}
