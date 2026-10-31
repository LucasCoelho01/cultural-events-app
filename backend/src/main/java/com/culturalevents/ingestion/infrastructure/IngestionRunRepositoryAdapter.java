package com.culturalevents.ingestion.infrastructure;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.culturalevents.ingestion.domain.CreatedEvent;
import com.culturalevents.ingestion.domain.IngestionRun;
import com.culturalevents.ingestion.domain.IngestionRunRepository;
import com.culturalevents.ingestion.domain.IngestionRunStatus;

@Repository
class IngestionRunRepositoryAdapter implements IngestionRunRepository {

    private static final String CREATED_EVENTS_SQL = """
            select e.public_id, e.title, e.status
              from event_source_link l
              join event e on e.id = l.event_id
             where l.source_id = :sourceId
               and l.event_id is not null
               and l.created_at >= :startedAt
               and l.created_at <= :until
               and e.origin = 'AGENT'
               and l.id = (select min(o.id) from event_source_link o where o.event_id = l.event_id)
             order by l.id
            """;

    private final EntityManager entityManager;

    IngestionRunRepositoryAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Page<IngestionRun> search(String sourceCode, IngestionRunStatus status, Pageable pageable) {
        List<String> clauses = new ArrayList<>();
        if (sourceCode != null) {
            clauses.add("s.code = :source");
        }
        if (status != null) {
            clauses.add("r.status = :status");
        }
        String where = clauses.isEmpty() ? "" : " where " + String.join(" and ", clauses);

        TypedQuery<Long> count = entityManager
                .createQuery("select count(r) from IngestionRun r join r.source s" + where, Long.class);
        bind(count, sourceCode, status);
        long total = count.getSingleResult();

        TypedQuery<IngestionRun> query = entityManager.createQuery(
                "select r from IngestionRun r join fetch r.source s" + where
                        + " order by r.startedAt desc, r.id desc",
                IngestionRun.class);
        bind(query, sourceCode, status);
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());
        return new PageImpl<>(query.getResultList(), pageable, total);
    }

    private static void bind(TypedQuery<?> query, String sourceCode, IngestionRunStatus status) {
        if (sourceCode != null) {
            query.setParameter("source", sourceCode);
        }
        if (status != null) {
            query.setParameter("status", status);
        }
    }

    @Override
    public Optional<IngestionRun> findById(long id) {
        return entityManager
                .createQuery("select r from IngestionRun r join fetch r.source where r.id = :id", IngestionRun.class)
                .setParameter("id", id)
                .getResultStream()
                .findFirst();
    }

    @Override
    public List<CreatedEvent> findCreatedEvents(IngestionRun run, OffsetDateTime until) {
        List<?> rows = entityManager.createNativeQuery(CREATED_EVENTS_SQL)
                .setParameter("sourceId", run.getSource().getId())
                .setParameter("startedAt", run.getStartedAt())
                .setParameter("until", until)
                .getResultList();
        return rows.stream()
                .map(row -> (Object[]) row)
                .map(row -> new CreatedEvent((UUID) row[0], (String) row[1], (String) row[2]))
                .toList();
    }
}
