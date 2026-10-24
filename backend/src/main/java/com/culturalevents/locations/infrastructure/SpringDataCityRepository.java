package com.culturalevents.locations.infrastructure;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.culturalevents.locations.domain.City;

interface SpringDataCityRepository extends JpaRepository<City, Long> {

    Page<City> findByActive(boolean active, Pageable pageable);

    Page<City> findByStateUfAndActive(String stateUf, boolean active, Pageable pageable);

    Optional<City> findByStateUfAndSlug(String stateUf, String slug);
}
