package com.culturalevents.locations.application;

import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.culturalevents.locations.domain.City;
import com.culturalevents.locations.domain.CityRepository;
import com.culturalevents.locations.domain.State;
import com.culturalevents.locations.domain.StateRepository;
import com.culturalevents.shared.error.NotFoundException;

@Service
@Transactional(readOnly = true)
public class LocationsService {

    private static final Sort CITY_ORDER = Sort.by("name", "id");

    private final StateRepository states;
    private final CityRepository cities;
    private final DefaultCityResolver defaultCityResolver;

    public LocationsService(StateRepository states, CityRepository cities, DefaultCityResolver defaultCityResolver) {
        this.states = states;
        this.cities = cities;
        this.defaultCityResolver = defaultCityResolver;
    }

    public List<State> listStates() {
        return states.findAllOrderedByName();
    }

    public Page<City> listCities(String uf, boolean active, int page, int size) {
        String normalizedUf = uf == null ? null : uf.toUpperCase(Locale.ROOT);
        return cities.search(normalizedUf, active, PageRequest.of(page, size, CITY_ORDER));
    }

    public City getCity(String uf, String slug) {
        return cities.findByUfAndSlug(uf.toUpperCase(Locale.ROOT), slug)
                .orElseThrow(() -> new NotFoundException("Cidade não encontrada: " + uf + "/" + slug));
    }

    public City getDefaultCity() {
        return defaultCityResolver.resolve();
    }
}
