package com.culturalevents.ingestion.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IngestionRunRepository {

    /** Ordenado por {@code started_at} e {@code id} decrescentes; filtros {@code null} não restringem. */
    Page<IngestionRun> search(String sourceCode, IngestionRunStatus status, Pageable pageable);

    Optional<IngestionRun> findById(long id);

    /**
     * Eventos do agente criados pela execução: vínculo da fonte com {@code created_at} em
     * [{@code started_at}, {@code until}], não lápide e o mais antigo do evento (descarta a deduplicação).
     */
    List<CreatedEvent> findCreatedEvents(IngestionRun run, OffsetDateTime until);
}
