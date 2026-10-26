package com.culturalevents.events.application;

import java.util.List;
import java.util.UUID;

import com.culturalevents.events.domain.EventStatus;

/**
 * Dados de criação ou substituição de um evento. {@code slug} nulo ou em branco é gerado de {@code title};
 * {@code version} só é usada na substituição.
 */
public record EventCommand(
        Long cityId,
        Long categoryId,
        UUID venueId,
        String title,
        String slug,
        String description,
        EventStatus status,
        String ageRating,
        Integer priceMinCents,
        Integer priceMaxCents,
        boolean free,
        String ticketUrl,
        String imageUrl,
        List<OccurrenceCommand> occurrences,
        Integer version) {
}
