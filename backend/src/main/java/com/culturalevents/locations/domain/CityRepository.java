package com.culturalevents.locations.domain;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CityRepository {

    /** @param uf sigla em maiúsculas, ou {@code null} para não filtrar por UF */
    Page<City> search(String uf, boolean active, Pageable pageable);

    Optional<City> findById(Long id);

    Optional<City> findByUfAndSlug(String uf, String slug);
}
