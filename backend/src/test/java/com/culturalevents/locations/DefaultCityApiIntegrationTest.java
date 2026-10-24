package com.culturalevents.locations;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.culturalevents.AbstractPostgresIntegrationTest;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /cities/default. Cada cenário de configuração de {@code app.default-city} roda num
 * {@code @Nested} com seu próprio {@code @TestPropertySource}; as cidades usadas são fixtures,
 * então nenhum cenário depende de uma cidade específica da carga inicial.
 */
@AutoConfigureMockMvc
class DefaultCityApiIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @TestPropertySource(properties = "app.default-city=rj/padrao-a")
    class CidadeConfigurada {

        @Test
        void retornaACidadeConfigurada() throws Exception {
            insertFixtureCity(1, "RJ", "Cidade Padrão A", "padrao-a", true);

            mockMvc.perform(get("/api/v1/cities/default"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uf").value("RJ"))
                    .andExpect(jsonPath("$.slug").value("padrao-a"))
                    .andExpect(jsonPath("$.name").value("Cidade Padrão A"))
                    .andExpect(jsonPath("$.active").value(true));
        }

        @Test
        void inexistenteRetorna500ComProblemSemStackTrace() throws Exception {
            // a fixture "padrao-a" não foi inserida neste teste
            mockMvc.perform(get("/api/v1/cities/default"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.title").value("Internal Server Error"))
                    .andExpect(jsonPath("$.detail").value(containsString("não existe")))
                    .andExpect(content().string(not(containsString("\tat "))))
                    .andExpect(content().string(not(containsString("Exception"))));
        }

        @Test
        void inativaRetorna500() throws Exception {
            insertFixtureCity(2, "RJ", "Cidade Padrão A", "padrao-a", false);

            mockMvc.perform(get("/api/v1/cities/default"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.detail").value(containsString("inativa")));
        }
    }

    @Nested
    @TestPropertySource(properties = "app.default-city=mg/padrao-b")
    class OutraCidadeConfigurada {

        @Test
        void trocarAConfiguracaoTrocaACidadePadraoSemMudarCodigo() throws Exception {
            insertFixtureCity(3, "RJ", "Cidade Padrão A", "padrao-a", true);
            insertFixtureCity(4, "MG", "Cidade Padrão B", "padrao-b", true);

            mockMvc.perform(get("/api/v1/cities/default"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uf").value("MG"))
                    .andExpect(jsonPath("$.slug").value("padrao-b"));
        }
    }

    @Nested
    @TestPropertySource(properties = "app.default-city=formato-invalido")
    class ConfiguracaoMalformada {

        @Test
        void retorna500IndicandoOFormatoEsperado() throws Exception {
            mockMvc.perform(get("/api/v1/cities/default"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.detail").value(containsString("uf/slug")));
        }
    }
}
