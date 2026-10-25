package com.culturalevents.venues.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.culturalevents.venues.domain.Venue;

interface SpringDataVenueRepository extends JpaRepository<Venue, Long> {

    Page<Venue> findByCityId(Long cityId, Pageable pageable);

    Page<Venue> findByCityIdAndNeighborhoodIgnoreCase(Long cityId, String neighborhood, Pageable pageable);

    Optional<Venue> findByPublicId(UUID publicId);
}
