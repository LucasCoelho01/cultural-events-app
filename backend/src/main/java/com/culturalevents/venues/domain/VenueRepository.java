package com.culturalevents.venues.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface VenueRepository {

    /** @param neighborhood bairro (sem diferenciar maiúsculas), ou {@code null} para não filtrar */
    Page<Venue> search(long cityId, String neighborhood, Pageable pageable);

    Optional<Venue> findByPublicId(UUID publicId);

    List<Venue> findAllByIds(Collection<Long> ids);

    /** Grava e sincroniza com o banco, para que triggers e constraints se manifestem na chamada. */
    Venue saveAndFlush(Venue venue);

    void deleteAndFlush(Venue venue);
}
