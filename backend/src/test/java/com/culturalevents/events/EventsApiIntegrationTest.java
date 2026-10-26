package com.culturalevents.events;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.culturalevents.AbstractPostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST, GET /events/{id} e DELETE. Eventos de fixture têm slug com prefixo e são removidos após cada teste. */
@AutoConfigureMockMvc
class EventsApiIntegrationTest extends AbstractPostgresIntegrationTest {

    static final String FUTURE_1 = "2099-03-01T20:00:00Z";
    static final String FUTURE_2 = "2099-04-01T20:00:00Z";
    static final String PAST = "2001-03-01T20:00:00Z";

    @Autowired
    private MockMvc mockMvc;

    private long sp;
    private long shows;

    @BeforeEach
    void ids() {
        sp = cityId("SP", "sao-paulo");
        shows = categoryId("shows");
    }

    // --- POST /events

    @Test
    void criacaoMinimaRetorna201ComLocationEPadroes() throws Exception {
        MvcResult result = post("{\"cityId\": %d, \"categoryId\": %d, \"title\": \"Teste Show Minimo\"}".formatted(sp, shows))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.slug").value("teste-show-minimo"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.origin").value("MANUAL"))
                .andExpect(jsonPath("$.isFree").value(false))
                .andExpect(jsonPath("$.occurrences", hasSize(0)))
                .andExpect(jsonPath("$.venue").value(nullValue()))
                .andExpect(jsonPath("$.category.id").value(shows))
                .andExpect(jsonPath("$.category.slug").value("shows"))
                .andExpect(jsonPath("$.category.name").value("Shows"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.updatedAt").isString())
                .andReturn();

        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        assertThat(UUID.fromString(id)).isNotNull();
        assertThat(result.getResponse().getHeader("Location")).endsWith("/api/v1/events/" + id);
    }

    @Test
    void criacaoCompletaComLocalEDuasSessoesDevolveSessoesOrdenadas() throws Exception {
        UUID venue = venue(sp, "local-completo");
        String json = """
                {"cityId": %d, "categoryId": %d, "venueId": "%s", "title": "Teste Show Completo",
                 "slug": "teste-show-completo", "description": "Descrição", "status": "PUBLISHED", "ageRating": "14",
                 "priceMinCents": 1000, "priceMaxCents": 5000, "isFree": false,
                 "ticketUrl": "https://ingressos.exemplo.org/1", "imageUrl": "https://img.exemplo.org/1.png",
                 "occurrences": [
                   {"startsAt": "%s", "endsAt": "2099-04-01T22:00:00Z", "status": "SOLD_OUT"},
                   {"startsAt": "%s"}]}
                """.formatted(sp, shows, venue, FUTURE_2, FUTURE_1);

        post(json)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.description").value("Descrição"))
                .andExpect(jsonPath("$.ageRating").value("14"))
                .andExpect(jsonPath("$.priceMinCents").value(1000))
                .andExpect(jsonPath("$.priceMaxCents").value(5000))
                .andExpect(jsonPath("$.ticketUrl").value("https://ingressos.exemplo.org/1"))
                .andExpect(jsonPath("$.imageUrl").value("https://img.exemplo.org/1.png"))
                .andExpect(jsonPath("$.venue.id").value(venue.toString()))
                .andExpect(jsonPath("$.venue.name").value("Local local-completo"))
                .andExpect(jsonPath("$.venue.slug").value(FIXTURE_VENUE_PREFIX + "local-completo"))
                .andExpect(jsonPath("$.occurrences", hasSize(2)))
                .andExpect(jsonPath("$.occurrences[0].startsAt").value(startsWith("2099-03-01")))
                .andExpect(jsonPath("$.occurrences[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$.occurrences[0].endsAt").value(nullValue()))
                .andExpect(jsonPath("$.occurrences[1].startsAt").value(startsWith("2099-04-01")))
                .andExpect(jsonPath("$.occurrences[1].status").value("SOLD_OUT"))
                .andExpect(jsonPath("$.occurrences[1].endsAt").value(startsWith("2099-04-01")));
    }

    @Test
    void idsInternosNaoAparecemNoCorpoNemNoLocation() throws Exception {
        UUID venue = venue(sp, "local-ids");
        MvcResult result = post("{\"cityId\": %d, \"categoryId\": %d, \"venueId\": \"%s\", \"title\": \"Teste Ids\"}"
                .formatted(sp, shows, venue))
                .andExpect(status().isCreated())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        String publicId = JsonPath.read(json, "$.id");
        long internalId = jdbcTemplate.queryForObject(
                "SELECT id FROM event WHERE public_id = ?::uuid", Long.class, publicId);
        assertThat(JsonPath.<Object>read(json, "$.id")).as("o id do evento é o UUID, não um número").isInstanceOf(String.class);
        assertThat(JsonPath.<Object>read(json, "$.venue.id")).isEqualTo(venue.toString());
        assertThat(result.getResponse().getHeader("Location")).doesNotEndWith("/" + internalId);
    }

    @Test
    void camposInvalidosRetornam422UmErroPorCampo() throws Exception {
        String json = """
                {"cityId": %d, "categoryId": %d, "title": "   ", "slug": "Slug Inválido", "priceMinCents": -1,
                 "ticketUrl": "ftp://x", "imageUrl": "javascript:x"}
                """.formatted(sp, shows);

        post(json)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.errors[?(@.field == 'title')]", hasSize(1)))
                .andExpect(jsonPath("$.errors[?(@.field == 'slug')]", hasSize(1)))
                .andExpect(jsonPath("$.errors[?(@.field == 'priceMinCents')]", hasSize(1)))
                .andExpect(jsonPath("$.errors[?(@.field == 'ticketUrl')]", hasSize(1)))
                .andExpect(jsonPath("$.errors[?(@.field == 'imageUrl')]", hasSize(1)));
        assertThat(countEvents()).isZero();
    }

    @Test
    void camposObrigatoriosAusentesRetornam422() throws Exception {
        post("{}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.field == 'cityId')]", hasSize(1)))
                .andExpect(jsonPath("$.errors[?(@.field == 'categoryId')]", hasSize(1)))
                .andExpect(jsonPath("$.errors[?(@.field == 'title')]", hasSize(1)));
    }

    @Test
    void sessaoSemStartsAtRetorna422EmOccurrences() throws Exception {
        post("{\"cityId\": %d, \"categoryId\": %d, \"title\": \"Teste Sessao Sem Inicio\", \"occurrences\": [{}]}"
                .formatted(sp, shows))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value(startsWith("occurrences")));
    }

    @Test
    void statusDesconhecidoRetorna400() throws Exception {
        post("{\"cityId\": %d, \"categoryId\": %d, \"title\": \"Teste Status\", \"status\": \"FOO\"}".formatted(sp, shows))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        post("{\"cityId\": %d, \"categoryId\": %d, \"title\": \"Teste Status 2\", \"occurrences\": [{\"startsAt\": \"%s\", \"status\": \"FOO\"}]}"
                .formatted(sp, shows, FUTURE_1))
                .andExpect(status().isBadRequest());
        assertThat(countEvents()).isZero();
    }

    @Test
    void jsonIlegivelRetorna400() throws Exception {
        post("{nao e json").andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void cidadeInexistenteRetorna422NoCampoCityId() throws Exception {
        post("{\"cityId\": -5, \"categoryId\": %d, \"title\": \"Teste Sem Cidade\"}".formatted(shows))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("cityId"));
        assertThat(countEvents()).isZero();
    }

    @Test
    void cidadeInativaRetorna422NoCampoCityId() throws Exception {
        insertFixtureCity(70, "RJ", "Cidade Inativa E", "cidade-inativa-e", false);

        post("{\"cityId\": %d, \"categoryId\": %d, \"title\": \"Teste Cidade Inativa\"}"
                .formatted(cityId("RJ", "cidade-inativa-e"), shows))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("cityId"))
                .andExpect(jsonPath("$.errors[0].message").value(containsString("inativa")));
    }

    @Test
    void categoriaInexistenteRetorna422NoCampoCategoryId() throws Exception {
        post("{\"cityId\": %d, \"categoryId\": -5, \"title\": \"Teste Sem Categoria\"}".formatted(sp))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("categoryId"));
    }

    @Test
    void localInexistenteRetorna422NoCampoVenueId() throws Exception {
        post("{\"cityId\": %d, \"categoryId\": %d, \"venueId\": \"%s\", \"title\": \"Teste Sem Local\"}"
                .formatted(sp, shows, UUID.randomUUID()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("venueId"));
    }

    @Test
    void localDeOutraCidadeRetorna422NoCampoVenueId() throws Exception {
        insertFixtureCity(71, "RJ", "Cidade Do Local", "cidade-do-local", true);
        UUID venueRj = venue(cityId("RJ", "cidade-do-local"), "local-rj");

        post("{\"cityId\": %d, \"categoryId\": %d, \"venueId\": \"%s\", \"title\": \"Teste Local De Outra Cidade\"}"
                .formatted(sp, shows, venueRj))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("venueId"))
                .andExpect(jsonPath("$.errors[0].message").value(containsString("outra cidade")));
        assertThat(countEvents()).isZero();
    }

    @Test
    void errosDeReferenciaSaoAcumulados() throws Exception {
        post("{\"cityId\": -5, \"categoryId\": -5, \"venueId\": \"%s\", \"title\": \"Teste Tudo Errado\"}"
                .formatted(UUID.randomUUID()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.field == 'cityId')]", hasSize(1)))
                .andExpect(jsonPath("$.errors[?(@.field == 'categoryId')]", hasSize(1)))
                .andExpect(jsonPath("$.errors[?(@.field == 'venueId')]", hasSize(1)));
    }

    @Test
    void eventoGratuitoComPrecoPositivoRetorna422EmIsFree() throws Exception {
        post("{\"cityId\": %d, \"categoryId\": %d, \"title\": \"Teste Gratis Caro\", \"isFree\": true, \"priceMinCents\": 1000}"
                .formatted(sp, shows))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("isFree"));
    }

    @Test
    void eventoGratuitoSemPrecoOuComPrecoZeroEAceito() throws Exception {
        post("{\"cityId\": %d, \"categoryId\": %d, \"title\": \"Teste Gratis\", \"isFree\": true}"
                .formatted(sp, shows))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isFree").value(true));
        post("{\"cityId\": %d, \"categoryId\": %d, \"title\": \"Teste Gratis Zero\", \"isFree\": true, \"priceMinCents\": 0, \"priceMaxCents\": 0}"
                .formatted(sp, shows))
                .andExpect(status().isCreated());
    }

    @Test
    void precoMaximoMenorQueOMinimoRetorna422EmPriceMaxCents() throws Exception {
        post("{\"cityId\": %d, \"categoryId\": %d, \"title\": \"Teste Faixa\", \"priceMinCents\": 5000, \"priceMaxCents\": 1000}"
                .formatted(sp, shows))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("priceMaxCents"));
    }

    @Test
    void sessaoComFimAnteriorAoInicioRetorna422() throws Exception {
        post("{\"cityId\": %d, \"categoryId\": %d, \"title\": \"Teste Fim Antes\", \"occurrences\": [{\"startsAt\": \"%s\", \"endsAt\": \"2099-02-01T20:00:00Z\"}]}"
                .formatted(sp, shows, FUTURE_1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("occurrences[0].endsAt"));
        assertThat(countEvents()).isZero();
    }

    @Test
    void sessoesComOMesmoInicioRetornam422() throws Exception {
        post("{\"cityId\": %d, \"categoryId\": %d, \"title\": \"Teste Sessoes Repetidas\", \"occurrences\": [{\"startsAt\": \"%s\"}, {\"startsAt\": \"%s\"}]}"
                .formatted(sp, shows, FUTURE_1, FUTURE_1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("occurrences[1].startsAt"));
        assertThat(countEvents()).isZero();
    }

    @Test
    void tituloSemAlfanumericosRetorna422EmTitle() throws Exception {
        post("{\"cityId\": %d, \"categoryId\": %d, \"title\": \"!!!\"}".formatted(sp, shows))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
    }

    // --- publicação

    @Test
    void publicarSemSessoesRetorna422EmOccurrences() throws Exception {
        post("{\"cityId\": %d, \"categoryId\": %d, \"title\": \"Teste Publicar Sem Sessao\", \"status\": \"PUBLISHED\"}"
                .formatted(sp, shows))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("occurrences"));
        assertThat(countEvents()).isZero();
    }

    @Test
    void publicarSoComSessoesPassadasRetorna422() throws Exception {
        post("{\"cityId\": %d, \"categoryId\": %d, \"title\": \"Teste Publicar Passado\", \"status\": \"PUBLISHED\", \"occurrences\": [{\"startsAt\": \"%s\"}]}"
                .formatted(sp, shows, PAST))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("occurrences"));
    }

    @Test
    void publicarComSessaoFuturaEAceito() throws Exception {
        post("{\"cityId\": %d, \"categoryId\": %d, \"title\": \"Teste Publicar Futuro\", \"status\": \"PUBLISHED\", \"occurrences\": [{\"startsAt\": \"%s\"}, {\"startsAt\": \"%s\"}]}"
                .formatted(sp, shows, PAST, FUTURE_1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void arquivarSoComSessoesPassadasEAceito() throws Exception {
        post("{\"cityId\": %d, \"categoryId\": %d, \"title\": \"Teste Arquivado\", \"status\": \"ARCHIVED\", \"occurrences\": [{\"startsAt\": \"%s\"}]}"
                .formatted(sp, shows, PAST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    // --- slug

    @Test
    void slugInformadoDuplicadoNaCidadeRetorna409() throws Exception {
        create("Teste Original", "teste-dup");

        post("{\"cityId\": %d, \"categoryId\": %d, \"title\": \"Outro Titulo\", \"slug\": \"teste-dup\"}"
                .formatted(sp, shows))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        assertThat(countEvents()).isEqualTo(1);
    }

    @Test
    void slugGeradoDuplicadoNaCidadeRetorna409() throws Exception {
        create("Teste Mesmo Titulo", null);

        post("{\"cityId\": %d, \"categoryId\": %d, \"title\": \"Teste Mesmo Titulo\"}".formatted(sp, shows))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void mesmoSlugEmOutraCidadeERetorna201() throws Exception {
        insertFixtureCity(72, "RJ", "Outra Cidade E", "outra-cidade-e", true);
        create("Teste Em Duas", "teste-em-duas");

        post("{\"cityId\": %d, \"categoryId\": %d, \"title\": \"Teste Em Duas\", \"slug\": \"teste-em-duas\"}"
                .formatted(cityId("RJ", "outra-cidade-e"), shows))
                .andExpect(status().isCreated());
    }

    // --- GET /events/{id}

    @Test
    void consultaEventoComLocalESessoes() throws Exception {
        UUID venue = venue(sp, "local-get");
        String id = createWith("Teste Consulta", venue, FUTURE_2, FUTURE_1);

        mockMvc.perform(get("/api/v1/events/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.venue.id").value(venue.toString()))
                .andExpect(jsonPath("$.category.slug").value("shows"))
                .andExpect(jsonPath("$.occurrences", hasSize(2)))
                .andExpect(jsonPath("$.occurrences[0].startsAt").value(startsWith("2099-03-01")))
                .andExpect(jsonPath("$.occurrences[1].startsAt").value(startsWith("2099-04-01")));
    }

    @Test
    void consultaEventoSemLocalTemVenueNuloEOpcionaisNulos() throws Exception {
        String id = create("Teste Sem Local", null);

        mockMvc.perform(get("/api/v1/events/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.venue").value(nullValue()))
                .andExpect(jsonPath("$.description").value(nullValue()))
                .andExpect(jsonPath("$.priceMinCents").value(nullValue()))
                .andExpect(jsonPath("$.ticketUrl").value(nullValue()));
    }

    @Test
    void consultaEventoInexistenteRetorna404() throws Exception {
        mockMvc.perform(get("/api/v1/events/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").isString());
    }

    @Test
    void idQueNaoEUuidRetorna400() throws Exception {
        mockMvc.perform(get("/api/v1/events/123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // --- DELETE /events/{id}

    @Test
    void removeEventoESuasSessoesERetorna404DepoisDeRemovido() throws Exception {
        String id = createWith("Teste Remocao", null, FUTURE_1, FUTURE_2);
        long internalId = jdbcTemplate.queryForObject(
                "SELECT id FROM event WHERE public_id = ?::uuid", Long.class, id);

        mockMvc.perform(delete("/api/v1/events/" + id))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(get("/api/v1/events/" + id)).andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM occurrence WHERE event_id = ?", Integer.class, internalId)).isZero();
        mockMvc.perform(delete("/api/v1/events/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void removeEventoInexistenteRetorna404() throws Exception {
        mockMvc.perform(delete("/api/v1/events/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void metodoNaoSuportadoNaColecaoRetorna405() throws Exception {
        mockMvc.perform(delete("/api/v1/events"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }

    // --- helpers

    private ResultActions post(String json) throws Exception {
        return mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private UUID venue(long cityId, String slug) {
        insertFixtureVenue(cityId, slug, "Local " + slug, null);
        return jdbcTemplate.queryForObject(
                "SELECT public_id FROM venue WHERE slug = ?", UUID.class, FIXTURE_VENUE_PREFIX + slug);
    }

    private int countEvents() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM event WHERE slug LIKE ?", Integer.class, FIXTURE_EVENT_PREFIX + "%");
    }

    private String create(String title, String slug) throws Exception {
        String slugField = slug == null ? "" : ", \"slug\": \"" + slug + "\"";
        MvcResult result = post("{\"cityId\": %d, \"categoryId\": %d, \"title\": \"%s\"%s}"
                .formatted(sp, shows, title, slugField))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createWith(String title, UUID venue, String... startsAt) throws Exception {
        StringBuilder sessions = new StringBuilder();
        for (String start : startsAt) {
            sessions.append(sessions.isEmpty() ? "" : ",").append("{\"startsAt\": \"").append(start).append("\"}");
        }
        String venueField = venue == null ? "" : ", \"venueId\": \"" + venue + "\"";
        MvcResult result = post("{\"cityId\": %d, \"categoryId\": %d, \"title\": \"%s\"%s, \"occurrences\": [%s]}"
                .formatted(sp, shows, title, venueField, sessions))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}
