package com.culturalevents.locations.application;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.culturalevents.locations.domain.City;
import com.culturalevents.locations.domain.CityRepository;
import com.culturalevents.locations.domain.State;
import com.culturalevents.locations.domain.StateRepository;
import com.culturalevents.shared.error.NotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocationsServiceTest {

    private final StateRepository states = mock(StateRepository.class);
    private final CityRepository cities = mock(CityRepository.class);
    private final DefaultCityResolver resolver = mock(DefaultCityResolver.class);
    private final LocationsService service = new LocationsService(states, cities, resolver);

    @Test
    void listaEstadosDoRepositorio() {
        List<State> expected = List.of(new State("XX", "Estado Teste"));
        when(states.findAllOrderedByName()).thenReturn(expected);

        assertThat(service.listStates()).isEqualTo(expected);
    }

    @Test
    void listagemNormalizaUfEOrdenaPorNomeEId() {
        Page<City> empty = new PageImpl<>(List.of());
        when(cities.search(eq("XX"), eq(false), any(Pageable.class))).thenReturn(empty);

        service.listCities("xx", false, 2, 5);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(cities).search(eq("XX"), eq(false), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageable.getValue().getSort().stream().map(order -> order.getProperty()).toList())
                .containsExactly("name", "id");
    }

    @Test
    void listagemSemUfNaoFiltraPorUf() {
        when(cities.search(eq(null), eq(true), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        assertThat(service.listCities(null, true, 0, 20).getTotalElements()).isZero();
    }

    @Test
    void buscaPorUfESlugNormalizaUf() {
        City city = mock(City.class);
        when(cities.findByUfAndSlug("XX", "cidade")).thenReturn(Optional.of(city));

        assertThat(service.getCity("xx", "cidade")).isSameAs(city);
    }

    @Test
    void cidadeInexistenteLancaNotFound() {
        when(cities.findByUfAndSlug("XX", "nada")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCity("XX", "nada")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void cidadePadraoDelegaAoResolver() {
        City city = mock(City.class);
        when(resolver.resolve()).thenReturn(city);

        assertThat(service.getDefaultCity()).isSameAs(city);
    }
}
