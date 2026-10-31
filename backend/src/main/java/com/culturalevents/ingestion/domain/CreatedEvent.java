package com.culturalevents.ingestion.domain;

import java.util.UUID;

/** Evento criado por uma execução; {@code status} é o nome do status do evento. */
public record CreatedEvent(UUID id, String title, String status) {
}
