package com.culturalevents.events.api;

import java.util.List;

import com.culturalevents.events.application.EventCommand;
import com.culturalevents.events.application.EventView;
import com.culturalevents.events.application.OccurrenceCommand;
import com.culturalevents.events.domain.Event;
import com.culturalevents.events.domain.EventStatus;
import com.culturalevents.events.domain.Occurrence;
import com.culturalevents.events.domain.OccurrenceStatus;
import com.culturalevents.shared.api.model.CategoryRef;
import com.culturalevents.shared.api.model.EventInput;
import com.culturalevents.shared.api.model.EventUpdateInput;
import com.culturalevents.shared.api.model.OccurrenceInput;
import com.culturalevents.shared.api.model.VenueRef;

final class EventsMapper {

    private EventsMapper() {
    }

    static com.culturalevents.shared.api.model.Event toDto(EventView view) {
        Event event = view.event();
        return new com.culturalevents.shared.api.model.Event(
                event.getPublicId(),
                event.getCityId(),
                event.getTitle(),
                event.getSlug(),
                com.culturalevents.shared.api.model.EventStatus.valueOf(event.getStatus().name()),
                event.isFree(),
                com.culturalevents.shared.api.model.Event.OriginEnum.valueOf(event.getOrigin().name()),
                event.getVersion(),
                event.getCreatedAt(),
                event.getUpdatedAt(),
                new CategoryRef(view.category().id(), view.category().slug(), view.category().name()),
                event.getOccurrences().stream().map(EventsMapper::toDto).toList())
                .description(event.getDescription())
                .ageRating(event.getAgeRating())
                .priceMinCents(event.getPriceMinCents())
                .priceMaxCents(event.getPriceMaxCents())
                .ticketUrl(event.getTicketUrl())
                .imageUrl(event.getImageUrl())
                .venue(view.venue() == null
                        ? null
                        : new VenueRef(view.venue().publicId(), view.venue().name(), view.venue().slug()));
    }

    private static com.culturalevents.shared.api.model.Occurrence toDto(Occurrence occurrence) {
        return new com.culturalevents.shared.api.model.Occurrence(
                occurrence.getStartsAt(),
                com.culturalevents.shared.api.model.OccurrenceStatus.valueOf(occurrence.getStatus().name()))
                .endsAt(occurrence.getEndsAt());
    }

    static EventCommand toCommand(EventInput input) {
        return new EventCommand(
                input.getCityId(),
                input.getCategoryId(),
                input.getVenueId(),
                input.getTitle(),
                input.getSlug(),
                input.getDescription(),
                input.getStatus() == null ? EventStatus.DRAFT : EventStatus.valueOf(input.getStatus().name()),
                input.getAgeRating(),
                input.getPriceMinCents(),
                input.getPriceMaxCents(),
                Boolean.TRUE.equals(input.getIsFree()),
                input.getTicketUrl(),
                input.getImageUrl(),
                toCommands(input.getOccurrences()),
                null);
    }

    static EventCommand toCommand(EventUpdateInput input) {
        return new EventCommand(
                input.getCityId(),
                input.getCategoryId(),
                input.getVenueId(),
                input.getTitle(),
                input.getSlug(),
                input.getDescription(),
                EventStatus.valueOf(input.getStatus().name()),
                input.getAgeRating(),
                input.getPriceMinCents(),
                input.getPriceMaxCents(),
                Boolean.TRUE.equals(input.getIsFree()),
                input.getTicketUrl(),
                input.getImageUrl(),
                toCommands(input.getOccurrences()),
                input.getVersion());
    }

    private static List<OccurrenceCommand> toCommands(List<OccurrenceInput> occurrences) {
        if (occurrences == null) {
            return List.of();
        }
        return occurrences.stream()
                .map(o -> new OccurrenceCommand(
                        o.getStartsAt(),
                        o.getEndsAt(),
                        o.getStatus() == null ? OccurrenceStatus.SCHEDULED : OccurrenceStatus.valueOf(o.getStatus().name())))
                .toList();
    }
}
