package com.culturalevents.events.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import com.culturalevents.events.domain.Event;
import com.culturalevents.events.domain.EventFilter;
import com.culturalevents.events.domain.EventRepository;

@Repository
class EventRepositoryAdapter implements EventRepository {

    private static final String ORDER_BY = " order by"
            + " (select min(o.startsAt) from Occurrence o where o.event = e and o.startsAt >= :reference) asc nulls last,"
            + " (select max(o.startsAt) from Occurrence o where o.event = e) desc nulls last,"
            + " e.id asc";

    private final EntityManager entityManager;

    EventRepositoryAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Page<Event> search(EventFilter filter, Pageable pageable) {
        String where = where(filter);

        TypedQuery<Long> count = entityManager.createQuery("select count(e) from Event e" + where, Long.class);
        bind(count, filter, false);
        long total = count.getSingleResult();

        TypedQuery<Event> query = entityManager.createQuery("select e from Event e" + where + ORDER_BY, Event.class);
        bind(query, filter, true);
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());
        return new PageImpl<>(query.getResultList(), pageable, total);
    }

    @Override
    public Optional<Event> findByPublicId(UUID publicId) {
        return entityManager.createQuery("select e from Event e where e.publicId = :publicId", Event.class)
                .setParameter("publicId", publicId)
                .getResultStream()
                .findFirst();
    }

    @Override
    public Optional<Event> findByCityIdAndSlug(long cityId, String slug) {
        return entityManager
                .createQuery("select e from Event e where e.cityId = :cityId and e.slug = :slug", Event.class)
                .setParameter("cityId", cityId)
                .setParameter("slug", slug)
                .getResultStream()
                .findFirst();
    }

    @Override
    public Event create(Event event) {
        entityManager.persist(event);
        entityManager.flush();
        entityManager.refresh(event);
        return event;
    }

    @Override
    public Event update(Event event) {
        int before = event.getVersion();
        entityManager.flush();
        if (event.getVersion() == before) {
            // Nada mudou na linha do evento (ou só as sessões mudaram): força o UPDATE que incrementa a versão e
            // dispara updated_at. Um UPDATE explícito age já (o lock OPTIMISTIC_FORCE_INCREMENT só incrementa no
            // commit e deixaria a versão devolvida desatualizada) e vale também para entidade recém-persistida.
            int updated = entityManager.createQuery(
                            "update Event e set e.version = e.version + 1 where e.id = :id and e.version = :version")
                    .setParameter("id", event.getId())
                    .setParameter("version", before)
                    .executeUpdate();
            if (updated == 0) {
                throw new ObjectOptimisticLockingFailureException(Event.class, event.getId());
            }
        }
        entityManager.refresh(event);
        return event;
    }

    @Override
    public void deleteAndFlush(Event event) {
        entityManager.remove(event);
        entityManager.flush();
    }

    private static String where(EventFilter filter) {
        List<String> clauses = new ArrayList<>();
        clauses.add("e.cityId = :cityId");
        if (filter.categoryIds() != null) {
            clauses.add("e.categoryId in :categoryIds");
        }
        if (filter.status() != null) {
            clauses.add("e.status = :status");
        }
        if (filter.from() != null || filter.to() != null) {
            StringBuilder exists = new StringBuilder("exists (select 1 from Occurrence x where x.event = e");
            if (filter.from() != null) {
                exists.append(" and x.startsAt >= :from");
            }
            if (filter.to() != null) {
                exists.append(" and x.startsAt <= :to");
            }
            clauses.add(exists.append(")").toString());
        }
        return " where " + String.join(" and ", clauses);
    }

    private static void bind(TypedQuery<?> query, EventFilter filter, boolean withReference) {
        query.setParameter("cityId", filter.cityId());
        if (filter.categoryIds() != null) {
            query.setParameter("categoryIds", filter.categoryIds());
        }
        if (filter.status() != null) {
            query.setParameter("status", filter.status());
        }
        if (filter.from() != null) {
            query.setParameter("from", filter.from());
        }
        if (filter.to() != null) {
            query.setParameter("to", filter.to());
        }
        if (withReference) {
            query.setParameter("reference", filter.reference());
        }
    }
}
