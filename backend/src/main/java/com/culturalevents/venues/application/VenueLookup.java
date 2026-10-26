package com.culturalevents.venues.application;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.culturalevents.venues.domain.Venue;
import com.culturalevents.venues.domain.VenueRepository;

/** Caso de uso de consulta de local para outras capabilities, sem expor as classes de domínio. */
@Service
@Transactional(readOnly = true)
public class VenueLookup {

    private final VenueRepository venues;

    public VenueLookup(VenueRepository venues) {
        this.venues = venues;
    }

    public Optional<VenueRef> findByPublicId(UUID publicId) {
        return venues.findByPublicId(publicId).map(VenueLookup::toRef);
    }

    public List<VenueRef> findAllByIds(Collection<Long> ids) {
        return ids.isEmpty() ? List.of() : venues.findAllByIds(ids).stream().map(VenueLookup::toRef).toList();
    }

    private static VenueRef toRef(Venue venue) {
        return new VenueRef(venue.getId(), venue.getPublicId(), venue.getCityId(), venue.getName(), venue.getSlug());
    }
}
