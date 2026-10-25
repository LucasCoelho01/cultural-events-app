package com.culturalevents.locations.infrastructure;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.culturalevents.locations.domain.City;
import com.culturalevents.locations.domain.CityRepository;

@Repository
class CityRepositoryAdapter implements CityRepository {

    private final SpringDataCityRepository delegate;

    CityRepositoryAdapter(SpringDataCityRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Page<City> search(String uf, boolean active, Pageable pageable) {
        return uf == null
                ? delegate.findByActive(active, pageable)
                : delegate.findByStateUfAndActive(uf, active, pageable);
    }

    @Override
    public Optional<City> findById(Long id) {
        return delegate.findById(id);
    }

    @Override
    public Optional<City> findByUfAndSlug(String uf, String slug) {
        return delegate.findByStateUfAndSlug(uf, slug);
    }
}
