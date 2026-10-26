package com.culturalevents.events;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.culturalevents.AbstractPostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /events (listagem) e PUT /events/{id} (substituição com lock otimista). */
@AutoConfigureMockMvc
class EventsListAndReplaceApiIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String FUTURE_1 = "2099-03-01T20:00:00Z";
    private static final String FUTURE_2 = "2099-04-01T20:00:00Z";
    private static final String FUTURE_3 = "2099-05-01T20:00:00Z";
    private static final String PAST = "2001-03-01T20:00:00Z";

    @Autowired
    private MockMvc mockMvc;

    private long sp;
    private long shows;

    @BeforeEach
    void ids() {
        sp = cityId("SP", "sao-paulo");
        shows = categoryId("shows");
    }

    // --- GET /events

    @Test
    void listagemPadraoTrazEventosDaCidadePadraoDeQualquerStatus() throws Exception {
        insertFixtureCity(80, "RJ", "Cidade Lista E", "cidade-lista-e", true);
        insertFixtureEvent(sp, shows, null, "rascunho", "Rascunho");
        long publicado = insertFixtureEvent(sp, shows, null, "publicado", "Publicado");
        jdbcTemplate.update("UPDATE event SET status = 'PUBLISHED' WHERE id = ?", publicado);
        insertFixtureEvent(cityId("RJ", "cidade-lista-e"), shows, null, "no-rj", "No RJ");

        mockMvc.perform(get("/api/v1/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[?(@.slug == 'teste-no-rj')]", hasSize(0)))
                .andExpect(jsonPath("$.page.page").value(0))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.totalPages").value(1));
    }

    @Test
    void ordenaPelaProximaSessaoDepoisSemSessaoFuturaDepoisPorId() throws Exception {
        long tarde = insertFixtureEvent(sp, shows, null, "tarde", "Tarde");
        insertFixtureOccurrence(tarde, FUTURE_3);
        long cedo = insertFixtureEvent(sp, shows, null, "cedo", "Cedo");
        insertFixtureOccurrence(cedo, FUTURE_1);
        insertFixtureOccurrence(cedo, "2099-12-01T20:00:00Z");
        long passado = insertFixtureEvent(sp, shows, null, "passado", "Passado");
        insertFixtureOccurrence(passado, PAST);
        insertFixtureEvent(sp, shows, null, "sem-sessao-a", "Sem sessão A");
        insertFixtureEvent(sp, shows, null, "sem-sessao-b", "Sem sessão B");

        mockMvc.perform(get("/api/v1/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].slug").value("teste-cedo"))
                .andExpect(jsonPath("$.items[1].slug").value("teste-tarde"))
                .andExpect(jsonPath("$.items[2].slug").value("teste-passado"))
                .andExpect(jsonPath("$.items[3].slug").value("teste-sem-sessao-a"))
                .andExpect(jsonPath("$.items[4].slug").value("teste-sem-sessao-b"))
                .andExpect(jsonPath("$.items[0].occurrences", hasSize(2)));
    }

    @Test
    void filtraPorCategoriaIncluindoSubcategorias() throws Exception {
        String rock = insertFixtureCategory("rock", "Rock", "shows", 10);
        insertFixtureEvent(sp, shows, null, "na-raiz", "Na raiz");
        insertFixtureEvent(sp, jdbcTemplate.queryForObject("SELECT id FROM category WHERE slug = ?", Long.class, rock),
                null, "na-sub", "Na sub");
        insertFixtureEvent(sp, categoryId("teatro"), null, "no-teatro", "No teatro");

        mockMvc.perform(get("/api/v1/events").param("category", "shows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[?(@.slug == 'teste-no-teatro')]", hasSize(0)));
        mockMvc.perform(get("/api/v1/events").param("category", rock))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].slug").value("teste-na-sub"))
                .andExpect(jsonPath("$.items[0].category.slug").value(rock));
    }

    @Test
    void filtraPorIntervaloDeSessoesSemDuplicarOEvento() throws Exception {
        long dentro = insertFixtureEvent(sp, shows, null, "dentro", "Dentro");
        insertFixtureOccurrence(dentro, "2099-06-10T20:00:00Z");
        insertFixtureOccurrence(dentro, "2099-06-11T20:00:00Z");
        insertFixtureOccurrence(dentro, "2099-12-01T20:00:00Z");
        long fora = insertFixtureEvent(sp, shows, null, "fora", "Fora");
        insertFixtureOccurrence(fora, "2100-01-01T20:00:00Z");
        insertFixtureEvent(sp, shows, null, "sem-sessao", "Sem sessão");

        mockMvc.perform(get("/api/v1/events")
                        .param("from", "2099-06-01T00:00:00Z").param("to", "2099-06-30T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].slug").value("teste-dentro"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
        mockMvc.perform(get("/api/v1/events").param("from", "2099-06-10T20:00:00Z").param("to", "2099-06-10T20:00:00Z"))
                .andExpect(jsonPath("$.items", hasSize(1)));
        mockMvc.perform(get("/api/v1/events").param("from", "2099-07-01T00:00:00Z"))
                .andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    void filtraPorStatusEPorCidade() throws Exception {
        insertFixtureCity(81, "RJ", "Cidade Status", "cidade-status", true);
        long rj = cityId("RJ", "cidade-status");
        long publicado = insertFixtureEvent(sp, shows, null, "pub", "Pub");
        jdbcTemplate.update("UPDATE event SET status = 'PUBLISHED' WHERE id = ?", publicado);
        insertFixtureEvent(sp, shows, null, "rasc", "Rasc");
        insertFixtureEvent(rj, shows, null, "do-rj", "Do RJ");

        mockMvc.perform(get("/api/v1/events").param("status", "PUBLISHED"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].slug").value("teste-pub"));
        mockMvc.perform(get("/api/v1/events").param("city", "rj/cidade-status"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].slug").value("teste-do-rj"));
    }

    @Test
    void listagemTrazLocalECategoriaDeCadaEvento() throws Exception {
        insertFixtureVenue(sp, "local-lista", "Local Lista", null);
        long venueId = jdbcTemplate.queryForObject(
                "SELECT id FROM venue WHERE slug = ?", Long.class, FIXTURE_VENUE_PREFIX + "local-lista");
        insertFixtureEvent(sp, shows, venueId, "a-com-local", "A com local");
        insertFixtureEvent(sp, shows, null, "b-sem-local", "B sem local");

        mockMvc.perform(get("/api/v1/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].venue.name").value("Local Lista"))
                .andExpect(jsonPath("$.items[0].category.slug").value("shows"))
                .andExpect(jsonPath("$.items[1].venue").value(nullValue()));
    }

    @Test
    void paginacaoRespeitaPageESize() throws Exception {
        insertFixtureEvent(sp, shows, null, "p1", "P1");
        insertFixtureEvent(sp, shows, null, "p2", "P2");
        insertFixtureEvent(sp, shows, null, "p3", "P3");

        mockMvc.perform(get("/api/v1/events").param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].slug").value("teste-p2"))
                .andExpect(jsonPath("$.page.page").value(1))
                .andExpect(jsonPath("$.page.size").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(3));
    }

    @Test
    void parametrosInvalidosRetornam400() throws Exception {
        for (String[] param : new String[][] {{"city", "sao-paulo"}, {"status", "FOO"}, {"from", "ontem"},
                {"to", "amanha"}, {"size", "0"}, {"size", "101"}, {"page", "-1"}}) {
            mockMvc.perform(get("/api/v1/events").param(param[0], param[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    @Test
    void cidadeOuCategoriaInexistenteRetorna404() throws Exception {
        mockMvc.perform(get("/api/v1/events").param("city", "sp/nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        mockMvc.perform(get("/api/v1/events").param("category", "nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void metodoNaoSuportadoRetorna405() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/events"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Nested
    @TestPropertySource(properties = "app.default-city=rj/padrao-inexistente-e")
    class CidadePadraoInexistente {

        @Test
        void listagemSemCityRetorna500SemStackTrace() throws Exception {
            mockMvc.perform(get("/api/v1/events"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(content().string(not(containsString("\tat "))));
        }
    }

    @Nested
    @TestPropertySource(properties = "app.default-city=rj/padrao-inativa-e")
    class CidadePadraoInativa {

        @Test
        void listagemSemCityRetorna500ComCityExplicitaFunciona() throws Exception {
            insertFixtureCity(82, "RJ", "Padrao Inativa E", "padrao-inativa-e", false);

            mockMvc.perform(get("/api/v1/events"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.detail").value(containsString("inativa")));
            mockMvc.perform(get("/api/v1/events").param("city", "sp/sao-paulo")).andExpect(status().isOk());
        }
    }

    // --- PUT /events/{id}

    @Test
    void substituiTudoIncrementandoVersionEMantendoIdECreatedAt() throws Exception {
        String id = createWith("Teste Antes", "teste-antes", FUTURE_1, FUTURE_2);
        MvcResult before = mockMvc.perform(get("/api/v1/events/" + id)).andReturn();
        Thread.sleep(20);
        insertFixtureCity(83, "RJ", "Cidade Put", "cidade-put", true);

        MvcResult after = put(id, putBody(0, "PUBLISHED", "Teste Depois", "teste-depois", FUTURE_2, FUTURE_3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("Teste Depois"))
                .andExpect(jsonPath("$.slug").value("teste-depois"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.description").value(nullValue()))
                .andExpect(jsonPath("$.occurrences", hasSize(2)))
                .andExpect(jsonPath("$.occurrences[0].startsAt").value(containsString("2099-04-01")))
                .andExpect(jsonPath("$.occurrences[1].startsAt").value(containsString("2099-05-01")))
                .andReturn();

        String beforeJson = before.getResponse().getContentAsString();
        String afterJson = after.getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(afterJson, "$.createdAt")).isEqualTo(JsonPath.<String>read(beforeJson, "$.createdAt"));
        assertThat(JsonPath.<String>read(afterJson, "$.origin")).isEqualTo("MANUAL");
        assertThat(OffsetDateTime.parse(JsonPath.read(afterJson, "$.updatedAt")))
                .isAfter(OffsetDateTime.parse(JsonPath.read(beforeJson, "$.updatedAt")));
        mockMvc.perform(get("/api/v1/events/" + id))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.occurrences", hasSize(2)));
    }

    @Test
    void putIdenticoAindaIncrementaAVersionEOUpdatedAt() throws Exception {
        String id = createWith("Teste Identico", "teste-identico", FUTURE_1);
        MvcResult before = mockMvc.perform(get("/api/v1/events/" + id)).andReturn();
        Thread.sleep(20);

        MvcResult after = put(id, putBody(0, "DRAFT", "Teste Identico", "teste-identico", FUTURE_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andReturn();

        assertThat(OffsetDateTime.parse(JsonPath.read(after.getResponse().getContentAsString(), "$.updatedAt")))
                .isAfter(OffsetDateTime.parse(JsonPath.read(before.getResponse().getContentAsString(), "$.updatedAt")));
    }

    @Test
    void mudarSomenteAsSessoesIncrementaAVersion() throws Exception {
        String id = createWith("Teste So Sessoes", "teste-so-sessoes", FUTURE_1);

        put(id, putBody(0, "DRAFT", "Teste So Sessoes", "teste-so-sessoes", FUTURE_1, FUTURE_2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.occurrences", hasSize(2)));
    }

    @Test
    void versionDesatualizadaRetorna409EMantemOEvento() throws Exception {
        String id = createWith("Teste Versao", "teste-versao", FUTURE_1);
        put(id, putBody(0, "DRAFT", "Teste Versao 2", "teste-versao", FUTURE_1)).andExpect(status().isOk());

        put(id, putBody(0, "DRAFT", "Teste Versao Velha", "teste-versao", FUTURE_1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        mockMvc.perform(get("/api/v1/events/" + id))
                .andExpect(jsonPath("$.title").value("Teste Versao 2"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void doisPutComAMesmaVersionApenasUmEAceito() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // Várias rodadas, cada uma com um evento novo, para exercitar também a corrida real no UPDATE.
            for (int round = 0; round < 8; round++) {
                String slug = "teste-corrida-" + round;
                String id = createWith("Teste Corrida " + round, slug, FUTURE_1);
                CountDownLatch start = new CountDownLatch(1);
                List<Future<Integer>> futures = new ArrayList<>();
                for (String title : List.of("Teste Corrida A", "Teste Corrida B")) {
                    futures.add(pool.submit(() -> {
                        start.await();
                        return put(id, putBody(0, "DRAFT", title, slug, FUTURE_1))
                                .andReturn().getResponse().getStatus();
                    }));
                }
                start.countDown();
                List<Integer> statuses = new ArrayList<>();
                for (Future<Integer> future : futures) {
                    statuses.add(future.get());
                }

                assertThat(statuses).as("rodada %d", round).containsExactlyInAnyOrder(200, 409);
                mockMvc.perform(get("/api/v1/events/" + id)).andExpect(jsonPath("$.version").value(1));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void sessaoMantidaPorStartsAtEAtualizadaENaoDuplicada() throws Exception {
        String id = createWith("Teste Sessao Mantida", "teste-sessao-mantida", FUTURE_1, FUTURE_2);
        String json = """
                {"version": 0, "status": "DRAFT", "cityId": %d, "categoryId": %d, "title": "Teste Sessao Mantida",
                 "slug": "teste-sessao-mantida", "occurrences": [{"startsAt": "%s", "status": "SOLD_OUT"}]}
                """.formatted(sp, shows, FUTURE_1);

        put(id, json)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occurrences", hasSize(1)))
                .andExpect(jsonPath("$.occurrences[0].status").value("SOLD_OUT"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM occurrence o JOIN event e ON e.id = o.event_id "
                + "WHERE e.public_id = ?::uuid", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void sessoesAusentesNoPutSaoRemovidas() throws Exception {
        String id = createWith("Teste Remove Sessoes", "teste-remove-sessoes", FUTURE_1, FUTURE_2);

        put(id, putBody(0, "DRAFT", "Teste Remove Sessoes", "teste-remove-sessoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occurrences", hasSize(0)));
    }

    @Test
    void putDeEventoInexistenteRetorna404() throws Exception {
        put(UUID.randomUUID().toString(), putBody(0, "DRAFT", "Teste Fantasma", "teste-fantasma"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM event WHERE slug LIKE ?", Integer.class, FIXTURE_EVENT_PREFIX + "%")).isZero();
    }

    @Test
    void putComSlugDeOutroEventoRetorna409EMantemOEvento() throws Exception {
        createWith("Teste Primeiro", "teste-primeiro", FUTURE_1);
        String segundo = createWith("Teste Segundo", "teste-segundo", FUTURE_1);

        put(segundo, putBody(0, "DRAFT", "Teste Segundo", "teste-primeiro", FUTURE_1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        mockMvc.perform(get("/api/v1/events/" + segundo))
                .andExpect(jsonPath("$.slug").value("teste-segundo"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void putSemSlugRegeneraOSlugDoTitulo() throws Exception {
        String id = createWith("Teste Slug Antigo", "teste-slug-manual", FUTURE_1);
        String json = """
                {"version": 0, "status": "DRAFT", "cityId": %d, "categoryId": %d, "title": "Teste Slug Novo"}
                """.formatted(sp, shows);

        put(id, json).andExpect(status().isOk()).andExpect(jsonPath("$.slug").value("teste-slug-novo"));
    }

    @Test
    void putSemVersionStatusOuComCorpoInvalidoRetorna422() throws Exception {
        String id = createWith("Teste Invalido", "teste-invalido", FUTURE_1);

        put(id, "{\"status\": \"DRAFT\", \"cityId\": %d, \"categoryId\": %d, \"title\": \"Teste Invalido\"}"
                .formatted(sp, shows))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.field == 'version')]", hasSize(1)));
        put(id, "{\"version\": 0, \"cityId\": %d, \"categoryId\": %d, \"title\": \"Teste Invalido\"}"
                .formatted(sp, shows))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.field == 'status')]", hasSize(1)));
        put(id, "{\"version\": 0, \"status\": \"DRAFT\", \"cityId\": %d, \"categoryId\": -5, \"title\": \" \"}"
                .formatted(sp))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.field == 'title')]", hasSize(1)));
        put(id, "{\"version\": 0, \"status\": \"DRAFT\", \"cityId\": %d, \"categoryId\": -5, \"title\": \"Teste Invalido\"}"
                .formatted(sp))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("categoryId"));
        mockMvc.perform(get("/api/v1/events/" + id))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.title").value("Teste Invalido"));
    }

    @Test
    void statusAusenteNoPutNaoDespublicaOEvento() throws Exception {
        String id = createWith("Teste Publicado", "teste-publicado", FUTURE_1);
        put(id, putBody(0, "PUBLISHED", "Teste Publicado", "teste-publicado", FUTURE_1)).andExpect(status().isOk());

        put(id, "{\"version\": 1, \"cityId\": %d, \"categoryId\": %d, \"title\": \"Teste Publicado\", \"slug\": \"teste-publicado\"}"
                .formatted(sp, shows))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("status"));
        mockMvc.perform(get("/api/v1/events/" + id))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void putPublicadoSemSessaoFuturaRetorna422EmOccurrences() throws Exception {
        String id = createWith("Teste Publicar Put", "teste-publicar-put", FUTURE_1);

        put(id, putBody(0, "PUBLISHED", "Teste Publicar Put", "teste-publicar-put", PAST))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("occurrences"));
    }

    @Test
    void putTrocandoParaCidadeInativaOuLocalDeOutraCidadeRetorna422() throws Exception {
        insertFixtureCity(84, "RJ", "Cidade Inativa Put", "cidade-inativa-put", false);
        String id = createWith("Teste Troca Cidade", "teste-troca-cidade", FUTURE_1);

        put(id, "{\"version\": 0, \"status\": \"DRAFT\", \"cityId\": %d, \"categoryId\": %d, \"title\": \"Teste Troca Cidade\"}"
                .formatted(cityId("RJ", "cidade-inativa-put"), shows))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("cityId"));

        insertFixtureVenue(cityId("RJ", "cidade-inativa-put"), "local-outra", "Local outra", null);
        UUID venue = jdbcTemplate.queryForObject("SELECT public_id FROM venue WHERE slug = ?", UUID.class,
                FIXTURE_VENUE_PREFIX + "local-outra");
        put(id, "{\"version\": 0, \"status\": \"DRAFT\", \"cityId\": %d, \"categoryId\": %d, \"venueId\": \"%s\", \"title\": \"Teste Troca Cidade\"}"
                .formatted(sp, shows, venue))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("venueId"));
    }

    // --- helpers

    private ResultActions put(String id, String json) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/events/" + id).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String putBody(int version, String status, String title, String slug, String... startsAt) {
        StringBuilder sessions = new StringBuilder();
        for (String start : startsAt) {
            sessions.append(sessions.isEmpty() ? "" : ",").append("{\"startsAt\": \"").append(start).append("\"}");
        }
        return "{\"version\": %d, \"status\": \"%s\", \"cityId\": %d, \"categoryId\": %d, \"title\": \"%s\", \"slug\": \"%s\", \"occurrences\": [%s]}"
                .formatted(version, status, sp, shows, title, slug, sessions);
    }

    private String createWith(String title, String slug, String... startsAt) throws Exception {
        StringBuilder sessions = new StringBuilder();
        for (String start : startsAt) {
            sessions.append(sessions.isEmpty() ? "" : ",").append("{\"startsAt\": \"").append(start).append("\"}");
        }
        MvcResult result = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"cityId\": %d, \"categoryId\": %d, \"title\": \"%s\", \"slug\": \"%s\", \"occurrences\": [%s]}"
                                        .formatted(sp, shows, title, slug, sessions)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}
