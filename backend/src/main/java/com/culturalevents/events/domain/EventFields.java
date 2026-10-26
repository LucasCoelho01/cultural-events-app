package com.culturalevents.events.domain;

/** Campos editáveis de um evento (tudo exceto sessões, id, origem, versão e datas). */
public record EventFields(
        Long cityId,
        Long categoryId,
        Long venueId,
        String title,
        String slug,
        String description,
        EventStatus status,
        String ageRating,
        Integer priceMinCents,
        Integer priceMaxCents,
        boolean free,
        String ticketUrl,
        String imageUrl) {
}
