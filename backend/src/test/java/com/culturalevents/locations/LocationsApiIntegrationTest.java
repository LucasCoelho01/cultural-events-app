package com.culturalevents.locations;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.culturalevents.AbstractPostgresIntegrationTest;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Endpoints /states e /cities; /cities/default fica em {@link DefaultCityApiIntegrationTest}. */
@AutoConfigureMockMvc
class LocationsApiIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // --- GET /states

    @Test
    void listaAs27UfsOrdenadasPorNome() throws Exception {
        mockMvc.perform(get("/api/v1/states"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(27)))
                .andExpect(jsonPath("$[0].name").value("Acre"))
                .andExpect(jsonPath("$[0].uf").value("AC"))
                .andExpect(jsonPath("$[26].name").value("Tocantins"));
    }

    @Test
    void postEmStatesRetorna405ComProblem() throws Exception {
        mockMvc.perform(post("/api/v1/states"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }

    // --- GET /cities

    @Test
    void listagemPadraoTrazApenasCidadesAtivas() throws Exception {
        insertFixtureCity(1, "RJ", "Cidade Ativa", "cidade-ativa", true);
        insertFixtureCity(2, "RJ", "Cidade Inativa", "cidade-inativa", false);

        mockMvc.perform(get("/api/v1/cities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.slug == 'cidade-ativa')]", hasSize(1)))
                .andExpect(jsonPath("$.items[?(@.slug == 'cidade-inativa')]", hasSize(0)))
                .andExpect(jsonPath("$.items[?(@.active == false)]", hasSize(0)))
                .andExpect(jsonPath("$.page.page").value(0))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").isNumber())
                .andExpect(jsonPath("$.page.totalPages").isNumber());
    }

    @Test
    void filtraPorUfSemDiferenciarMaiusculas() throws Exception {
        insertFixtureCity(3, "RJ", "Cidade RJ", "cidade-rj", true);
        insertFixtureCity(4, "MG", "Cidade MG", "cidade-mg", true);

        mockMvc.perform(get("/api/v1/cities").param("uf", "rj"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.slug == 'cidade-rj')]", hasSize(1)))
                .andExpect(jsonPath("$.items[?(@.slug == 'cidade-mg')]", hasSize(0)))
                .andExpect(jsonPath("$.items[?(@.uf != 'RJ')]", hasSize(0)));
    }

    @Test
    void filtraPorCidadesInativas() throws Exception {
        insertFixtureCity(5, "RJ", "Cidade Ativa", "cidade-ativa", true);
        insertFixtureCity(6, "RJ", "Cidade Inativa", "cidade-inativa", false);

        mockMvc.perform(get("/api/v1/cities").param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.slug == 'cidade-inativa')]", hasSize(1)))
                .andExpect(jsonPath("$.items[?(@.active == true)]", hasSize(0)));
    }

    @Test
    void paginaRespeitandoPageESizeEOrdemPorNome() throws Exception {
        insertFixtureCity(7, "RJ", "Cidade B", "cidade-b", false);
        insertFixtureCity(8, "RJ", "Cidade A", "cidade-a", false);
        insertFixtureCity(9, "RJ", "Cidade C", "cidade-c", false);

        mockMvc.perform(get("/api/v1/cities").param("uf", "RJ").param("active", "false")
                        .param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].name").value("Cidade B"))
                .andExpect(jsonPath("$.page.page").value(1))
                .andExpect(jsonPath("$.page.size").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(3));
    }

    @Test
    void ufComTamanhoInvalidoRetorna400() throws Exception {
        for (String uf : new String[] {"S", "SPP"}) {
            mockMvc.perform(get("/api/v1/cities").param("uf", uf))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.title").value("Bad Request"))
                    .andExpect(jsonPath("$.detail").exists());
        }
    }

    @Test
    void paginacaoInvalidaRetorna400() throws Exception {
        mockMvc.perform(get("/api/v1/cities").param("size", "0")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        mockMvc.perform(get("/api/v1/cities").param("size", "101")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        mockMvc.perform(get("/api/v1/cities").param("page", "-1")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void ufBemFormadaPoremInexistenteRetornaListaVazia() throws Exception {
        mockMvc.perform(get("/api/v1/cities").param("uf", "ZZ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    // --- GET /cities/{uf}/{slug}

    @Test
    void buscaCidadePorUfESlugComTodosOsCampos() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO city (ibge_code, state_uf, name, slug, timezone, latitude, longitude, active)"
                        + " VALUES (?, 'RJ', 'Cidade Teste', 'cidade-teste', 'America/Sao_Paulo', -22.5, -43.25, true)",
                FIXTURE_IBGE_BASE + 10);

        mockMvc.perform(get("/api/v1/cities/RJ/cidade-teste"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.ibgeCode").value(FIXTURE_IBGE_BASE + 10))
                .andExpect(jsonPath("$.uf").value("RJ"))
                .andExpect(jsonPath("$.name").value("Cidade Teste"))
                .andExpect(jsonPath("$.slug").value("cidade-teste"))
                .andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"))
                .andExpect(jsonPath("$.latitude").value(-22.5))
                .andExpect(jsonPath("$.longitude").value(-43.25))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void ufEmMinusculasERetornaAMesmaCidade() throws Exception {
        insertFixtureCity(11, "RJ", "Cidade Teste", "cidade-teste", true);

        mockMvc.perform(get("/api/v1/cities/rj/cidade-teste"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uf").value("RJ"))
                .andExpect(jsonPath("$.slug").value("cidade-teste"));
    }

    @Test
    void cidadeInativaERetornadaComActiveFalse() throws Exception {
        insertFixtureCity(12, "RJ", "Cidade Inativa", "cidade-inativa", false);

        mockMvc.perform(get("/api/v1/cities/RJ/cidade-inativa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void cidadeSemCoordenadasSerializaLatitudeELongitudeNulas() throws Exception {
        insertFixtureCity(13, "RJ", "Cidade Sem Coordenadas", "sem-coordenadas", true);

        mockMvc.perform(get("/api/v1/cities/RJ/sem-coordenadas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value((Object) null))
                .andExpect(jsonPath("$.longitude").value((Object) null))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.ibgeCode").exists())
                .andExpect(jsonPath("$.timezone").exists());
    }

    @Test
    void cidadeInexistenteRetorna404ComProblem() throws Exception {
        mockMvc.perform(get("/api/v1/cities/RJ/slug-que-nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void ufMalFormadaNaRotaRetorna400() throws Exception {
        mockMvc.perform(get("/api/v1/cities/SPP/qualquer"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // --- somente leitura

    @Test
    void escritaEmCidadesRetorna405() throws Exception {
        mockMvc.perform(post("/api/v1/cities")).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
        mockMvc.perform(put("/api/v1/cities/RJ/qualquer")).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
        mockMvc.perform(delete("/api/v1/cities/RJ/qualquer")).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }
}
