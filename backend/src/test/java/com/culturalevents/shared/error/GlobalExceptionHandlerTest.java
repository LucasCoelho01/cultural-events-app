package com.culturalevents.shared.error;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@Import(GlobalExceptionHandlerTest.TestController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void corpoMalformadoRetorna400() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").exists());
    }

    @Test
    void recursoNaoEncontradoRetorna404() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("recurso não encontrado"));
    }

    @Test
    void conflitoDeEstadoRetorna409() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("conflito de estado"));
    }

    @Test
    void erroDeValidacaoRetorna422ComErrosPorCampo() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").exists());
    }

    @Test
    void violacoesDeCampoRetornam422ComUmErroPorCampo() throws Exception {
        mockMvc.perform(get("/test/field-violations"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.title").value("Unprocessable Entity"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].field").value("cityId"))
                .andExpect(jsonPath("$.errors[0].message").value("cidade inativa"))
                .andExpect(jsonPath("$.errors[1].field").value("venueId"))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("	at "))));
    }

    @Test
    void violacaoDeUmCampoRetorna422() throws Exception {
        mockMvc.perform(get("/test/field-violation"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void parametroDeQueryInvalidoRetorna400() throws Exception {
        mockMvc.perform(get("/test/param").param("uf", "SPP"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    @Test
    void violacaoEmParametroDeMetodoRetorna400() throws Exception {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        var method = TestController.class.getDeclaredMethod("param", String.class);
        var violations = validator.forExecutables()
                .validateParameters(new TestController(), method, new Object[] {"SPP"});

        var response = new GlobalExceptionHandler()
                .handleConstraintViolation(new ConstraintViolationException(violations));

        org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value()).isEqualTo(400);
        org.assertj.core.api.Assertions.assertThat(((ProblemDetail) response.getBody()).getDetail()).contains("uf");
    }

    @Test
    void violacaoEmBeanContinuaRetornando422() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        var violations = validator.validate(new TestRequest());

        var response = new GlobalExceptionHandler()
                .handleConstraintViolation(new ConstraintViolationException(violations));

        org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value()).isEqualTo(422);
    }

    @Test
    void corpoInvalidoComValidacaoDeMetodoRetorna422ComErrosPorCampo() throws Exception {
        mockMvc.perform(post("/test/items/AB")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").exists());
    }

    @Test
    void pathVariableInvalidoComValidacaoDeMetodoRetorna400() throws Exception {
        mockMvc.perform(post("/test/items/ABC")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"ok\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void erroInesperadoRetorna500SemStackTrace() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("detalhe interno sensível"))))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("RuntimeException"))))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("\tat "))));
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @PostMapping("/validate")
        public void validate(@Valid @RequestBody TestRequest request) {
            // no-op; existe apenas para disparar o pipeline de validação do MVC
        }

        @GetMapping("/not-found")
        public void notFound() {
            throw new NotFoundException("recurso não encontrado");
        }

        @GetMapping("/conflict")
        public void conflict() {
            throw new ConflictException("conflito de estado");
        }

        @GetMapping("/param")
        public void param(@RequestParam @Size(min = 2, max = 2) String uf) {
            // no-op; existe apenas para disparar a validação de parâmetro de query
        }

        @PostMapping("/items/{code}")
        public void item(@PathVariable @Size(min = 2, max = 2) String code, @Valid @RequestBody TestRequest request) {
            // no-op; path restrito + corpo validado ativam a validação de método do MVC
        }

        @GetMapping("/field-violations")
        public void fieldViolations() {
            throw new FieldValidationException(java.util.List.of(
                    new FieldViolation("cityId", "cidade inativa"), new FieldViolation("venueId", "local inexistente")));
        }

        @GetMapping("/field-violation")
        public void fieldViolation() {
            throw new FieldValidationException("name", "não gera slug");
        }

        @GetMapping("/boom")
        public void boom() {
            throw new RuntimeException("detalhe interno sensível");
        }
    }

    static class TestRequest {
        @NotBlank
        public String name;
    }
}
