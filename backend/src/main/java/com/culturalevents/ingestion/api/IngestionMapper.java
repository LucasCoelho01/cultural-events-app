package com.culturalevents.ingestion.api;

import com.culturalevents.ingestion.application.RunError;
import com.culturalevents.ingestion.domain.CreatedEvent;
import com.culturalevents.ingestion.domain.IngestionRun;
import com.culturalevents.ingestion.domain.Source;
import com.culturalevents.shared.api.model.EventStatus;
import com.culturalevents.shared.api.model.IngestionCreatedEvent;
import com.culturalevents.shared.api.model.IngestionRunDetail;
import com.culturalevents.shared.api.model.IngestionRunError;
import com.culturalevents.shared.api.model.IngestionRunStatus;
import com.culturalevents.shared.api.model.IngestionRunSummary;
import com.culturalevents.shared.api.model.SourceRef;

final class IngestionMapper {

    private IngestionMapper() {
    }

    static SourceRef toDto(Source source) {
        return new SourceRef(source.getCode(), source.getName());
    }

    static IngestionRunSummary toSummary(IngestionRun run) {
        return new IngestionRunSummary(run.getId(), toDto(run.getSource()), run.getStartedAt(),
                IngestionRunStatus.valueOf(run.getStatus().name()), run.getCreated(), run.getUpdated(),
                run.getUnchanged(), run.getFailed())
                .finishedAt(run.getFinishedAt())
                .durationSeconds(run.getDurationSeconds());
    }

    static IngestionRunDetail toDetail(com.culturalevents.ingestion.application.IngestionRunDetail detail) {
        IngestionRun run = detail.run();
        return new IngestionRunDetail(run.getId(), toDto(run.getSource()), run.getStartedAt(),
                IngestionRunStatus.valueOf(run.getStatus().name()), run.getCreated(), run.getUpdated(),
                run.getUnchanged(), run.getFailed(),
                detail.errors().stream().map(IngestionMapper::toDto).toList(),
                detail.createdEvents().stream().map(IngestionMapper::toDto).toList())
                .finishedAt(run.getFinishedAt())
                .durationSeconds(run.getDurationSeconds());
    }

    private static IngestionRunError toDto(RunError error) {
        return new IngestionRunError(error.externalId(), error.detail()).code(error.code());
    }

    private static IngestionCreatedEvent toDto(CreatedEvent event) {
        return new IngestionCreatedEvent(event.id(), event.title(), EventStatus.valueOf(event.status()));
    }
}
