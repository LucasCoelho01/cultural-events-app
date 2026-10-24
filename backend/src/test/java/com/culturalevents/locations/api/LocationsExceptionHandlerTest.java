package com.culturalevents.locations.api;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.culturalevents.locations.application.DefaultCityMisconfiguredException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LocationsExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new LocationsExceptionHandler())
            .build();

    @Test
    void configuracaoInvalidaDaCidadePadraoRetorna500ComProblemSemStackTrace() throws Exception {
        mockMvc.perform(get("/misconfigured"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.detail").value("cidade padrão inativa"))
                .andExpect(content().string(not(containsString("\tat "))));
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/misconfigured")
        void misconfigured() {
            throw new DefaultCityMisconfiguredException("cidade padrão inativa");
        }
    }
}
