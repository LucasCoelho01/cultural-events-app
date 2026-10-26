package com.culturalevents.events;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import com.culturalevents.AbstractPostgresIntegrationTest;
import com.culturalevents.events.domain.Event;
import com.culturalevents.events.domain.EventFields;
import com.culturalevents.events.domain.EventFilter;
import com.culturalevents.events.domain.EventRepository;
import com.culturalevents.events.domain.EventStatus;
import com.culturalevents.events.domain.Occurrence;
import com.culturalevents.events.domain.OccurrenceFields;
import com.culturalevents.events.domain.OccurrenceStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Roda em transação (revertida ao final). Garante que as entidades batem com o V005 sob ddl-auto=validate e que o
 * adaptador grava, relê o que o banco gera, filtra, ordena e pagina.
 */
@Transactional
class EventsContextIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final OffsetDateTime REF = OffsetDateTime.parse("2030-01-01T00:00:00Z");

    @Autowired
    private EventRepository events;

    @Autowired
    private EntityManager entityManager;

    private long sp;
    private long shows;

    @BeforeEach
    void ids() {
        sp = cityId("SP", "sao-paulo");
        shows = categoryId("shows");
    }

    @Test
    void contextoSobeComSchemaV005ECriaEventoSemSlugLendoOGeradoPeloBanco() {
        Event saved = events.create(new Event(fields("Teste Sem Slug", null, EventStatus.DRAFT), List.of(
                occurrence("2030-02-01T20:00:00Z", OccurrenceStatus.SCHEDULED))));

        assertThat(saved.getSlug()).isEqualTo("teste-sem-slug");
        assertThat(saved.getPublicId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getVersion()).isZero();
        assertThat(saved.getOccurrences()).hasSize(1);
        assertThat(events.findByPublicId(saved.getPublicId())).containsSame(saved);
        assertThat(events.findByPublicId(java.util.UUID.randomUUID())).isEmpty();
    }

    @Test
    void sessoesSaemOrdenadasPorStartsAt() {
        Event saved = events.create(new Event(fields("Teste Ordem", "teste-ordem", EventStatus.DRAFT), List.of(
                occurrence("2030-03-01T20:00:00Z", OccurrenceStatus.SCHEDULED),
                occurrence("2030-02-01T20:00:00Z", OccurrenceStatus.SCHEDULED))));
        entityManager.clear();

        Event reloaded = events.findByPublicId(saved.getPublicId()).orElseThrow();

        assertThat(reloaded.getOccurrences()).extracting(Occurrence::getStartsAt)
                .isSortedAccordingTo(java.util.Comparator.naturalOrder());
    }

    @Test
    void updateIncrementaAVersaoEAtualizaUpdatedAtMesmoSemMudancas() throws Exception {
        Event saved = events.create(new Event(fields("Teste Versao", "teste-versao", EventStatus.DRAFT), List.of()));
        OffsetDateTime before = saved.getUpdatedAt();
        Thread.sleep(20);

        Event updated = events.update(saved);

        assertThat(updated.getVersion()).isEqualTo(1);
        assertThat(updated.getUpdatedAt()).isAfter(before);
    }

    @Test
    void substituirMantemSessaoComOMesmoStartsAtEAtualizaStatus() {
        Event saved = events.create(new Event(fields("Teste Sync", "teste-sync", EventStatus.DRAFT), List.of(
                occurrence("2030-02-01T20:00:00Z", OccurrenceStatus.SCHEDULED),
                occurrence("2030-02-02T20:00:00Z", OccurrenceStatus.SCHEDULED))));

        saved.replaceWith(fields("Teste Sync", "teste-sync", EventStatus.DRAFT), List.of(
                occurrence("2030-02-01T20:00:00Z", OccurrenceStatus.SOLD_OUT),
                occurrence("2030-02-03T20:00:00Z", OccurrenceStatus.SCHEDULED)));
        Event updated = events.update(saved);

        assertThat(updated.getOccurrences()).extracting(o -> o.getStartsAt().toInstant().toString(), Occurrence::getStatus)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("2030-02-01T20:00:00Z", OccurrenceStatus.SOLD_OUT),
                        org.assertj.core.groups.Tuple.tuple("2030-02-03T20:00:00Z", OccurrenceStatus.SCHEDULED));
        assertThat(count("SELECT count(*) FROM occurrence WHERE event_id = " + saved.getId())).isEqualTo(2);
    }

    @Test
    void deleteRemoveOEventoESuasSessoes() {
        Event saved = events.create(new Event(fields("Teste Delete", "teste-delete", EventStatus.DRAFT), List.of(
                occurrence("2030-02-01T20:00:00Z", OccurrenceStatus.SCHEDULED))));
        long id = saved.getId();

        events.deleteAndFlush(saved);

        assertThat(count("SELECT count(*) FROM event WHERE id = " + id)).isZero();
        assertThat(count("SELECT count(*) FROM occurrence WHERE event_id = " + id)).isZero();
    }

    // --- busca

    @Test
    void buscaFiltraPorCidadeCategoriaEStatus() {
        insertFixtureCity(61, "RJ", "Cidade Busca", "cidade-busca", true);
        long rj = cityId("RJ", "cidade-busca");
        long teatro = categoryId("teatro");
        insertFixtureEvent(sp, shows, null, "a-show", "A Show");
        insertFixtureEvent(sp, teatro, null, "b-teatro", "B Teatro");
        insertFixtureEvent(rj, shows, null, "c-rj", "C RJ");
        jdbcTemplate.update("UPDATE event SET status = 'PUBLISHED' WHERE slug = ?", FIXTURE_EVENT_PREFIX + "b-teatro");

        assertThat(slugs(filter(sp, null, null, null, null))).containsExactly("a-show", "b-teatro");
        assertThat(slugs(filter(sp, List.of(teatro), null, null, null))).containsExactly("b-teatro");
        assertThat(slugs(filter(sp, List.of(shows, teatro), null, null, null))).containsExactly("a-show", "b-teatro");
        assertThat(slugs(filter(sp, null, null, null, EventStatus.PUBLISHED))).containsExactly("b-teatro");
        assertThat(slugs(filter(rj, null, null, null, null))).containsExactly("c-rj");
    }

    @Test
    void buscaPorIntervaloDeSessoesIncluiOEventoUmaVezSoEExcluiOsSemSessaoNoIntervalo() {
        long dentro = insertFixtureEvent(sp, shows, null, "dentro", "Dentro");
        insertFixtureOccurrence(dentro, "2030-06-10T20:00:00Z");
        insertFixtureOccurrence(dentro, "2030-06-11T20:00:00Z");
        insertFixtureOccurrence(dentro, "2031-01-01T20:00:00Z");
        long fora = insertFixtureEvent(sp, shows, null, "fora", "Fora");
        insertFixtureOccurrence(fora, "2032-01-01T20:00:00Z");
        insertFixtureEvent(sp, shows, null, "sem-sessao", "Sem sessão");

        OffsetDateTime from = OffsetDateTime.parse("2030-06-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2030-06-30T00:00:00Z");

        assertThat(slugs(filter(sp, null, from, to, null))).containsExactly("dentro");
        assertThat(slugs(filter(sp, null, from, null, null))).containsExactly("dentro", "fora");
        assertThat(slugs(filter(sp, null, null, to, null))).containsExactly("dentro");
        assertThat(slugs(filter(sp, null, OffsetDateTime.parse("2030-06-10T20:00:00Z"),
                OffsetDateTime.parse("2030-06-10T20:00:00Z"), null))).containsExactly("dentro");
    }

    @Test
    void intervaloInvertidoDevolveVazio() {
        long evento = insertFixtureEvent(sp, shows, null, "invertido", "Invertido");
        insertFixtureOccurrence(evento, "2030-06-10T20:00:00Z");

        Page<Event> page = events.search(new EventFilter(sp, null, OffsetDateTime.parse("2030-07-01T00:00:00Z"),
                OffsetDateTime.parse("2030-06-01T00:00:00Z"), null, REF), PageRequest.of(0, 20));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    void ordenaPelaProximaSessaoDepoisSemSessaoFuturaEPorFimPeloId() {
        long tarde = insertFixtureEvent(sp, shows, null, "tarde", "Tarde");
        insertFixtureOccurrence(tarde, "2030-09-01T20:00:00Z");
        long cedo = insertFixtureEvent(sp, shows, null, "cedo", "Cedo");
        insertFixtureOccurrence(cedo, "2030-03-01T20:00:00Z");
        insertFixtureOccurrence(cedo, "2030-12-01T20:00:00Z");
        long passadoRecente = insertFixtureEvent(sp, shows, null, "passado-recente", "Passado recente");
        insertFixtureOccurrence(passadoRecente, "2029-12-01T20:00:00Z");
        long passadoAntigo = insertFixtureEvent(sp, shows, null, "passado-antigo", "Passado antigo");
        insertFixtureOccurrence(passadoAntigo, "2020-01-01T20:00:00Z");
        insertFixtureEvent(sp, shows, null, "sem-sessao-1", "Sem sessão 1");
        insertFixtureEvent(sp, shows, null, "sem-sessao-2", "Sem sessão 2");

        assertThat(slugs(filter(sp, null, null, null, null)))
                .containsExactly("cedo", "tarde", "passado-recente", "passado-antigo", "sem-sessao-1", "sem-sessao-2");
    }

    @Test
    void referenciaMaisTardiaMudaAProximaSessao() {
        long a = insertFixtureEvent(sp, shows, null, "ref-a", "A");
        insertFixtureOccurrence(a, "2030-03-01T20:00:00Z");
        insertFixtureOccurrence(a, "2030-10-01T20:00:00Z");
        long b = insertFixtureEvent(sp, shows, null, "ref-b", "B");
        insertFixtureOccurrence(b, "2030-06-01T20:00:00Z");

        Page<Event> page = events.search(new EventFilter(sp, null, null, null, null,
                OffsetDateTime.parse("2030-05-01T00:00:00Z")), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Event::getSlug)
                .containsExactly(FIXTURE_EVENT_PREFIX + "ref-b", FIXTURE_EVENT_PREFIX + "ref-a");
    }

    @Test
    void paginacaoRespeitaPageESizeETotal() {
        insertFixtureEvent(sp, shows, null, "p1", "P1");
        insertFixtureEvent(sp, shows, null, "p2", "P2");
        insertFixtureEvent(sp, shows, null, "p3", "P3");

        Page<Event> page = events.search(new EventFilter(sp, null, null, null, null, REF), PageRequest.of(1, 1));

        assertThat(page.getContent()).extracting(Event::getSlug).containsExactly(FIXTURE_EVENT_PREFIX + "p2");
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(3);
    }

    // --- helpers

    private Page<Event> filter(long city, List<Long> categories, OffsetDateTime from, OffsetDateTime to,
            EventStatus status) {
        return events.search(new EventFilter(city, categories, from, to, status, REF), PageRequest.of(0, 50));
    }

    private static List<String> slugs(Page<Event> page) {
        return page.getContent().stream().map(e -> e.getSlug().replaceFirst("^" + FIXTURE_EVENT_PREFIX, "")).toList();
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private EventFields fields(String title, String slug, EventStatus status) {
        return new EventFields(sp, shows, null, title, slug == null ? null : slug, null, status, null, null, null,
                false, null, null);
    }

    private static OccurrenceFields occurrence(String startsAt, OccurrenceStatus status) {
        return new OccurrenceFields(OffsetDateTime.parse(startsAt), null, status);
    }
}
