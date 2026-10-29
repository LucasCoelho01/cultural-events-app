package com.culturalevents.events.domain;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Filtros da listagem. {@code categoryIds}, {@code from}, {@code to} e {@code status} são opcionais ({@code null}).
 * {@code reference} é o instante a partir do qual se procura a "próxima sessão" para ordenar. {@code query} é o texto
 * da busca textual, já sem espaços nas pontas, ou {@code null} para listar sem busca (e sem ordenar por relevância).
 */
public record EventFilter(
        long cityId,
        List<Long> categoryIds,
        OffsetDateTime from,
        OffsetDateTime to,
        EventStatus status,
        OffsetDateTime reference,
        String query) {

    public EventFilter(long cityId, List<Long> categoryIds, OffsetDateTime from, OffsetDateTime to, EventStatus status,
            OffsetDateTime reference) {
        this(cityId, categoryIds, from, to, status, reference, null);
    }
}
