package com.culturalevents.categories;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.culturalevents.AbstractPostgresIntegrationTest;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Endpoints /categories e /categories/{slug}. */
@AutoConfigureMockMvc
class CategoriesApiIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String SHOWS_CHILD = "$[?(@.slug == 'shows')].children";

    @Autowired
    private MockMvc mockMvc;

    // --- GET /categories

    @Test
    void arvoreTrazAsSeisRaizesNaOrdemDaCargaInicial() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].name").value("Shows"))
                .andExpect(jsonPath("$[1].name").value("Teatro"))
                .andExpect(jsonPath("$[2].name").value("Exposições"))
                .andExpect(jsonPath("$[3].name").value("Dança"))
                .andExpect(jsonPath("$[4].name").value("Cinema"))
                .andExpect(jsonPath("$[5].name").value("Outros"))
                .andExpect(jsonPath("$[0].parentSlug").doesNotExist());
    }

    @Test
    void raizTrazSubcategoriaComParentSlug() throws Exception {
        insertFixtureCategory("rock", "Rock", "shows", 10);

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(SHOWS_CHILD + "[0]", hasSize(1)))
                .andExpect(jsonPath("$[0].children[0].slug").value(FIXTURE_CATEGORY_PREFIX + "rock"))
                .andExpect(jsonPath("$[0].children[0].parentSlug").value("shows"))
                .andExpect(jsonPath("$[0].children[0].children", hasSize(0)));
    }

    @Test
    void raizSemSubcategoriasTemChildrenVazioENaoNulo() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug == 'cinema')].children[0]", hasSize(0)));
    }

    @Test
    void subcategoriasComEmpateDeSortOrderSaemOrdenadasPorNome() throws Exception {
        insertFixtureCategory("zeta", "Zeta", "shows", 10);
        insertFixtureCategory("alfa", "Alfa", "shows", 10);

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].children[0].name").value("Alfa"))
                .andExpect(jsonPath("$[0].children[1].name").value("Zeta"));
    }

    @Test
    void postEmCategoriesRetorna405ComProblem() throws Exception {
        mockMvc.perform(post("/api/v1/categories"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }

    // --- GET /categories/{slug}

    @Test
    void raizPorSlugTrazSubcategorias() throws Exception {
        insertFixtureCategory("rock", "Rock", "shows", 10);

        mockMvc.perform(get("/api/v1/categories/shows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("shows"))
                .andExpect(jsonPath("$.name").value("Shows"))
                .andExpect(jsonPath("$.sortOrder").value(10))
                .andExpect(jsonPath("$.parentSlug").doesNotExist())
                .andExpect(jsonPath("$.children", hasSize(1)))
                .andExpect(jsonPath("$.children[0].slug").value(FIXTURE_CATEGORY_PREFIX + "rock"));
    }

    @Test
    void subcategoriaPorSlugTrazOPaiEChildrenVazio() throws Exception {
        insertFixtureCategory("rock", "Rock", "shows", 10);

        mockMvc.perform(get("/api/v1/categories/" + FIXTURE_CATEGORY_PREFIX + "rock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(FIXTURE_CATEGORY_PREFIX + "rock"))
                .andExpect(jsonPath("$.parentSlug").value("shows"))
                .andExpect(jsonPath("$.children", hasSize(0)));
    }

    @Test
    void slugInexistenteRetorna404ComProblem() throws Exception {
        mockMvc.perform(get("/api/v1/categories/nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("Categoria não encontrada: nao-existe"));
    }

    @Test
    void slugComMaisDe80CaracteresRetorna400ComProblem() throws Exception {
        mockMvc.perform(get("/api/v1/categories/" + "a".repeat(81)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    @Test
    void slugComExatamente80CaracteresNaoEBarradoPelaValidacao() throws Exception {
        mockMvc.perform(get("/api/v1/categories/" + "a".repeat(80)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteEmCategoriaRetorna405ComProblemEACategoriaPermanece() throws Exception {
        mockMvc.perform(delete("/api/v1/categories/shows"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));

        mockMvc.perform(get("/api/v1/categories/shows"))
                .andExpect(status().isOk());
    }
}
