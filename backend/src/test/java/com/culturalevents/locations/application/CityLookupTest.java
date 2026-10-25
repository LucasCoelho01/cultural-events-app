package com.culturalevents.locations.application;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.culturalevents.locations.domain.City;
import com.culturalevents.locations.domain.CityRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CityLookupTest {

    private final CityRepository cities = mock(CityRepository.class);
    private final DefaultCityResolver resolver = mock(DefaultCityResolver.class);
    private final CityLookup lookup = new CityLookup(cities, resolver);

    @Test
    void cidadeAtivaPorId() {
        City city = city(7L, true);
        when(cities.findById(7L)).thenReturn(Optional.of(city));

        assertThat(lookup.findById(7L)).contains(new CityRef(7L, true));
    }

    @Test
    void cidadeInativaPorId() {
        City city = city(8L, false);
        when(cities.findById(8L)).thenReturn(Optional.of(city));

        assertThat(lookup.findById(8L)).contains(new CityRef(8L, false));
    }

    @Test
    void cidadeInexistentePorId() {
        when(cities.findById(9L)).thenReturn(Optional.empty());

        assertThat(lookup.findById(9L)).isEmpty();
    }

    @Test
    void ufEmMinusculasEBuscadaEmMaiusculas() {
        City city = city(3L, true);
        when(cities.findByUfAndSlug("XX", "cidade")).thenReturn(Optional.of(city));

        assertThat(lookup.findIdByUfAndSlug("xx", "cidade")).contains(3L);
    }

    @Test
    void ufESlugInexistentesRetornamVazio() {
        when(cities.findByUfAndSlug("XX", "nada")).thenReturn(Optional.empty());

        assertThat(lookup.findIdByUfAndSlug("xx", "nada")).isEmpty();
    }

    @Test
    void cidadePadraoDelegaAoResolver() {
        City city = city(5L, true);
        when(resolver.resolve()).thenReturn(city);

        assertThat(lookup.defaultCityId()).isEqualTo(5L);
    }

    @Test
    void cidadePadraoMalConfiguradaPropagaOErro() {
        when(resolver.resolve()).thenThrow(new DefaultCityMisconfiguredException("formato uf/slug"));

        assertThatThrownBy(lookup::defaultCityId).isInstanceOf(DefaultCityMisconfiguredException.class);
    }

    private static City city(long id, boolean active) {
        City city = mock(City.class);
        when(city.getId()).thenReturn(id);
        when(city.isActive()).thenReturn(active);
        return city;
    }
}
