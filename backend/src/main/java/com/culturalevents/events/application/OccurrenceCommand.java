package com.culturalevents.events.application;

import java.time.OffsetDateTime;

import com.culturalevents.events.domain.OccurrenceStatus;

public record OccurrenceCommand(OffsetDateTime startsAt, OffsetDateTime endsAt, OccurrenceStatus status) {
}
