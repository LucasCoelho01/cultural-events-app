package com.culturalevents.events.domain;

import java.time.OffsetDateTime;

/** Campos editáveis de uma sessão. */
public record OccurrenceFields(OffsetDateTime startsAt, OffsetDateTime endsAt, OccurrenceStatus status) {
}
