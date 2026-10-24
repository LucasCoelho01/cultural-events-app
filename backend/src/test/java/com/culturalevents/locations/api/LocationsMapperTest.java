package com.culturalevents.locations.api;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.culturalevents.locations.domain.City;
import com.culturalevents.locations.domain.State;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocationsMapperTest {

    @Test
    void mapeiaTodosOsCamposDaCidade() {
        City city = cityWithCoordinates(new BigDecimal("-1.500000"), new BigDecimal("-2.250000"));

        var dto = LocationsMapper.toDto(city);

        assertThat(dto.getId()).isEqualTo(7L);
        assertThat(dto.getIbgeCode()).isEqualTo(1234567);
        assertThat(dto.getUf()).isEqualTo("XX");
        assertThat(dto.getName()).isEqualTo("Cidade Teste");
        assertThat(dto.getSlug()).isEqualTo("cidade-teste");
        assertThat(dto.getTimezone()).isEqualTo("America/Sao_Paulo");
        assertThat(dto.getLatitude()).isEqualByComparingTo("-1.5");
        assertThat(dto.getLongitude()).isEqualByComparingTo("-2.25");
        assertThat(dto.getActive()).isTrue();
    }

    @Test
    void coordenadasAusentesViramNulo() {
        var dto = LocationsMapper.toDto(cityWithCoordinates(null, null));

        assertThat(dto.getLatitude()).isNull();
        assertThat(dto.getLongitude()).isNull();
    }

    @Test
    void mapeiaEstado() {
        var dto = LocationsMapper.toDto(new State("XX", "Estado Teste"));

        assertThat(dto.getUf()).isEqualTo("XX");
        assertThat(dto.getName()).isEqualTo("Estado Teste");
    }

    private City cityWithCoordinates(BigDecimal latitude, BigDecimal longitude) {
        City city = mock(City.class);
        when(city.getId()).thenReturn(7L);
        when(city.getIbgeCode()).thenReturn(1234567);
        when(city.getStateUf()).thenReturn("XX");
        when(city.getName()).thenReturn("Cidade Teste");
        when(city.getSlug()).thenReturn("cidade-teste");
        when(city.getTimezone()).thenReturn("America/Sao_Paulo");
        when(city.getLatitude()).thenReturn(latitude);
        when(city.getLongitude()).thenReturn(longitude);
        when(city.isActive()).thenReturn(true);
        return city;
    }
}
