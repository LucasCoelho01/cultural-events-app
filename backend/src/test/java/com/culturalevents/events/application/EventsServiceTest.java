package com.culturalevents.events.application;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import com.culturalevents.categories.application.CategoryLookup;
import com.culturalevents.categories.application.CategoryRef;
import com.culturalevents.events.domain.Event;
import com.culturalevents.events.domain.EventFields;
import com.culturalevents.events.domain.EventFilter;
import com.culturalevents.events.domain.EventRepository;
import com.culturalevents.events.domain.EventStatus;
import com.culturalevents.events.domain.OccurrenceFields;
import com.culturalevents.events.domain.OccurrenceStatus;
import com.culturalevents.locations.application.CityLookup;
import com.culturalevents.locations.application.CityRef;
import com.culturalevents.shared.error.ConflictException;
import com.culturalevents.shared.error.FieldValidationException;
import com.culturalevents.shared.error.FieldViolation;
import com.culturalevents.shared.error.NotFoundException;
import com.culturalevents.venues.application.VenueLookup;
import com.culturalevents.venues.application.VenueRef;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventsServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2030-06-01T12:00:00Z");
    private static final OffsetDateTime FUTURE = NOW.plusDays(10);
    private static final OffsetDateTime PAST = NOW.minusDays(10);

    private final EventRepository events = mock(EventRepository.class);
    private final CityLookup cities = mock(CityLookup.class);
    private final CategoryLookup categories = mock(CategoryLookup.class);
    private final VenueLookup venues = mock(VenueLookup.class);
    private final Clock clock = Clock.fixed(Instant.parse("2030-06-01T12:00:00Z"), ZoneOffset.UTC);
    private final EventsService service = new EventsService(events, cities, categories, venues, clock);

    private final UUID venuePublicId = UUID.randomUUID();

    // --- getPublished

    @Test
    void detalhePublicoDevolveEventoPublicadoDaCidade() {
        when(cities.findIdByUfAndSlug("ZZ", "cidade-teste")).thenReturn(Optional.of(1L));
        when(events.findByCityIdAndSlug(1L, "titulo")).thenReturn(Optional.of(published()));
        when(categories.findAllByIds(any())).thenReturn(List.of(new CategoryRef(2L, "shows", "Shows")));

        EventView view = service.getPublished("ZZ", "cidade-teste", "titulo");

        assertThat(view.event().getSlug()).isEqualTo("titulo");
        assertThat(view.category().name()).isEqualTo("Shows");
    }

    @Test
    void detalhePublicoRejeitaEventoNaoPublicadoComMesmaMensagemDeInexistente() {
        when(cities.findIdByUfAndSlug("ZZ", "cidade-teste")).thenReturn(Optional.of(1L));
        when(events.findByCityIdAndSlug(1L, "titulo")).thenReturn(Optional.of(existing(0)));
        when(events.findByCityIdAndSlug(1L, "outro")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPublished("ZZ", "cidade-teste", "titulo"))
                .isInstanceOf(NotFoundException.class).hasMessage("Evento não encontrado: titulo");
        assertThatThrownBy(() -> service.getPublished("ZZ", "cidade-teste", "outro"))
                .isInstanceOf(NotFoundException.class).hasMessage("Evento não encontrado: outro");
    }

    @Test
    void detalhePublicoRejeitaCidadeInexistenteSemBuscarEvento() {
        when(cities.findIdByUfAndSlug("ZZ", "nenhuma")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPublished("ZZ", "nenhuma", "titulo"))
                .isInstanceOf(NotFoundException.class).hasMessageContaining("Cidade não encontrada");
        verify(events, never()).findByCityIdAndSlug(any(Long.class), any());
    }

    // --- create: caminho feliz

    @Test
    void criaEventoValidoComCategoriaELocalNaVisao() {
        validReferences();
        when(venues.findByPublicId(venuePublicId)).thenReturn(Optional.of(new VenueRef(9L, venuePublicId, 1L, "Sala", "sala")));
        when(venues.findAllByIds(any())).thenReturn(List.of(new VenueRef(9L, venuePublicId, 1L, "Sala", "sala")));
        when(events.create(any(Event.class))).thenAnswer(call -> call.getArgument(0));

        EventView view = service.create(command(venuePublicId, EventStatus.PUBLISHED, "  ", FUTURE));

        assertThat(view.event().getVenueId()).isEqualTo(9L);
        assertThat(view.event().getSlug()).as("slug em branco vira nulo para o banco gerar").isNull();
        assertThat(view.event().getOccurrences()).hasSize(1);
        assertThat(view.category()).isEqualTo(new CategoryRef(2L, "shows", "Shows"));
        assertThat(view.venue().name()).isEqualTo("Sala");
    }

    @Test
    void criaEventoSemLocalNemSessoesComoRascunho() {
        validReferences();
        when(events.create(any(Event.class))).thenAnswer(call -> call.getArgument(0));

        EventView view = service.create(command(null, EventStatus.DRAFT, null));

        assertThat(view.venue()).isNull();
        assertThat(view.event().getOccurrences()).isEmpty();
    }

    // --- create: referências

    @Test
    void cidadeInexistenteGeraErroNoCampoCityId() {
        when(cities.findById(1L)).thenReturn(Optional.empty());
        when(categories.findById(2L)).thenReturn(Optional.of(new CategoryRef(2L, "shows", "Shows")));

        assertThatThrownBy(() -> service.create(command(null, EventStatus.DRAFT, null)))
                .isInstanceOfSatisfying(FieldValidationException.class, ex -> assertThat(fields(ex)).containsExactly("cityId"));
        verify(events, never()).create(any());
    }

    @Test
    void cidadeInativaGeraErroNoCampoCityId() {
        when(cities.findById(1L)).thenReturn(Optional.of(new CityRef(1L, false)));
        when(categories.findById(2L)).thenReturn(Optional.of(new CategoryRef(2L, "shows", "Shows")));

        assertThatThrownBy(() -> service.create(command(null, EventStatus.DRAFT, null)))
                .isInstanceOfSatisfying(FieldValidationException.class, ex -> {
                    assertThat(fields(ex)).containsExactly("cityId");
                    assertThat(ex.getViolations().getFirst().message()).contains("inativa");
                });
    }

    @Test
    void categoriaInexistenteGeraErroNoCampoCategoryId() {
        when(cities.findById(1L)).thenReturn(Optional.of(new CityRef(1L, true)));
        when(categories.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(command(null, EventStatus.DRAFT, null)))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        ex -> assertThat(fields(ex)).containsExactly("categoryId"));
    }

    @Test
    void localInexistenteGeraErroNoCampoVenueId() {
        validReferences();
        when(venues.findByPublicId(venuePublicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(command(venuePublicId, EventStatus.DRAFT, null)))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        ex -> assertThat(fields(ex)).containsExactly("venueId"));
    }

    @Test
    void localDeOutraCidadeGeraErroNoCampoVenueId() {
        validReferences();
        when(venues.findByPublicId(venuePublicId)).thenReturn(Optional.of(new VenueRef(9L, venuePublicId, 77L, "Sala", "sala")));

        assertThatThrownBy(() -> service.create(command(venuePublicId, EventStatus.DRAFT, null)))
                .isInstanceOfSatisfying(FieldValidationException.class, ex -> {
                    assertThat(fields(ex)).containsExactly("venueId");
                    assertThat(ex.getViolations().getFirst().message()).contains("outra cidade");
                });
    }

    @Test
    void errosDeReferenciaSaoAcumulados() {
        when(cities.findById(1L)).thenReturn(Optional.of(new CityRef(1L, true)));
        when(categories.findById(2L)).thenReturn(Optional.empty());
        when(venues.findByPublicId(venuePublicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(command(venuePublicId, EventStatus.DRAFT, null)))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        ex -> assertThat(fields(ex)).containsExactly("categoryId", "venueId"));
    }

    // --- create: preço e sessões

    @Test
    void eventoGratuitoComPrecoPositivoGeraErroEmIsFree() {
        validReferences();
        EventCommand command = with(command(null, EventStatus.DRAFT, null), true, 1000, null);

        assertThatThrownBy(() -> service.create(command)).isInstanceOfSatisfying(FieldValidationException.class,
                ex -> assertThat(fields(ex)).containsExactly("isFree"));
    }

    @Test
    void eventoGratuitoComPrecoZeroOuNuloEAceito() {
        validReferences();
        when(events.create(any(Event.class))).thenAnswer(call -> call.getArgument(0));

        service.create(with(command(null, EventStatus.DRAFT, null), true, 0, 0));
        service.create(with(command(null, EventStatus.DRAFT, null), true, null, null));
    }

    @Test
    void precoMaximoMenorQueOMinimoGeraErroEmPriceMaxCents() {
        validReferences();
        EventCommand command = with(command(null, EventStatus.DRAFT, null), false, 1000, 500);

        assertThatThrownBy(() -> service.create(command)).isInstanceOfSatisfying(FieldValidationException.class,
                ex -> assertThat(fields(ex)).containsExactly("priceMaxCents"));
    }

    @Test
    void sessaoComFimAnteriorAoInicioGeraErroIndexado() {
        validReferences();
        EventCommand command = commandWith(EventStatus.DRAFT, List.of(
                new OccurrenceCommand(FUTURE, null, OccurrenceStatus.SCHEDULED),
                new OccurrenceCommand(FUTURE.plusDays(1), FUTURE, OccurrenceStatus.SCHEDULED)));

        assertThatThrownBy(() -> service.create(command)).isInstanceOfSatisfying(FieldValidationException.class,
                ex -> assertThat(fields(ex)).containsExactly("occurrences[1].endsAt"));
    }

    @Test
    void sessoesComOMesmoInicioGeramErroNaRepetida() {
        validReferences();
        EventCommand command = commandWith(EventStatus.DRAFT, List.of(
                new OccurrenceCommand(FUTURE, null, OccurrenceStatus.SCHEDULED),
                new OccurrenceCommand(FUTURE.plusNanos(100), null, OccurrenceStatus.SCHEDULED)));

        assertThatThrownBy(() -> service.create(command)).isInstanceOfSatisfying(FieldValidationException.class,
                ex -> assertThat(fields(ex)).containsExactly("occurrences[1].startsAt"));
    }

    @Test
    void publicarSemSessaoGeraErroEmOccurrences() {
        validReferences();

        assertThatThrownBy(() -> service.create(commandWith(EventStatus.PUBLISHED, List.of())))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        ex -> assertThat(fields(ex)).containsExactly("occurrences"));
    }

    @Test
    void publicarSoComSessoesPassadasGeraErroEmOccurrences() {
        validReferences();

        assertThatThrownBy(() -> service.create(command(null, EventStatus.PUBLISHED, null, PAST)))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        ex -> assertThat(fields(ex)).containsExactly("occurrences"));
    }

    @Test
    void arquivarSoComSessoesPassadasEAceito() {
        validReferences();
        when(events.create(any(Event.class))).thenAnswer(call -> call.getArgument(0));

        EventView view = service.create(command(null, EventStatus.ARCHIVED, null, PAST));

        assertThat(view.event().getStatus()).isEqualTo(EventStatus.ARCHIVED);
    }

    // --- create: tradução de erros do banco por nome de constraint

    @Test
    void slugDuplicadoVira409() {
        validReferences();
        when(events.create(any())).thenThrow(violation("23505", "event_city_slug_key"));

        assertThatThrownBy(() -> service.create(command(null, EventStatus.DRAFT, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void unicidadeSemNomeConhecidoTambemVira409() {
        validReferences();
        when(events.create(any())).thenThrow(violation("23505", null));

        assertThatThrownBy(() -> service.create(command(null, EventStatus.DRAFT, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void sessaoDuplicadaNaCorridaVira422EmOccurrences() {
        validReferences();
        when(events.create(any())).thenThrow(violation("23505", "occurrence_event_starts_at_key"));

        assertThatThrownBy(() -> service.create(command(null, EventStatus.DRAFT, null)))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        ex -> assertThat(fields(ex)).containsExactly("occurrences"));
    }

    @Test
    void checksDoBancoSaoTraduzidosPeloNome() {
        validReferences();
        for (String[] pair : new String[][] {{"event_slug_not_empty", "title"}, {"event_venue_city_match", "venueId"},
                {"event_free_no_price", "isFree"}, {"event_price_range", "priceMaxCents"},
                {"event_price_min_nonneg", "priceMinCents"}, {"occurrence_time_order", "occurrences"}}) {
            doThrow(violation("23514", pair[0])).when(events).create(any());

            assertThatThrownBy(() -> service.create(command(null, EventStatus.DRAFT, null)))
                    .as(pair[0])
                    .isInstanceOfSatisfying(FieldValidationException.class,
                            ex -> assertThat(fields(ex)).containsExactly(pair[1]));
        }
    }

    @Test
    void chavesEstrangeirasSaoTraduzidasPeloNome() {
        validReferences();
        when(events.create(any())).thenThrow(violation("23503", "event_category_id_fkey"));

        assertThatThrownBy(() -> service.create(command(null, EventStatus.DRAFT, null)))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        ex -> assertThat(fields(ex)).containsExactly("categoryId"));
    }

    @Test
    void violacaoDesconhecidaEPropagada() {
        validReferences();
        when(events.create(any())).thenThrow(violation("23514", "outra_constraint"));

        assertThatThrownBy(() -> service.create(command(null, EventStatus.DRAFT, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- replace

    @Test
    void substituicaoComVersionAtualGravaEDevolveOEvento() {
        validReferences();
        Event event = existing(3);
        UUID id = UUID.randomUUID();
        when(events.findByPublicId(id)).thenReturn(Optional.of(event));
        when(events.update(event)).thenReturn(event);

        EventView view = service.replace(id, versioned(command(null, EventStatus.DRAFT, null), 3));

        assertThat(view.event().getTitle()).isEqualTo("Título");
        verify(events).update(event);
    }

    @Test
    void substituicaoComVersionDesatualizadaVira409SemValidarNemGravar() {
        Event event = existing(3);
        UUID id = UUID.randomUUID();
        when(events.findByPublicId(id)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service.replace(id, versioned(command(null, EventStatus.DRAFT, null), 2)))
                .isInstanceOf(ConflictException.class);
        verify(events, never()).update(any());
        verify(cities, never()).findById(any(Long.class));
    }

    @Test
    void substituicaoSemVersionVira409() {
        Event event = existing(3);
        UUID id = UUID.randomUUID();
        when(events.findByPublicId(id)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service.replace(id, versioned(command(null, EventStatus.DRAFT, null), null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void substituicaoDeEventoInexistenteVira404() {
        UUID id = UUID.randomUUID();
        when(events.findByPublicId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.replace(id, versioned(command(null, EventStatus.DRAFT, null), 0)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void substituicaoMantendoACidadeNaoRevalidaACidade() {
        when(categories.findById(2L)).thenReturn(Optional.of(new CategoryRef(2L, "shows", "Shows")));
        when(categories.findAllByIds(any())).thenReturn(List.of(new CategoryRef(2L, "shows", "Shows")));
        Event event = existing(0);
        UUID id = UUID.randomUUID();
        when(events.findByPublicId(id)).thenReturn(Optional.of(event));
        when(events.update(event)).thenReturn(event);

        service.replace(id, versioned(command(null, EventStatus.DRAFT, null), 0));

        verify(cities, never()).findById(any(Long.class));
    }

    @Test
    void substituicaoTrocandoParaCidadeInativaGeraErro() {
        when(cities.findById(5L)).thenReturn(Optional.of(new CityRef(5L, false)));
        when(categories.findById(2L)).thenReturn(Optional.of(new CategoryRef(2L, "shows", "Shows")));
        Event event = existing(0);
        UUID id = UUID.randomUUID();
        when(events.findByPublicId(id)).thenReturn(Optional.of(event));
        EventCommand other = new EventCommand(5L, 2L, null, "T", null, null, EventStatus.DRAFT, null, null, null,
                false, null, null, List.of(), 0);

        assertThatThrownBy(() -> service.replace(id, other)).isInstanceOfSatisfying(FieldValidationException.class,
                ex -> assertThat(fields(ex)).containsExactly("cityId"));
        verify(events, never()).update(any());
    }

    @Test
    void corridaDeDoisPutVira409() {
        validReferences();
        Event event = existing(0);
        UUID id = UUID.randomUUID();
        when(events.findByPublicId(id)).thenReturn(Optional.of(event));
        when(events.update(event)).thenThrow(new ObjectOptimisticLockingFailureException(Event.class, 1L));

        assertThatThrownBy(() -> service.replace(id, versioned(command(null, EventStatus.DRAFT, null), 0)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void substituicaoComSlugDeOutroEventoVira409() {
        validReferences();
        Event event = existing(0);
        UUID id = UUID.randomUUID();
        when(events.findByPublicId(id)).thenReturn(Optional.of(event));
        when(events.update(event)).thenThrow(violation("23505", "event_city_slug_key"));

        assertThatThrownBy(() -> service.replace(id, versioned(command(null, EventStatus.DRAFT, null), 0)))
                .isInstanceOf(ConflictException.class);
    }

    // --- get / delete

    @Test
    void getDeEventoInexistenteVira404() {
        UUID id = UUID.randomUUID();
        when(events.findByPublicId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteRemoveOEventoEncontradoEFalhaSeInexistente() {
        UUID id = UUID.randomUUID();
        Event event = existing(0);
        when(events.findByPublicId(id)).thenReturn(Optional.of(event));
        service.delete(id);
        verify(events).deleteAndFlush(event);

        UUID missing = UUID.randomUUID();
        when(events.findByPublicId(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(missing)).isInstanceOf(NotFoundException.class);
    }

    // --- list

    @Test
    void listagemSemCidadeUsaACidadePadraoEAReferenciaEAgora() {
        when(cities.defaultCityId()).thenReturn(10L);
        when(events.search(any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.list(null, null, null, null, null, 0, 20);

        EventFilter filter = captureFilter();
        assertThat(filter.cityId()).isEqualTo(10L);
        assertThat(filter.categoryIds()).isNull();
        assertThat(filter.reference()).isEqualTo(NOW);
    }

    @Test
    void referenciaEOMaiorEntreAgoraEFrom() {
        when(cities.defaultCityId()).thenReturn(10L);
        when(events.search(any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.list(null, null, FUTURE, null, null, 0, 20);
        assertThat(captureFilter().reference()).isEqualTo(FUTURE);
    }

    @Test
    void referenciaIgnoraFromNoPassado() {
        when(cities.defaultCityId()).thenReturn(10L);
        when(events.search(any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.list(null, null, PAST, null, null, 0, 20);
        assertThat(captureFilter().reference()).isEqualTo(NOW);
    }

    @Test
    void listagemResolveCidadeECategoriaComSubcategorias() {
        when(cities.findIdByUfAndSlug("xx", "cidade")).thenReturn(Optional.of(3L));
        when(categories.findIdsWithDescendantsBySlug("shows")).thenReturn(Optional.of(List.of(2L, 5L)));
        when(events.search(any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.list("xx/cidade", "shows", PAST, FUTURE, EventStatus.PUBLISHED, 1, 5);

        EventFilter filter = captureFilter();
        assertThat(filter.cityId()).isEqualTo(3L);
        assertThat(filter.categoryIds()).containsExactly(2L, 5L);
        assertThat(filter.status()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(filter.from()).isEqualTo(PAST);
        assertThat(filter.to()).isEqualTo(FUTURE);
    }

    @Test
    void listagemComCidadeOuCategoriaInexistenteVira404() {
        when(cities.findIdByUfAndSlug("xx", "nada")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.list("xx/nada", null, null, null, null, 0, 20))
                .isInstanceOf(NotFoundException.class);

        when(cities.defaultCityId()).thenReturn(10L);
        when(categories.findIdsWithDescendantsBySlug("nada")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.list(null, "nada", null, null, null, 0, 20))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listagemAnexaCategoriaELocalEmLote() {
        when(cities.defaultCityId()).thenReturn(10L);
        Event a = existing(0);
        ReflectionTestUtils.setField(a, "venueId", 9L);
        Event b = existing(0);
        Page<Event> page = new PageImpl<>(List.of(a, b), org.springframework.data.domain.PageRequest.of(0, 20), 2);
        when(events.search(any(), any())).thenReturn(page);
        when(categories.findAllByIds(any())).thenReturn(List.of(new CategoryRef(2L, "shows", "Shows")));
        when(venues.findAllByIds(any())).thenReturn(List.of(new VenueRef(9L, venuePublicId, 1L, "Sala", "sala")));

        Page<EventView> result = service.list(null, null, null, null, null, 0, 20);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent().get(0).venue().name()).isEqualTo("Sala");
        assertThat(result.getContent().get(1).venue()).isNull();
        verify(categories).findAllByIds(any());
        verify(venues).findAllByIds(any());
    }

    // --- helpers

    private EventFilter captureFilter() {
        ArgumentCaptor<EventFilter> captor = ArgumentCaptor.forClass(EventFilter.class);
        verify(events).search(captor.capture(), any(Pageable.class));
        return captor.getValue();
    }

    private void validReferences() {
        when(cities.findById(1L)).thenReturn(Optional.of(new CityRef(1L, true)));
        when(categories.findById(2L)).thenReturn(Optional.of(new CategoryRef(2L, "shows", "Shows")));
        when(categories.findAllByIds(any())).thenReturn(List.of(new CategoryRef(2L, "shows", "Shows")));
    }

    private static Event published() {
        return new Event(new EventFields(1L, 2L, null, "Título", "titulo", null, EventStatus.PUBLISHED, null, null,
                null, false, null, null), List.of(new OccurrenceFields(FUTURE, null, OccurrenceStatus.SCHEDULED)));
    }

    private static Event existing(int version) {
        Event event = new Event(new EventFields(1L, 2L, null, "Título", "titulo", null, EventStatus.DRAFT, null, null,
                null, false, null, null), List.of(new OccurrenceFields(FUTURE, null, OccurrenceStatus.SCHEDULED)));
        ReflectionTestUtils.setField(event, "version", version);
        return event;
    }

    private static EventCommand command(UUID venueId, EventStatus status, String slug, OffsetDateTime... startsAt) {
        List<OccurrenceCommand> occurrences = new java.util.ArrayList<>();
        for (OffsetDateTime start : startsAt) {
            occurrences.add(new OccurrenceCommand(start, null, OccurrenceStatus.SCHEDULED));
        }
        return new EventCommand(1L, 2L, venueId, "Título", slug, null, status, null, null, null, false, null, null,
                occurrences, null);
    }

    private static EventCommand command(UUID venueId, EventStatus status, String slug) {
        return command(venueId, status, slug, new OffsetDateTime[0]);
    }

    private static EventCommand commandWith(EventStatus status, List<OccurrenceCommand> occurrences) {
        return new EventCommand(1L, 2L, null, "Título", null, null, status, null, null, null, false, null, null,
                occurrences, null);
    }

    private static EventCommand with(EventCommand base, boolean free, Integer min, Integer max) {
        return new EventCommand(base.cityId(), base.categoryId(), base.venueId(), base.title(), base.slug(),
                base.description(), base.status(), base.ageRating(), min, max, free, base.ticketUrl(),
                base.imageUrl(), base.occurrences(), base.version());
    }

    private static EventCommand versioned(EventCommand base, Integer version) {
        return new EventCommand(base.cityId(), base.categoryId(), base.venueId(), base.title(), base.slug(),
                base.description(), base.status(), base.ageRating(), base.priceMinCents(), base.priceMaxCents(),
                base.free(), base.ticketUrl(), base.imageUrl(), base.occurrences(), version);
    }

    private static List<String> fields(FieldValidationException ex) {
        return ex.getViolations().stream().map(FieldViolation::field).toList();
    }

    private static DataIntegrityViolationException violation(String sqlState, String constraint) {
        return new DataIntegrityViolationException("violação",
                new ConstraintViolationException("x", new SQLException("x", sqlState), constraint));
    }
}
