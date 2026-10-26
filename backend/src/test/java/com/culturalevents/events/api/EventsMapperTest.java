package com.culturalevents.events.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.culturalevents.categories.application.CategoryRef;
import com.culturalevents.events.application.EventCommand;
import com.culturalevents.events.application.EventView;
import com.culturalevents.events.domain.Event;
import com.culturalevents.events.domain.EventFields;
import com.culturalevents.events.domain.EventStatus;
import com.culturalevents.events.domain.OccurrenceFields;
import com.culturalevents.events.domain.OccurrenceStatus;
import com.culturalevents.shared.api.model.EventInput;
import com.culturalevents.shared.api.model.EventUpdateInput;
import com.culturalevents.shared.api.model.OccurrenceInput;
import com.culturalevents.venues.application.VenueRef;

import static org.assertj.core.api.Assertions.assertThat;

class EventsMapperTest {

    private static final OffsetDateTime T1 = OffsetDateTime.parse("2030-02-01T20:00:00Z");
    private static final OffsetDateTime T2 = OffsetDateTime.parse("2030-03-01T20:00:00Z");

    @Test
    void entradaDeCriacaoUsaPadroesQuandoOsCamposVierem() {
        EventInput input = new EventInput(1L, 2L, "Título");

        EventCommand command = EventsMapper.toCommand(input);

        assertThat(command.status()).isEqualTo(EventStatus.DRAFT);
        assertThat(command.free()).isFalse();
        assertThat(command.occurrences()).isEmpty();
        assertThat(command.version()).isNull();
        assertThat(command.venueId()).isNull();
    }

    @Test
    void entradaDeCriacaoComStatusNuloEIsFreeNuloUsaPadroes() {
        EventInput input = new EventInput(1L, 2L, "Título");
        input.setStatus(null);
        input.setIsFree(null);
        input.setOccurrences(null);

        EventCommand command = EventsMapper.toCommand(input);

        assertThat(command.status()).isEqualTo(EventStatus.DRAFT);
        assertThat(command.free()).isFalse();
        assertThat(command.occurrences()).isEmpty();
    }

    @Test
    void entradaDeCriacaoCompletaVirComandoComSessoes() {
        UUID venue = UUID.randomUUID();
        EventInput input = new EventInput(1L, 2L, "Título")
                .venueId(venue).slug("titulo").description("d").status(com.culturalevents.shared.api.model.EventStatus.PUBLISHED)
                .ageRating("L").priceMinCents(10).priceMaxCents(20).isFree(false)
                .ticketUrl("https://t.org").imageUrl("https://i.org")
                .addOccurrencesItem(new OccurrenceInput(T1).endsAt(T1.plusHours(2))
                        .status(com.culturalevents.shared.api.model.OccurrenceStatus.SOLD_OUT))
                .addOccurrencesItem(new OccurrenceInput(T2));

        EventCommand command = EventsMapper.toCommand(input);

        assertThat(command.venueId()).isEqualTo(venue);
        assertThat(command.status()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(command.occurrences()).hasSize(2);
        assertThat(command.occurrences().get(0).status()).isEqualTo(OccurrenceStatus.SOLD_OUT);
        assertThat(command.occurrences().get(0).endsAt()).isEqualTo(T1.plusHours(2));
        assertThat(command.occurrences().get(1).status()).isEqualTo(OccurrenceStatus.SCHEDULED);
    }

    @Test
    void entradaDeSubstituicaoCarregaAVersion() {
        EventUpdateInput input = new EventUpdateInput(4, 1L, 2L, "Título",
                com.culturalevents.shared.api.model.EventStatus.ARCHIVED);

        EventCommand command = EventsMapper.toCommand(input);

        assertThat(command.version()).isEqualTo(4);
        assertThat(command.status()).isEqualTo(EventStatus.ARCHIVED);
    }

    @Test
    void dtoComLocalEIdsPublicosSemIdsInternos() {
        UUID publicId = UUID.randomUUID();
        UUID venuePublicId = UUID.randomUUID();
        Event event = event(publicId, 9L, List.of(new OccurrenceFields(T1, T1.plusHours(1), OccurrenceStatus.SOLD_OUT)));
        EventView view = new EventView(event, new CategoryRef(2L, "shows", "Shows"),
                new VenueRef(9L, venuePublicId, 1L, "Sala", "sala"));

        var dto = EventsMapper.toDto(view);

        assertThat(dto.getId()).isEqualTo(publicId);
        assertThat(dto.getCategory().getId()).isEqualTo(2L);
        assertThat(dto.getCategory().getSlug()).isEqualTo("shows");
        assertThat(dto.getVenue().getId()).as("o id do local é o público").isEqualTo(venuePublicId);
        assertThat(dto.getVenue().getName()).isEqualTo("Sala");
        assertThat(dto.getOccurrences()).hasSize(1);
        assertThat(dto.getOccurrences().getFirst().getEndsAt()).isEqualTo(T1.plusHours(1));
        assertThat(dto.getOccurrences().getFirst().getStatus())
                .isEqualTo(com.culturalevents.shared.api.model.OccurrenceStatus.SOLD_OUT);
        assertThat(dto.getOrigin()).isEqualTo(com.culturalevents.shared.api.model.Event.OriginEnum.MANUAL);
        assertThat(dto.getVersion()).isEqualTo(3);
        assertThat(dto.getPriceMinCents()).isEqualTo(100);
    }

    @Test
    void dtoSemLocalNemSessoesTemVenueNuloEListaVazia() {
        Event event = event(UUID.randomUUID(), null, List.of());
        EventView view = new EventView(event, new CategoryRef(2L, "shows", "Shows"), null);

        var dto = EventsMapper.toDto(view);

        assertThat(dto.getVenue()).isNull();
        assertThat(dto.getOccurrences()).isEmpty();
        assertThat(dto.getDescription()).isNull();
    }

    private static Event event(UUID publicId, Long venueId, List<OccurrenceFields> occurrences) {
        Event event = new Event(new EventFields(1L, 2L, venueId, "Título", "titulo", null, EventStatus.DRAFT, null,
                100, 200, false, null, null), occurrences);
        ReflectionTestUtils.setField(event, "publicId", publicId);
        ReflectionTestUtils.setField(event, "createdAt", OffsetDateTime.now());
        ReflectionTestUtils.setField(event, "updatedAt", OffsetDateTime.now());
        ReflectionTestUtils.setField(event, "version", 3);
        return event;
    }
}
