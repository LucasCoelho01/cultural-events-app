package com.culturalevents.locations;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.culturalevents.AbstractPostgresIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** As rotas geradas do contrato vivem sob /api/v1 (servers de api/openapi.yaml); o actuator não. */
@AutoConfigureMockMvc
class ApiBasePathIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rotasDeLocationsRespondemSobOPrefixo() throws Exception {
        mockMvc.perform(get("/api/v1/states")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/cities")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/cities/RJ/inexistente")).andExpect(status().isNotFound());
    }

    @Test
    void rotasSemOPrefixoNaoExistem() throws Exception {
        for (String path : new String[] {"/states", "/cities", "/cities/default", "/cities/RJ/qualquer"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
    }

    @Test
    void actuatorPermaneceForaDoPrefixo() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/actuator/health")).andExpect(status().isNotFound());
    }
}
