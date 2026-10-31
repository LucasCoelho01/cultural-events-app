package com.culturalevents.ingestion.application;

import java.util.List;

import com.culturalevents.ingestion.domain.CreatedEvent;
import com.culturalevents.ingestion.domain.IngestionRun;

/** Execução com seus erros interpretados e os eventos que ela criou. */
public record IngestionRunDetail(IngestionRun run, List<RunError> errors, List<CreatedEvent> createdEvents) {
}
