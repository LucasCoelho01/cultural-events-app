package com.culturalevents.venues.api;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.culturalevents.venues.application.VenueValidationException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VenuesExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new VenuesExceptionHandler())
            .build();

    @Test
    void validacaoDeNegocioRetorna422ComErrosPorCampo() throws Exception {
        mockMvc.perform(get("/invalid"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.title").value("Unprocessable Entity"))
                .andExpect(jsonPath("$.errors[0].field").value("cityId"))
                .andExpect(jsonPath("$.errors[0].message").value("a cidade informada não existe"))
                .andExpect(content().string(not(containsString("\tat "))));
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/invalid")
        void invalid() {
            throw new VenueValidationException("cityId", "a cidade informada não existe");
        }
    }
}
