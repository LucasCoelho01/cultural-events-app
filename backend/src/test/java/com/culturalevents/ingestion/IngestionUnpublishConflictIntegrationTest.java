package com.culturalevents.ingestion;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.culturalevents.AbstractPostgresIntegrationTest;
import com.culturalevents.events.domain.Event;
import com.culturalevents.events.domain.EventRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A despublicação em lote é atômica: um conflito de versão no meio do lote desfaz tudo e responde 409. */
@AutoConfigureMockMvc
class IngestionUnpublishConflictIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
    private EventRepository eventRepository;

    @Test
    void eventoAlteradoNoMeioDoLoteRetorna409SemAlterarNenhum() throws Exception {
        long source = insertFixtureSource("conflito", null);
        long run = jdbcTemplate.queryForObject(
                "INSERT INTO ingestion_run (source_id, started_at, finished_at, status, created)"
                        + " VALUES (?, '2026-03-01T10:00:00Z', '2026-03-01T10:05:00Z', 'SUCCESS', 2) RETURNING id",
                Long.class, source);
        long first = publishedAgentEvent("conflito-1", source, run, "c1");
        long second = publishedAgentEvent("conflito-2", source, run, "c2");

        // Outro usuário edita o segundo evento depois da leitura do lote e antes de ele ser gravado.
        doAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            if (event.getId().equals(second)) {
                jdbcTemplate.update("UPDATE event SET version = version + 1 WHERE id = ?", second);
            }
            return invocation.callRealMethod();
        }).when(eventRepository).update(any(Event.class));

        mockMvc.perform(post("/api/v1/admin/ingestion-runs/{id}/unpublish", run))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        assertThat(statusOf(first)).isEqualTo("PUBLISHED");
        assertThat(statusOf(second)).isEqualTo("PUBLISHED");
        assertThat(versionOf(first)).isZero();
    }

    private long publishedAgentEvent(String slug, long source, long run, String externalId) {
        long eventId = insertFixtureEvent(cityId("SP", "sao-paulo"), categoryId("shows"), null, slug, "Evento " + slug);
        jdbcTemplate.update("UPDATE event SET status = 'PUBLISHED', origin = 'AGENT' WHERE id = ?", eventId);
        jdbcTemplate.update(
                "INSERT INTO event_source_link (event_id, source_id, external_id, last_run_id, created_at)"
                        + " VALUES (?, ?, ?, ?, '2026-03-01T10:02:00Z')",
                eventId, source, FIXTURE_SOURCE_PREFIX + externalId, run);
        return eventId;
    }

    private String statusOf(long eventId) {
        return jdbcTemplate.queryForObject("SELECT status FROM event WHERE id = ?", String.class, eventId);
    }

    private int versionOf(long eventId) {
        return jdbcTemplate.queryForObject("SELECT version FROM event WHERE id = ?", Integer.class, eventId);
    }
}
