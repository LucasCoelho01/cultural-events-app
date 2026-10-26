package com.culturalevents.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.culturalevents.AbstractPostgresIntegrationTest;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /cities/{uf}/{slug}/events/{eventSlug}: detalhe público, somente de eventos PUBLISHED da cidade. */
@AutoConfigureMockMvc
class EventsPublicApiIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String URL = "/api/v1/cities/{uf}/{slug}/events/{eventSlug}";

    @Autowired
    private MockMvc mockMvc;

    private long cityA;
    private long cityB;
    private long shows;

    @BeforeEach
    void cities() {
        insertFixtureCity(90, "RJ", "Cidade Pública A", "cidade-publica-a", true);
        insertFixtureCity(91, "RJ", "Cidade Pública B", "cidade-publica-b", true);
        cityA = cityId("RJ", "cidade-publica-a");
        cityB = cityId("RJ", "cidade-publica-b");
        shows = categoryId("shows");
    }

    @Test
    void devolveEventoPublicadoComCategoriaLocalESessoesOrdenadas() throws Exception {
        insertFixtureVenue(cityA, "local-publico", "Local Público", "Centro");
        long venueId = jdbcTemplate.queryForObject(
                "SELECT id FROM venue WHERE slug = ?", Long.class, FIXTURE_VENUE_PREFIX + "local-publico");
        long event = insertFixtureEvent(cityA, shows, venueId, "publicado", "Publicado");
        jdbcTemplate.update("UPDATE event SET status = 'PUBLISHED' WHERE id = ?", event);
        insertFixtureOccurrence(event, "2099-05-01T20:00:00Z");
        insertFixtureOccurrence(event, "2099-03-01T20:00:00Z");

        mockMvc.perform(get(URL, "RJ", "cidade-publica-a", "teste-publicado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("teste-publicado"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.category.slug").value("shows"))
                .andExpect(jsonPath("$.venue.name").value("Local Público"))
                .andExpect(jsonPath("$.occurrences", hasSize(2)))
                .andExpect(jsonPath("$.occurrences[0].startsAt").value(containsString("2099-03-01")))
                .andExpect(jsonPath("$.id").isString());
    }

    @Test
    void ufNaoDiferenciaMaiusculasEEventoSemLocalTemVenueNulo() throws Exception {
        long event = insertFixtureEvent(cityA, shows, null, "sem-local", "Sem local");
        jdbcTemplate.update("UPDATE event SET status = 'PUBLISHED' WHERE id = ?", event);

        mockMvc.perform(get(URL, "rj", "cidade-publica-a", "teste-sem-local"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.venue").value(nullValue()));
    }

    @Test
    void naoPublicadoRetorna404IgualAoInexistente() throws Exception {
        for (String status : new String[] { "DRAFT", "CANCELLED", "ARCHIVED" }) {
            long event = insertFixtureEvent(cityA, shows, null, status.toLowerCase(), status);
            jdbcTemplate.update("UPDATE event SET status = ? WHERE id = ?", status, event);
        }

        String inexistente = mockMvc.perform(get(URL, "RJ", "cidade-publica-a", "teste-nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andReturn().getResponse().getContentAsString();

        for (String status : new String[] { "draft", "cancelled", "archived" }) {
            mockMvc.perform(get(URL, "RJ", "cidade-publica-a", "teste-" + status))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.title").isNotEmpty())
                    .andExpect(jsonPath("$.detail", not(containsString("DRAFT"))));
        }
        org.assertj.core.api.Assertions.assertThat(inexistente).contains("Evento não encontrado");
    }

    @Test
    void eventoPublicadoDeOutraCidadeRetorna404() throws Exception {
        long event = insertFixtureEvent(cityB, shows, null, "da-outra", "Da outra");
        jdbcTemplate.update("UPDATE event SET status = 'PUBLISHED' WHERE id = ?", event);

        mockMvc.perform(get(URL, "RJ", "cidade-publica-a", "teste-da-outra"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        mockMvc.perform(get(URL, "RJ", "cidade-publica-b", "teste-da-outra"))
                .andExpect(status().isOk());
    }

    @Test
    void cidadeInexistenteRetorna404() throws Exception {
        mockMvc.perform(get(URL, "RJ", "nenhuma-cidade", "teste-qualquer"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value(containsString("Cidade não encontrada")));
    }

    @Test
    void ufMalformadaRetorna400() throws Exception {
        mockMvc.perform(get(URL, "sao", "cidade-publica-a", "teste-qualquer"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void naoExpoeIdsInternos() throws Exception {
        long event = insertFixtureEvent(cityA, shows, null, "ids", "Ids");
        jdbcTemplate.update("UPDATE event SET status = 'PUBLISHED' WHERE id = ?", event);

        mockMvc.perform(get(URL, "RJ", "cidade-publica-a", "teste-ids"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.matchesPattern("[0-9a-f-]{36}")));
    }
}
