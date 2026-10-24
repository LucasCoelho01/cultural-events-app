package com.culturalevents.locations.api;

import com.culturalevents.locations.domain.City;
import com.culturalevents.locations.domain.State;

final class LocationsMapper {

    private LocationsMapper() {
    }

    static com.culturalevents.shared.api.model.City toDto(City city) {
        return new com.culturalevents.shared.api.model.City(
                city.getId(),
                city.getIbgeCode(),
                city.getStateUf(),
                city.getName(),
                city.getSlug(),
                city.getTimezone(),
                city.isActive())
                .latitude(city.getLatitude())
                .longitude(city.getLongitude());
    }

    static com.culturalevents.shared.api.model.State toDto(State state) {
        return new com.culturalevents.shared.api.model.State(state.getUf(), state.getName());
    }
}
