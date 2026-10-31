package com.culturalevents.ingestion.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.culturalevents.events.application.EventsService;
import com.culturalevents.ingestion.domain.CreatedEvent;
import com.culturalevents.ingestion.domain.IngestionRun;
import com.culturalevents.ingestion.domain.IngestionRunRepository;
import com.culturalevents.ingestion.domain.IngestionRunStatus;
import com.culturalevents.ingestion.domain.Source;
import com.culturalevents.ingestion.domain.SourceRepository;
import com.culturalevents.shared.error.ConflictException;
import com.culturalevents.shared.error.NotFoundException;

@Service
@Transactional
public class IngestionService {

    private final IngestionRunRepository runs;
    private final SourceRepository sources;
    private final EventsService events;
    private final Clock clock;

    public IngestionService(IngestionRunRepository runs, SourceRepository sources, EventsService events,
            Clock clock) {
        this.runs = runs;
        this.sources = sources;
        this.events = events;
        this.clock = clock;
    }

    /** @param source code da fonte ou {@code null}; um code sem execuções devolve página vazia */
    @Transactional(readOnly = true)
    public Page<IngestionRun> list(String source, IngestionRunStatus status, int page, int size) {
        return runs.search(source, status, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public List<Source> listSources() {
        return sources.findAll();
    }

    @Transactional(readOnly = true)
    public IngestionRunDetail get(long id) {
        IngestionRun run = find(id);
        return new IngestionRunDetail(run, RunErrorParser.parse(run.getErrors()), createdEvents(run));
    }

    /**
     * Despublica os eventos {@code PUBLISHED} criados pela execução. Execução em andamento é recusada (409): ainda
     * pode estar criando eventos. Atômico: um conflito de versão em qualquer evento desfaz o lote inteiro (409).
     *
     * @return quantos eventos foram despublicados
     */
    public int unpublish(long id) {
        IngestionRun run = find(id);
        if (run.getStatus() == IngestionRunStatus.RUNNING) {
            throw new ConflictException("A execução ainda está em andamento; aguarde o término para despublicar.");
        }
        List<java.util.UUID> ids = createdEvents(run).stream().map(CreatedEvent::id).toList();
        return events.unpublish(ids);
    }

    private IngestionRun find(long id) {
        return runs.findById(id).orElseThrow(() -> new NotFoundException("Execução não encontrada: " + id));
    }

    private List<CreatedEvent> createdEvents(IngestionRun run) {
        OffsetDateTime until = run.getFinishedAt() != null ? run.getFinishedAt() : OffsetDateTime.now(clock);
        return runs.findCreatedEvents(run, until);
    }
}
