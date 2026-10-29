package com.culturalevents.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.culturalevents.AbstractPostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifica por SQL a coluna, o trigger e o índice de busca da migration V006. */
class EventsSearchSchemaIntegrationTest extends AbstractPostgresIntegrationTest {

    private long sp;
    private long shows;

    @BeforeEach
    void ids() {
        sp = cityId("SP", "sao-paulo");
        shows = categoryId("shows");
    }

    @Test
    void indiceGinCriado() {
        String definition = jdbcTemplate.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname = 'event_search_vector_idx'", String.class);
        assertThat(definition).containsIgnoringCase("USING gin").contains("search_vector");
    }

    @Test
    void buscaSemAcentoEncontraTextoAcentuado() {
        insertFixtureEvent(sp, shows, null, "musica", "Festival de Música");

        assertThat(matches("musica", "musica")).isTrue();
        assertThat(matches("musica", "música")).isTrue();
        assertThat(matches("musica", "MÚSICA")).isTrue();
    }

    @Test
    void singularEPluralCasam() {
        insertFixtureEvent(sp, shows, null, "plural", "Grandes shows de rock");
        insertFixtureEvent(sp, shows, null, "singular", "Um show de jazz");

        assertThat(matches("plural", "show")).isTrue();
        assertThat(matches("plural", "shows")).isTrue();
        assertThat(matches("singular", "shows")).isTrue();
        assertThat(matches("singular", "show")).isTrue();
    }

    @Test
    void categoriaLocalEDescricaoEntramNoVetor() {
        String venue = insertFixtureVenue(sp, "teatro-vetor", "Teatro Municipal", "Centro");
        long venueId = jdbcTemplate.queryForObject("SELECT id FROM venue WHERE slug = ?", Long.class, venue);
        long event = insertFixtureEvent(sp, shows, venueId, "campos", "Espetáculo");
        jdbcTemplate.update("UPDATE event SET description = ? WHERE id = ?", "Uma noite inesquecível", event);

        assertThat(matches("campos", "municipal")).isTrue();
        assertThat(matches("campos", "shows")).isTrue();
        assertThat(matches("campos", "inesquecivel")).isTrue();
        assertThat(matches("campos", "balé")).isFalse();
    }

    @Test
    void pesosTituloCategoriaEDescricao() {
        long event = insertFixtureEvent(sp, shows, null, "pesos", "Alfa");
        jdbcTemplate.update("UPDATE event SET description = ? WHERE id = ?", "Gama", event);

        String vector = jdbcTemplate.queryForObject("SELECT search_vector::text FROM event WHERE id = ?",
                String.class, event);
        assertThat(vector).contains("'alfa':1A").contains("'gam':3C").contains("'show':2B");
    }

    @Test
    void eventoSemDescricaoNemLocal() {
        insertFixtureEvent(sp, shows, null, "minimo", "Só título");

        assertThat(matches("minimo", "titulo")).isTrue();
    }

    @Test
    void vetorEnviadoPeloEscritorEDescartado() {
        jdbcTemplate.update("INSERT INTO event (city_id, category_id, title, slug, search_vector) "
                        + "VALUES (?, ?, 'Titulo Real', ?, to_tsvector('simple', 'enganoso'))",
                sp, shows, FIXTURE_EVENT_PREFIX + "forjado");

        assertThat(matches("forjado", "enganoso")).isFalse();
        assertThat(matches("forjado", "real")).isTrue();
    }

    @Test
    void eventoInseridoPorSqlDiretoAparece() {
        jdbcTemplate.update("INSERT INTO event (city_id, category_id, title, slug, origin, status) "
                        + "VALUES (?, ?, 'Orquestra Sinfônica', ?, 'AGENT', 'PUBLISHED')",
                sp, shows, FIXTURE_EVENT_PREFIX + "agente");

        assertThat(matches("agente", "sinfonica")).isTrue();
    }

    @Test
    void atualizarTituloRecalculaVetor() {
        long event = insertFixtureEvent(sp, shows, null, "titulo-novo", "Antigo");
        jdbcTemplate.update("UPDATE event SET title = 'Moderno' WHERE id = ?", event);

        assertThat(matches("titulo-novo", "antigo")).isFalse();
        assertThat(matches("titulo-novo", "moderno")).isTrue();
    }

    @Test
    void renomearLocalAtualizaOsEventos() {
        String venue = insertFixtureVenue(sp, "local-renomeado", "Casa Velha", "Centro");
        long venueId = jdbcTemplate.queryForObject("SELECT id FROM venue WHERE slug = ?", Long.class, venue);
        insertFixtureEvent(sp, shows, venueId, "no-local", "Concerto");

        jdbcTemplate.update("UPDATE venue SET name = 'Casa Nova' WHERE id = ?", venueId);

        assertThat(matches("no-local", "nova")).isTrue();
        assertThat(matches("no-local", "velha")).isFalse();
    }

    @Test
    void renomearLocalSemEventosConclui() {
        String venue = insertFixtureVenue(sp, "local-vazio", "Sem Eventos", "Centro");

        int updated = jdbcTemplate.update("UPDATE venue SET name = 'Ainda Sem Eventos' WHERE slug = ?", venue);

        assertThat(updated).isEqualTo(1);
    }

    @Test
    void renomearCategoriaNaoAtualizaOVetorAteRegravarOTitulo() {
        String category = insertFixtureCategory("renomeada", "Cinema Antigo", null, 90);
        long categoryId = categoryId(category);
        insertFixtureEvent(sp, categoryId, null, "na-categoria", "Sessão");

        jdbcTemplate.update("UPDATE category SET name = 'Cinema Moderno' WHERE id = ?", categoryId);
        assertThat(matches("na-categoria", "antigo")).isTrue();
        assertThat(matches("na-categoria", "moderno")).isFalse();

        jdbcTemplate.update("UPDATE event SET title = title WHERE category_id = ?", categoryId);
        assertThat(matches("na-categoria", "moderno")).isTrue();
        assertThat(matches("na-categoria", "antigo")).isFalse();
    }

    private boolean matches(String slug, String query) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM event WHERE slug = ? "
                        + "AND search_vector @@ websearch_to_tsquery('portuguese', event_search_text(?))",
                Integer.class, FIXTURE_EVENT_PREFIX + slug, query) > 0;
    }
}
