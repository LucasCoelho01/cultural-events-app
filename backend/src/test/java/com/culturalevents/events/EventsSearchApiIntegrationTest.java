package com.culturalevents.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.culturalevents.AbstractPostgresIntegrationTest;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /events?q= (busca textual). Termos inventados evitam colisão com a carga inicial e com outros testes. */
@AutoConfigureMockMvc
class EventsSearchApiIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String SOON = "2099-03-01T20:00:00Z";
    private static final String LATER = "2099-06-01T20:00:00Z";

    @Autowired
    private MockMvc mockMvc;

    private long sp;
    private long teatro;

    @BeforeEach
    void ids() {
        sp = cityId("SP", "sao-paulo");
        teatro = categoryId("teatro");
    }

    private ResultActions search(String q) throws Exception {
        return mockMvc.perform(get("/api/v1/events").param("q", q));
    }

    @Test
    void semAcentoEncontraTextoAcentuado() throws Exception {
        insertFixtureEvent(sp, teatro, null, "acento", "Festival de Música Quimérica");

        search("quimerica").andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].slug").value("teste-acento"));
        search("QUIMÉRICA").andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void singularEPluralSeEncontram() throws Exception {
        insertFixtureEvent(sp, teatro, null, "singular", "Um xilofone solitário");
        insertFixtureEvent(sp, teatro, null, "plural", "Os xilofones do bairro");

        search("xilofone").andExpect(jsonPath("$.items", hasSize(2)));
        search("xilofones").andExpect(jsonPath("$.items", hasSize(2)));
        insertFixtureEvent(sp, teatro, null, "show-um", "Grande show zumbilândico");
        insertFixtureEvent(sp, teatro, null, "show-dois", "Dois shows zumbilândicos");
        search("show zumbilandico").andExpect(jsonPath("$.items", hasSize(2)));
        search("shows zumbilandicos").andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    void tituloPesaMaisQueDescricaoMesmoComSessaoMaisTardia() throws Exception {
        long naDescricao = insertFixtureEvent(sp, teatro, null, "na-descricao", "Noite qualquer");
        jdbcTemplate.update("UPDATE event SET description = 'Com a banda trombeta' WHERE id = ?", naDescricao);
        insertFixtureOccurrence(naDescricao, SOON);
        long noTitulo = insertFixtureEvent(sp, teatro, null, "no-titulo", "Trombeta ao vivo");
        insertFixtureOccurrence(noTitulo, LATER);

        search("trombeta").andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].slug").value("teste-no-titulo"))
                .andExpect(jsonPath("$.items[1].slug").value("teste-na-descricao"));
    }

    @Test
    void empateDeRelevanciaSaiPelaProximaSessao() throws Exception {
        long tarde = insertFixtureEvent(sp, teatro, null, "empate-tarde", "Cornamusa tarde");
        insertFixtureOccurrence(tarde, LATER);
        long cedo = insertFixtureEvent(sp, teatro, null, "empate-cedo", "Cornamusa cedo");
        insertFixtureOccurrence(cedo, SOON);
        insertFixtureEvent(sp, teatro, null, "empate-sem", "Cornamusa sem sessão");

        search("cornamusa").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].slug").value("teste-empate-cedo"))
                .andExpect(jsonPath("$.items[1].slug").value("teste-empate-tarde"))
                .andExpect(jsonPath("$.items[2].slug").value("teste-empate-sem"));
    }

    @Test
    void qCombinaComCategoriaPeriodoEStatus() throws Exception {
        long certo = insertFixtureEvent(sp, teatro, null, "combina-certo", "Ocarina certa");
        insertFixtureOccurrence(certo, SOON);
        jdbcTemplate.update("UPDATE event SET status = 'PUBLISHED' WHERE id = ?", certo);
        long outraCategoria = insertFixtureEvent(sp, categoryId("danca"), null, "combina-danca", "Ocarina dançante");
        insertFixtureOccurrence(outraCategoria, SOON);
        long foraDoPeriodo = insertFixtureEvent(sp, teatro, null, "combina-fora", "Ocarina fora");
        insertFixtureOccurrence(foraDoPeriodo, LATER);
        long rascunho = insertFixtureEvent(sp, teatro, null, "combina-rascunho", "Ocarina rascunho");
        insertFixtureOccurrence(rascunho, SOON);

        mockMvc.perform(get("/api/v1/events").param("q", "ocarina").param("category", "teatro")
                        .param("from", "2099-02-01T00:00:00Z").param("to", "2099-04-01T00:00:00Z")
                        .param("status", "PUBLISHED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].slug").value("teste-combina-certo"))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        mockMvc.perform(get("/api/v1/events").param("q", "ocarina").param("category", "teatro"))
                .andExpect(jsonPath("$.page.totalElements").value(3));
    }

    @Test
    void qRespeitaACidade() throws Exception {
        insertFixtureCity(81, "RJ", "Cidade Busca", "cidade-busca", true);
        insertFixtureEvent(cityId("RJ", "cidade-busca"), teatro, null, "outra-cidade", "Harpa elétrica");
        insertFixtureEvent(sp, teatro, null, "mesma-cidade", "Harpa elétrica");

        search("harpa").andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].slug").value("teste-mesma-cidade"));
        mockMvc.perform(get("/api/v1/events").param("q", "harpa").param("city", "RJ/cidade-busca"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].slug").value("teste-outra-cidade"));
    }

    @Test
    void paginacaoComQ() throws Exception {
        for (int i = 1; i <= 3; i++) {
            insertFixtureEvent(sp, teatro, null, "pagina-" + i, "Bandolim " + i);
        }

        mockMvc.perform(get("/api/v1/events").param("q", "bandolim").param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].slug").value("teste-pagina-3"))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(2));
    }

    @Test
    void semResultadoRetornaListaVazia() throws Exception {
        search("naoexisteessapalavra").andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void qSoComPalavraIgnoradaRetornaListaVazia() throws Exception {
        insertFixtureEvent(sp, teatro, null, "ignorada", "Festa de rua");

        search("de").andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void sintaxeEspecialNuncaDa500() throws Exception {
        insertFixtureEvent(sp, teatro, null, "sintaxe", "Gaita de foles");

        for (String q : new String[] {"\"gaita -foles", "gaita OR", "-gaita", "gaita & | ! ( )", "'; drop table event;--"}) {
            search(q).andExpect(status().isOk());
        }
        search("gaita -foles").andExpect(jsonPath("$.items", hasSize(0)));
        search("gaita OR laudes").andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void qForaDosLimitesRetorna400() throws Exception {
        for (String q : new String[] {"a", "  a ", "", "   ", "a".repeat(101), " " + "a".repeat(101)}) {
            search(q).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    @Test
    void qNosLimitesRetorna200() throws Exception {
        insertFixtureEvent(sp, teatro, null, "limite", "Rabeca nordestina");

        search("ab").andExpect(status().isOk());
        search("a".repeat(100)).andExpect(status().isOk());
        search("  rabeca  ").andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void categoriaInexistenteComQRetorna404() throws Exception {
        mockMvc.perform(get("/api/v1/events").param("q", "show").param("category", "nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void eventoInseridoPorSqlDiretoAparece() throws Exception {
        jdbcTemplate.update("INSERT INTO event (city_id, category_id, title, slug, origin, status) "
                        + "VALUES (?, ?, 'Concerto de Berimbau Agente', ?, 'AGENT', 'PUBLISHED')",
                sp, teatro, FIXTURE_EVENT_PREFIX + "agente");

        search("berimbau").andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].slug").value("teste-agente"));
    }

    @Test
    void semQAListagemNaoMuda() throws Exception {
        long tarde = insertFixtureEvent(sp, teatro, null, "sem-q-tarde", "Tarde");
        insertFixtureOccurrence(tarde, LATER);
        long cedo = insertFixtureEvent(sp, teatro, null, "sem-q-cedo", "Cedo");
        insertFixtureOccurrence(cedo, SOON);

        mockMvc.perform(get("/api/v1/events").param("category", "teatro"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].slug").value("teste-sem-q-cedo"))
                .andExpect(jsonPath("$.items[1].slug").value("teste-sem-q-tarde"));
    }
}
