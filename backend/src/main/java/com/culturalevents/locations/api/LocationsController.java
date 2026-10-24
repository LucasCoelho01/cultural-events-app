package com.culturalevents.locations.api;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.culturalevents.locations.application.LocationsService;
import com.culturalevents.locations.domain.City;
import com.culturalevents.shared.api.LocationsApi;
import com.culturalevents.shared.api.model.CityPage;
import com.culturalevents.shared.api.model.State;
import com.culturalevents.shared.pagination.PageMetadataMapper;

@RestController
class LocationsController implements LocationsApi {

    private final LocationsService service;

    LocationsController(LocationsService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<List<State>> listStates() {
        return ResponseEntity.ok(service.listStates().stream().map(LocationsMapper::toDto).toList());
    }

    @Override
    public ResponseEntity<CityPage> listCities(String uf, Boolean active, Integer page, Integer size) {
        Page<City> result = service.listCities(uf, active, page, size);
        CityPage body = new CityPage(
                result.getContent().stream().map(LocationsMapper::toDto).toList(),
                PageMetadataMapper.from(result));
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<com.culturalevents.shared.api.model.City> getCity(String uf, String slug) {
        return ResponseEntity.ok(LocationsMapper.toDto(service.getCity(uf, slug)));
    }

    @Override
    public ResponseEntity<com.culturalevents.shared.api.model.City> getDefaultCity() {
        return ResponseEntity.ok(LocationsMapper.toDto(service.getDefaultCity()));
    }
}
