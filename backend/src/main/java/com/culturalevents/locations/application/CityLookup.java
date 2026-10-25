package com.culturalevents.locations.application;

import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.culturalevents.locations.domain.CityRepository;

/** Caso de uso de consulta de cidade para outras capabilities, sem expor as classes de domínio de locations. */
@Service
@Transactional(readOnly = true)
public class CityLookup {

    private final CityRepository cities;
    private final DefaultCityResolver defaultCityResolver;

    public CityLookup(CityRepository cities, DefaultCityResolver defaultCityResolver) {
        this.cities = cities;
        this.defaultCityResolver = defaultCityResolver;
    }

    public Optional<CityRef> findById(long id) {
        return cities.findById(id).map(city -> new CityRef(city.getId(), city.isActive()));
    }

    public Optional<Long> findIdByUfAndSlug(String uf, String slug) {
        return cities.findByUfAndSlug(uf.toUpperCase(Locale.ROOT), slug).map(city -> city.getId());
    }

    /** @throws DefaultCityMisconfiguredException se {@code app.default-city} não resolve para uma cidade ativa */
    public long defaultCityId() {
        return defaultCityResolver.resolve().getId();
    }
}
