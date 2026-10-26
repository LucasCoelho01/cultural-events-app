package com.culturalevents.venues.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.culturalevents.venues.domain.Venue;
import com.culturalevents.venues.domain.VenueRepository;

@Repository
class VenueRepositoryAdapter implements VenueRepository {

    private final SpringDataVenueRepository delegate;
    private final EntityManager entityManager;

    VenueRepositoryAdapter(SpringDataVenueRepository delegate, EntityManager entityManager) {
        this.delegate = delegate;
        this.entityManager = entityManager;
    }

    @Override
    public Page<Venue> search(long cityId, String neighborhood, Pageable pageable) {
        return neighborhood == null
                ? delegate.findByCityId(cityId, pageable)
                : delegate.findByCityIdAndNeighborhoodIgnoreCase(cityId, neighborhood, pageable);
    }

    @Override
    public Optional<Venue> findByPublicId(UUID publicId) {
        return delegate.findByPublicId(publicId);
    }

    @Override
    public List<Venue> findAllByIds(Collection<Long> ids) {
        return delegate.findAllById(ids);
    }

    @Override
    public Venue saveAndFlush(Venue venue) {
        Venue saved = delegate.saveAndFlush(venue);
        // Os triggers do banco (slug gerado, updated_at) rodam no INSERT/UPDATE; relê a linha para refleti-los.
        entityManager.refresh(saved);
        return saved;
    }

    @Override
    public void deleteAndFlush(Venue venue) {
        delegate.delete(venue);
        delegate.flush();
    }
}
