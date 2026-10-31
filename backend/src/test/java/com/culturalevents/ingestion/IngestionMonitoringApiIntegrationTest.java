package com.culturalevents.ingestion;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.culturalevents.AbstractPostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Leitura e despublicação em lote das execuções do agente (capability ingestion-monitoring). */
@AutoConfigureMockMvc
class IngestionMonitoringApiIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String T0 = "2026-03-01T10:00:00Z";
    private static final String T1 = "2026-03-01T10:05:00Z";
    private static final String BEFORE = "2026-03-01T09:00:00Z";
    private static final String AFTER = "2026-03-01T11:00:00Z";
    private static final String INSIDE = "2026-03-01T10:02:00Z";

    @Autowired
    private MockMvc mockMvc;

    private long sp;
    private long shows;
    private long sourceA;
    private long sourceB;

    @BeforeEach
    void fixtures() {
        sp = cityId("SP", "sao-paulo");
        shows = categoryId("shows");
        sourceA = insertFixtureSource("a", null);
        sourceB = insertFixtureSource("b", null);
    }

    // --- 2.1 / 2.2 listagem

    @Test
    void leUmaExecucaoCriadaPorSql() throws Exception {
        long run = run(sourceA, T0, T1, "SUCCESS", 3, 2, 1, 0, "[]");

        mockMvc.perform(get("/api/v1/admin/ingestion-runs/{id}", run))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(run))
                .andExpect(jsonPath("$.source.code").value("teste-a"))
                .andExpect(jsonPath("$.source.name").value("Fonte a"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.created").value(3))
                .andExpect(jsonPath("$.updated").value(2))
                .andExpect(jsonPath("$.unchanged").value(1))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.durationSeconds").value(300))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.createdEvents").isEmpty());
    }

    @Test
    void listaSemFiltroDaMaisRecenteParaAMaisAntiga() throws Exception {
        long oldest = run(sourceA, "2026-03-01T08:00:00Z", "2026-03-01T08:01:00Z", "SUCCESS", 0, 0, 0, 0, "[]");
        long tieFirst = run(sourceB, T0, T1, "SUCCESS", 0, 0, 0, 0, "[]");
        long tieSecond = run(sourceA, T0, T1, "FAILED", 0, 0, 0, 1, "[]");

        // Só as execuções de fixture: o banco compartilhado pode ter outras (fonte sympla).
        mockMvc.perform(get("/api/v1/admin/ingestion-runs").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == %d)]", tieSecond).exists())
                .andExpect(jsonPath("$.page.page").value(0))
                .andExpect(jsonPath("$.page.size").value(100));

        String body = mockMvc.perform(get("/api/v1/admin/ingestion-runs").param("size", "100"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body.indexOf("\"id\":" + tieSecond)).isLessThan(body.indexOf("\"id\":" + tieFirst));
        assertThat(body.indexOf("\"id\":" + tieFirst)).isLessThan(body.indexOf("\"id\":" + oldest));
    }

    @Test
    void filtroCombinadoPorFonteEStatus() throws Exception {
        run(sourceA, T0, T1, "SUCCESS", 0, 0, 0, 0, "[]");
        long failedA = run(sourceA, T0, T1, "FAILED", 0, 0, 0, 1, "[]");
        run(sourceB, T0, T1, "FAILED", 0, 0, 0, 1, "[]");

        mockMvc.perform(get("/api/v1/admin/ingestion-runs").param("source", "teste-a").param("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(failedA))
                .andExpect(jsonPath("$.items[0].source.code").value("teste-a"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void statusInvalidoRetorna400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ingestion-runs").param("status", "DONE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("status")));
    }

    @Test
    void fonteSemExecucoesRetornaPaginaVazia() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ingestion-runs").param("source", "inexistente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void execucaoEmAndamentoTemDatasNulas() throws Exception {
        long run = insertFixtureRun(sourceA);

        mockMvc.perform(get("/api/v1/admin/ingestion-runs").param("source", "teste-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(run))
                .andExpect(jsonPath("$.items[0].status").value("RUNNING"))
                .andExpect(jsonPath("$.items[0].finishedAt").doesNotExist())
                .andExpect(jsonPath("$.items[0].durationSeconds").doesNotExist());
    }

    // --- 2.3 fontes

    @Test
    void listaAsFontesOrdenadasPorNome() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ingestion-sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'sympla')].name").value("Sympla"))
                .andExpect(jsonPath("$[?(@.code == 'teste-a')].name").value("Fonte a"));
        List<String> names = jdbcTemplate.queryForList("SELECT name FROM source ORDER BY name", String.class);
        String body = mockMvc.perform(get("/api/v1/admin/ingestion-sources"))
                .andReturn().getResponse().getContentAsString();
        int last = -1;
        for (String name : names) {
            int at = body.indexOf("\"name\":\"" + name + "\"");
            assertThat(at).isGreaterThan(last);
            last = at;
        }
    }

    // --- 2.4 eventos criados

    @Test
    void eventoCriadoApareceEEventoSoAtualizadoNao() throws Exception {
        long run = run(sourceA, T0, T1, "SUCCESS", 1, 1, 0, 0, "[]");
        long created = agentEvent("criado", "PUBLISHED");
        long updatedOnly = agentEvent("atualizado", "PUBLISHED");
        link(created, sourceA, "c1", INSIDE, run);
        // Vínculo anterior à execução, mas com last_run_id desta execução: foi atualizado, não criado.
        link(updatedOnly, sourceA, "u1", BEFORE, run);

        mockMvc.perform(get("/api/v1/admin/ingestion-runs/{id}", run))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdEvents.length()").value(1))
                .andExpect(jsonPath("$.createdEvents[0].id").value(publicId(created).toString()))
                .andExpect(jsonPath("$.createdEvents[0].title").value("Evento criado"))
                .andExpect(jsonPath("$.createdEvents[0].status").value("PUBLISHED"));
    }

    @Test
    void vinculoPorDeduplicacaoNaoContaComoCriado() throws Exception {
        long run = run(sourceA, T0, T1, "SUCCESS", 0, 1, 0, 0, "[]");
        long ofOtherSource = agentEvent("de-outra-fonte", "PUBLISHED");
        long ofAdmin = manualEvent("do-admin", "PUBLISHED");
        link(ofOtherSource, sourceB, "o1", BEFORE, null);
        link(ofOtherSource, sourceA, "o1", INSIDE, run);
        link(ofAdmin, sourceA, "m1", INSIDE, run);

        mockMvc.perform(get("/api/v1/admin/ingestion-runs/{id}", run))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdEvents").isEmpty());
    }

    @Test
    void lapideNaoApareceENadaFalha() throws Exception {
        long run = run(sourceA, T0, T1, "SUCCESS", 1, 0, 0, 0, "[]");
        long gone = agentEvent("excluido", "PUBLISHED");
        link(gone, sourceA, "g1", INSIDE, run);
        jdbcTemplate.update("DELETE FROM event WHERE id = ?", gone); // ON DELETE SET NULL deixa a lápide

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM event_source_link WHERE external_id = 'teste-g1' AND event_id IS NULL",
                Integer.class)).isEqualTo(1);
        mockMvc.perform(get("/api/v1/admin/ingestion-runs/{id}", run))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdEvents").isEmpty());
    }

    @Test
    void execucaoSemFimUsaOInstanteAtualComoLimite() throws Exception {
        long running = insertFixtureRun(sourceA);
        long created = agentEvent("em-andamento", "DRAFT");
        jdbcTemplate.update("UPDATE ingestion_run SET started_at = now() - interval '1 hour' WHERE id = ?", running);
        link(created, sourceA, "r1", "now", running);

        mockMvc.perform(get("/api/v1/admin/ingestion-runs/{id}", running))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdEvents.length()").value(1))
                .andExpect(jsonPath("$.createdEvents[0].status").value("DRAFT"));
    }

    @Test
    void vinculoCriadoForaDaJanelaNaoConta() throws Exception {
        long run = run(sourceA, T0, T1, "SUCCESS", 0, 0, 0, 0, "[]");
        long late = agentEvent("tarde", "PUBLISHED");
        link(late, sourceA, "l1", AFTER, null);

        mockMvc.perform(get("/api/v1/admin/ingestion-runs/{id}", run))
                .andExpect(jsonPath("$.createdEvents").isEmpty());
    }

    // --- 2.5 detalhe

    @Test
    void detalheInterpretaOsErros() throws Exception {
        long run = run(sourceA, T0, T1, "PARTIAL", 0, 0, 0, 3,
                "[{\"external_id\": \"123\", \"message\": \"EXTRACTION_FAILED: sem título\"},"
                        + " {\"external_id\": \"-\", \"message\": \"erro inesperado na execução\"},"
                        + " {\"external_id\": \"9\"}]");

        mockMvc.perform(get("/api/v1/admin/ingestion-runs/{id}", run))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[0].code").value("EXTRACTION_FAILED"))
                .andExpect(jsonPath("$.errors[0].externalId").value("123"))
                .andExpect(jsonPath("$.errors[0].detail").value("sem título"))
                .andExpect(jsonPath("$.errors[1].code").doesNotExist())
                .andExpect(jsonPath("$.errors[1].detail").value("erro inesperado na execução"))
                .andExpect(jsonPath("$.errors[2].detail").value("-"));
    }

    @Test
    void execucaoInexistenteRetorna404() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ingestion-runs/{id}", 999_999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void idInvalidoRetorna400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ingestion-runs/{id}", "abc"))
                .andExpect(status().isBadRequest());
    }

    // --- 3.1 despublicação

    @Test
    void despublicaOsPublicadosEPreservaOResto() throws Exception {
        long run = run(sourceA, T0, T1, "SUCCESS", 3, 0, 0, 0, "[]");
        long first = agentEvent("pub-1", "PUBLISHED");
        long second = agentEvent("pub-2", "PUBLISHED");
        long draft = agentEvent("rascunho", "DRAFT");
        link(first, sourceA, "p1", INSIDE, run);
        link(second, sourceA, "p2", INSIDE, run);
        link(draft, sourceA, "p3", INSIDE, run);
        int draftVersion = version(draft);

        mockMvc.perform(post("/api/v1/admin/ingestion-runs/{id}/unpublish", run))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unpublished").value(2));

        assertThat(statusOf(first)).isEqualTo("DRAFT");
        assertThat(statusOf(second)).isEqualTo("DRAFT");
        assertThat(version(first)).isEqualTo(1);
        assertThat(version(second)).isEqualTo(1);
        assertThat(lockedFields(first)).contains("status");
        assertThat(version(draft)).isEqualTo(draftVersion);
        assertThat(lockedFields(draft)).isEmpty();

        mockMvc.perform(post("/api/v1/admin/ingestion-runs/{id}/unpublish", run))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unpublished").value(0));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM event WHERE slug LIKE 'teste-pub-%'",
                Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM event_source_link WHERE source_id = ? AND event_id IS NOT NULL",
                Integer.class, sourceA)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM event_source_link WHERE source_id = ? AND event_id IS NULL",
                Integer.class, sourceA)).isZero();
    }

    @Test
    void despublicarExecucaoInexistenteRetorna404() throws Exception {
        mockMvc.perform(post("/api/v1/admin/ingestion-runs/{id}/unpublish", 999_999_999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void despublicarExecucaoEmAndamentoRetorna409SemAlterarNada() throws Exception {
        long running = insertFixtureRun(sourceA);
        long published = agentEvent("andamento", "PUBLISHED");
        jdbcTemplate.update("UPDATE ingestion_run SET started_at = now() - interval '1 hour' WHERE id = ?", running);
        link(published, sourceA, "a1", "now", running);

        mockMvc.perform(post("/api/v1/admin/ingestion-runs/{id}/unpublish", running))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        assertThat(statusOf(published)).isEqualTo("PUBLISHED");
        assertThat(version(published)).isZero();
    }

    // --- helpers

    private long run(long sourceId, String startedAt, String finishedAt, String runStatus, int created, int updated,
            int unchanged, int failed, String errors) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO ingestion_run (source_id, started_at, finished_at, status, created, updated, unchanged,"
                        + " failed, errors) VALUES (?, ?::timestamptz, ?::timestamptz, ?, ?, ?, ?, ?, ?::jsonb)"
                        + " RETURNING id",
                Long.class, sourceId, startedAt, finishedAt, runStatus, created, updated, unchanged, failed, errors);
    }

    private long agentEvent(String slug, String eventStatus) {
        return event(slug, eventStatus, "AGENT");
    }

    private long manualEvent(String slug, String eventStatus) {
        return event(slug, eventStatus, "MANUAL");
    }

    private long event(String slug, String eventStatus, String origin) {
        long id = insertFixtureEvent(sp, shows, null, slug, "Evento " + slug);
        jdbcTemplate.update("UPDATE event SET status = ?, origin = ? WHERE id = ?", eventStatus, origin, id);
        return id;
    }

    /** Insere o vínculo; {@code createdAt} pode ser {@code "now"}. */
    private void link(Long eventId, long sourceId, String externalId, String createdAt, Long lastRunId) {
        jdbcTemplate.update(
                "INSERT INTO event_source_link (event_id, source_id, external_id, last_run_id, created_at)"
                        + " VALUES (?, ?, ?, ?, ?::timestamptz)",
                eventId, sourceId, FIXTURE_SOURCE_PREFIX + externalId, lastRunId, createdAt);
    }

    private UUID publicId(long eventId) {
        return jdbcTemplate.queryForObject("SELECT public_id FROM event WHERE id = ?", UUID.class, eventId);
    }

    private String statusOf(long eventId) {
        return jdbcTemplate.queryForObject("SELECT status FROM event WHERE id = ?", String.class, eventId);
    }

    private int version(long eventId) {
        return jdbcTemplate.queryForObject("SELECT version FROM event WHERE id = ?", Integer.class, eventId);
    }

    private List<String> lockedFields(long eventId) {
        return jdbcTemplate.queryForObject("SELECT array_to_string(locked_fields, ',') FROM event WHERE id = ?",
                String.class, eventId).isEmpty()
                        ? List.of()
                        : List.of(jdbcTemplate.queryForObject(
                                "SELECT array_to_string(locked_fields, ',') FROM event WHERE id = ?", String.class,
                                eventId).split(","));
    }
}
