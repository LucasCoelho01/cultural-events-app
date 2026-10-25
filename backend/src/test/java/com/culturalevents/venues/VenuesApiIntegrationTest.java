package com.culturalevents.venues;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.culturalevents.AbstractPostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Endpoints /venues. Os locais criados têm slug com prefixo de fixture e são removidos após cada teste. */
@AutoConfigureMockMvc
class VenuesApiIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // --- POST /venues

    @Test
    void criaComSlugGeradoRetorna201ComLocationEIdUuid() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/venues").contentType(MediaType.APPLICATION_JSON)
                        .content(body(saoPaulo(), "Teste Sala São Paulo", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("teste-sala-sao-paulo"))
                .andExpect(jsonPath("$.cityId").value(saoPaulo()))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.updatedAt").isString())
                .andReturn();

        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        assertThat(UUID.fromString(id)).isNotNull();
        assertThat(result.getResponse().getHeader("Location")).endsWith("/api/v1/venues/" + id);
    }

    @Test
    void criaComSlugInformado() throws Exception {
        mockMvc.perform(post("/api/v1/venues").contentType(MediaType.APPLICATION_JSON)
                        .content(body(saoPaulo(), "Qualquer Nome", "teste-sala-sp")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("teste-sala-sp"));
    }

    @Test
    void criaComTodosOsCamposEOsDevolve() throws Exception {
        String json = """
                {"cityId": %d, "name": "Teste Completo", "slug": "teste-completo",
                 "addressLine": "Rua A, 1", "neighborhood": "Centro", "postalCode": "01310-100",
                 "latitude": -23.5, "longitude": -46.6, "websiteUrl": "https://exemplo.org"}
                """.formatted(saoPaulo());

        mockMvc.perform(post("/api/v1/venues").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.addressLine").value("Rua A, 1"))
                .andExpect(jsonPath("$.neighborhood").value("Centro"))
                .andExpect(jsonPath("$.postalCode").value("01310-100"))
                .andExpect(jsonPath("$.latitude").value(-23.5))
                .andExpect(jsonPath("$.longitude").value(-46.6))
                .andExpect(jsonPath("$.websiteUrl").value("https://exemplo.org"));
    }

    @Test
    void idInternoNaoApareceNoCorpoNemNoLocation() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/venues").contentType(MediaType.APPLICATION_JSON)
                        .content(body(saoPaulo(), "Teste Sem Id Interno", null)))
                .andExpect(status().isCreated())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        String publicId = JsonPath.read(json, "$.id");
        Long internalId = jdbcTemplate.queryForObject(
                "SELECT id FROM venue WHERE public_id = ?::uuid", Long.class, publicId);
        assertThat(json).doesNotContainPattern("\"id\"\\s*:\\s*\\d");
        assertThat(result.getResponse().getHeader("Location")).doesNotEndWith("/" + internalId);
    }

    @Test
    void camposInvalidosRetornam422UmErroPorCampo() throws Exception {
        String json = """
                {"cityId": %d, "name": "   ", "slug": "Slug Inválido", "postalCode": "01310100",
                 "latitude": 91, "websiteUrl": "ftp://x"}
                """.formatted(saoPaulo());

        mockMvc.perform(post("/api/v1/venues").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')]", hasSize(1)))
                .andExpect(jsonPath("$.errors[?(@.field == 'slug')]", hasSize(1)))
                .andExpect(jsonPath("$.errors[?(@.field == 'postalCode')]", hasSize(1)))
                .andExpect(jsonPath("$.errors[?(@.field == 'latitude')]", hasSize(1)))
                .andExpect(jsonPath("$.errors[?(@.field == 'websiteUrl')]", hasSize(1)));
        assertThat(countVenues()).isZero();
    }

    @Test
    void camposObrigatoriosAusentesRetornam422() throws Exception {
        mockMvc.perform(post("/api/v1/venues").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.field == 'cityId')]", hasSize(1)))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')]", hasSize(1)));
    }

    @Test
    void cidadeInexistenteRetorna422NoCampoCityId() throws Exception {
        mockMvc.perform(post("/api/v1/venues").contentType(MediaType.APPLICATION_JSON)
                        .content(body(-5L, "Teste Sem Cidade", null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.errors[0].field").value("cityId"));
        assertThat(countVenues()).isZero();
    }

    @Test
    void cidadeInativaRetorna422NoCampoCityId() throws Exception {
        insertFixtureCity(40, "RJ", "Cidade Inativa V", "cidade-inativa-v", false);

        mockMvc.perform(post("/api/v1/venues").contentType(MediaType.APPLICATION_JSON)
                        .content(body(cityId("RJ", "cidade-inativa-v"), "Teste Inativa", null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("cityId"))
                .andExpect(jsonPath("$.errors[0].message").value(containsString("inativa")));
    }

    @Test
    void nomeSemAlfanumericosRetorna422NoCampoName() throws Exception {
        mockMvc.perform(post("/api/v1/venues").contentType(MediaType.APPLICATION_JSON)
                        .content(body(saoPaulo(), "!!!", null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void slugInformadoDuplicadoNaCidadeRetorna409() throws Exception {
        create("Teste Original", "teste-dup");

        mockMvc.perform(post("/api/v1/venues").contentType(MediaType.APPLICATION_JSON)
                        .content(body(saoPaulo(), "Outro Nome", "teste-dup")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        assertThat(countVenues()).isEqualTo(1);
    }

    @Test
    void slugGeradoDuplicadoNaCidadeRetorna409() throws Exception {
        create("Teste Mesmo Nome", null);

        mockMvc.perform(post("/api/v1/venues").contentType(MediaType.APPLICATION_JSON)
                        .content(body(saoPaulo(), "Teste Mesmo Nome", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void mesmoSlugEmOutraCidadeERetorna201() throws Exception {
        insertFixtureCity(41, "RJ", "Outra Cidade V", "outra-cidade-v", true);
        create("Teste Em Duas", "teste-em-duas");

        mockMvc.perform(post("/api/v1/venues").contentType(MediaType.APPLICATION_JSON)
                        .content(body(cityId("RJ", "outra-cidade-v"), "Teste Em Duas", "teste-em-duas")))
                .andExpect(status().isCreated());
    }

    @Test
    void jsonIlegivelRetorna400() throws Exception {
        mockMvc.perform(post("/api/v1/venues").contentType(MediaType.APPLICATION_JSON).content("{nao e json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // --- GET /venues/{id}

    @Test
    void consultaLocalExistente() throws Exception {
        String id = create("Teste Consulta", null);

        mockMvc.perform(get("/api/v1/venues/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Teste Consulta"))
                .andExpect(jsonPath("$.addressLine").value(nullValue()));
    }

    @Test
    void consultaLocalInexistenteRetorna404() throws Exception {
        mockMvc.perform(get("/api/v1/venues/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").isString());
    }

    @Test
    void idQueNaoEUuidRetorna400() throws Exception {
        mockMvc.perform(get("/api/v1/venues/123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // --- DELETE /venues/{id}

    @Test
    void removeLocalERetorna204DepoisDe404() throws Exception {
        String id = create("Teste Remocao", null);

        mockMvc.perform(delete("/api/v1/venues/" + id))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mockMvc.perform(get("/api/v1/venues/" + id)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/venues/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void removeLocalInexistenteRetorna404() throws Exception {
        mockMvc.perform(delete("/api/v1/venues/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // --- PUT /venues/{id}

    @Test
    void substituiTodosOsCamposMantendoIdECreatedAtEAtualizandoUpdatedAt() throws Exception {
        String id = createWithNeighborhood("Teste Antes", "teste-antes", "Centro");
        MvcResult before = mockMvc.perform(get("/api/v1/venues/" + id)).andReturn();
        Thread.sleep(20);

        MvcResult after = mockMvc.perform(put("/api/v1/venues/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(body(saoPaulo(), "Teste Depois", "teste-depois")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Teste Depois"))
                .andExpect(jsonPath("$.slug").value("teste-depois"))
                .andExpect(jsonPath("$.neighborhood").value(nullValue()))
                .andReturn();

        String beforeJson = before.getResponse().getContentAsString();
        String afterJson = after.getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(afterJson, "$.createdAt")).isEqualTo(JsonPath.<String>read(beforeJson, "$.createdAt"));
        assertThat(OffsetDateTime.parse(JsonPath.read(afterJson, "$.updatedAt")))
                .isAfter(OffsetDateTime.parse(JsonPath.read(beforeJson, "$.updatedAt")));
    }

    @Test
    void putSemSlugRegeneraOSlugAPartirDoNome() throws Exception {
        String id = create("Teste Antigo", "teste-slug-manual");

        mockMvc.perform(put("/api/v1/venues/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(body(saoPaulo(), "Teste Novo Nome", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("teste-novo-nome"));
    }

    @Test
    void putRepetindoOProprioSlugNaoConflita() throws Exception {
        String id = create("Teste Proprio", "teste-proprio");

        mockMvc.perform(put("/api/v1/venues/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(body(saoPaulo(), "Teste Proprio Renomeado", "teste-proprio")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("teste-proprio"));
    }

    @Test
    void putDeLocalInexistenteRetorna404() throws Exception {
        mockMvc.perform(put("/api/v1/venues/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(saoPaulo(), "Teste Fantasma", "teste-fantasma")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        assertThat(countVenues()).isZero();
    }

    @Test
    void putComSlugDeOutroLocalRetorna409EMantemOLocal() throws Exception {
        create("Teste Primeiro", "teste-primeiro");
        String segundo = create("Teste Segundo", "teste-segundo");

        mockMvc.perform(put("/api/v1/venues/" + segundo).contentType(MediaType.APPLICATION_JSON)
                        .content(body(saoPaulo(), "Teste Segundo", "teste-primeiro")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        mockMvc.perform(get("/api/v1/venues/" + segundo))
                .andExpect(jsonPath("$.slug").value("teste-segundo"));
    }

    @Test
    void putComCorpoInvalidoRetorna422EMantemOLocal() throws Exception {
        String id = create("Teste Invalido", "teste-invalido");

        mockMvc.perform(put("/api/v1/venues/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cityId": %d, "name": " ", "postalCode": "123"}
                                """.formatted(saoPaulo())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.field == 'name')]", hasSize(1)))
                .andExpect(jsonPath("$.errors[?(@.field == 'postalCode')]", hasSize(1)));
        mockMvc.perform(get("/api/v1/venues/" + id)).andExpect(jsonPath("$.name").value("Teste Invalido"));
    }

    @Test
    void putTrocandoParaCidadeInativaRetorna422EMantemOLocal() throws Exception {
        insertFixtureCity(42, "RJ", "Cidade Inativa P", "cidade-inativa-p", false);
        String id = create("Teste Troca", "teste-troca");

        mockMvc.perform(put("/api/v1/venues/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(body(cityId("RJ", "cidade-inativa-p"), "Teste Troca", "teste-troca")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("cityId"));
        mockMvc.perform(get("/api/v1/venues/" + id)).andExpect(jsonPath("$.cityId").value(saoPaulo()));
    }

    @Test
    void putMantendoCidadeQueFicouInativaNaoRevalidaACidade() throws Exception {
        insertFixtureCity(43, "RJ", "Cidade Desativada", "cidade-desativada", true);
        long city = cityId("RJ", "cidade-desativada");
        String id = createIn(city, "Teste Cidade Desativada", "teste-cidade-desativada");
        jdbcTemplate.update("UPDATE city SET active = false WHERE id = ?", city);

        mockMvc.perform(put("/api/v1/venues/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(body(city, "Teste Cidade Desativada 2", "teste-cidade-desativada")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Teste Cidade Desativada 2"));
    }

    // --- GET /venues

    @Test
    void listagemPadraoTrazLocaisDaCidadePadraoOrdenadosPorNome() throws Exception {
        insertFixtureCity(44, "RJ", "Cidade Lista", "cidade-lista", true);
        insertFixtureVenue(saoPaulo(), "zeta", "Zeta Teste", null);
        insertFixtureVenue(saoPaulo(), "alfa", "Alfa Teste", null);
        insertFixtureVenue(cityId("RJ", "cidade-lista"), "outra", "Outra Cidade Teste", null);

        mockMvc.perform(get("/api/v1/venues"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("Alfa Teste"))
                .andExpect(jsonPath("$.items[1].name").value("Zeta Teste"))
                .andExpect(jsonPath("$.items[?(@.name == 'Outra Cidade Teste')]", hasSize(0)))
                .andExpect(jsonPath("$.page.page").value(0))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.totalPages").value(1));
    }

    @Test
    void filtraPorCidade() throws Exception {
        insertFixtureCity(45, "RJ", "Cidade Filtro", "cidade-filtro", true);
        insertFixtureVenue(saoPaulo(), "em-sp", "Em SP", null);
        insertFixtureVenue(cityId("RJ", "cidade-filtro"), "no-rj", "No RJ", null);

        mockMvc.perform(get("/api/v1/venues").param("city", "rj/cidade-filtro"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].name").value("No RJ"));
    }

    @Test
    void filtraPorBairroSemDiferenciarMaiusculas() throws Exception {
        insertFixtureVenue(saoPaulo(), "pin", "Em Pinheiros", "Pinheiros");
        insertFixtureVenue(saoPaulo(), "cen", "No Centro", "Centro");

        mockMvc.perform(get("/api/v1/venues").param("neighborhood", "pINHEIROS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].name").value("Em Pinheiros"));
    }

    @Test
    void paginacaoRespeitaPageESize() throws Exception {
        insertFixtureVenue(saoPaulo(), "a", "A Pagina", null);
        insertFixtureVenue(saoPaulo(), "b", "B Pagina", null);
        insertFixtureVenue(saoPaulo(), "c", "C Pagina", null);

        mockMvc.perform(get("/api/v1/venues").param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].name").value("B Pagina"))
                .andExpect(jsonPath("$.page.page").value(1))
                .andExpect(jsonPath("$.page.size").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(3));
    }

    @Test
    void cidadeComFormatoInvalidoRetorna400() throws Exception {
        mockMvc.perform(get("/api/v1/venues").param("city", "sao-paulo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(containsString("city")));
    }

    @Test
    void cidadeInexistenteRetorna404() throws Exception {
        mockMvc.perform(get("/api/v1/venues").param("city", "sp/nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void paginacaoInvalidaRetorna400() throws Exception {
        mockMvc.perform(get("/api/v1/venues").param("size", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/venues").param("size", "101")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/venues").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void escritaEmColecaoComMetodoNaoSuportadoRetorna405() throws Exception {
        mockMvc.perform(delete("/api/v1/venues"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }

    // --- cidade padrão mal configurada

    @Nested
    @TestPropertySource(properties = "app.default-city=rj/padrao-inexistente-v")
    class CidadePadraoInexistente {

        @Test
        void listagemSemCityRetorna500SemStackTrace() throws Exception {
            mockMvc.perform(get("/api/v1/venues"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(content().string(not(containsString("\tat "))));
        }
    }

    @Nested
    @TestPropertySource(properties = "app.default-city=rj/padrao-inativa-v")
    class CidadePadraoInativa {

        @Test
        void listagemSemCityRetorna500() throws Exception {
            insertFixtureCity(46, "RJ", "Padrao Inativa V", "padrao-inativa-v", false);

            mockMvc.perform(get("/api/v1/venues"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.detail").value(containsString("inativa")));
        }

        @Test
        void listagemComCityExplicitaIndependeDaCidadePadrao() throws Exception {
            mockMvc.perform(get("/api/v1/venues").param("city", "sp/sao-paulo"))
                    .andExpect(status().isOk());
        }
    }

    // --- helpers

    private long saoPaulo() {
        return cityId("SP", "sao-paulo");
    }

    private int countVenues() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM venue WHERE slug LIKE ?", Integer.class, FIXTURE_VENUE_PREFIX + "%");
    }

    private static String body(long cityId, String name, String slug) {
        String slugField = slug == null ? "" : ", \"slug\": \"" + slug + "\"";
        return "{\"cityId\": " + cityId + ", \"name\": \"" + name + "\"" + slugField + "}";
    }

    private String create(String name, String slug) throws Exception {
        return createIn(saoPaulo(), name, slug);
    }

    private String createIn(long cityId, String name, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/venues").contentType(MediaType.APPLICATION_JSON)
                        .content(body(cityId, name, slug)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createWithNeighborhood(String name, String slug, String neighborhood) throws Exception {
        String json = "{\"cityId\": " + saoPaulo() + ", \"name\": \"" + name + "\", \"slug\": \"" + slug
                + "\", \"neighborhood\": \"" + neighborhood + "\"}";
        MvcResult result = mockMvc.perform(post("/api/v1/venues").contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}
