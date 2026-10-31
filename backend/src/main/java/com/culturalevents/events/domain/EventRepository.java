package com.culturalevents.events.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface EventRepository {

    /** Ordenado pela próxima sessão a partir de {@code filter.reference()}, depois sem sessão futura, depois {@code id}. */
    Page<Event> search(EventFilter filter, Pageable pageable);

    Optional<Event> findByPublicId(UUID publicId);

    /** Eventos pelos ids públicos, ordenados por {@code id}; ids sem evento são ignorados. */
    List<Event> findAllByPublicIds(Collection<UUID> publicIds);

    Optional<Event> findByCityIdAndSlug(long cityId, String slug);

    /** Grava um evento novo e relê os valores gerados pelo banco (slug, datas, id público). */
    Event create(Event event);

    /** Grava as alterações forçando o incremento de {@code version} e relê os valores gerados pelo banco. */
    Event update(Event event);

    void deleteAndFlush(Event event);
}
