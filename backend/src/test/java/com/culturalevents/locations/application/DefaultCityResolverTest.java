package com.culturalevents.locations.application;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.culturalevents.locations.domain.City;
import com.culturalevents.locations.domain.CityRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultCityResolverTest {

    private final CityRepository cities = mock(CityRepository.class);

    @Test
    void resolveCidadeAtivaNormalizandoAUfParaMaiusculas() {
        City city = mock(City.class);
        when(city.isActive()).thenReturn(true);
        when(cities.findByUfAndSlug("XX", "cidade-teste")).thenReturn(Optional.of(city));

        assertThat(new DefaultCityResolver(cities, "xx/cidade-teste").resolve()).isSameAs(city);
    }

    @Test
    void formatoInvalidoFalhaIndicandoOFormatoEsperado() {
        for (String configured : new String[] {"cidade-teste", "", "xxx/cidade", "xx/", "xx/a/b"}) {
            assertThatThrownBy(() -> new DefaultCityResolver(cities, configured).resolve())
                    .isInstanceOf(DefaultCityMisconfiguredException.class)
                    .hasMessageContaining("uf/slug");
        }
    }

    @Test
    void cidadeInexistenteFalha() {
        when(cities.findByUfAndSlug("XX", "nada")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new DefaultCityResolver(cities, "xx/nada").resolve())
                .isInstanceOf(DefaultCityMisconfiguredException.class)
                .hasMessageContaining("não existe");
    }

    @Test
    void cidadeInativaFalha() {
        City city = mock(City.class);
        when(city.isActive()).thenReturn(false);
        when(cities.findByUfAndSlug("XX", "inativa")).thenReturn(Optional.of(city));

        assertThatThrownBy(() -> new DefaultCityResolver(cities, "xx/inativa").resolve())
                .isInstanceOf(DefaultCityMisconfiguredException.class)
                .hasMessageContaining("inativa");
    }
}
