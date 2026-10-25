package com.culturalevents.venues.api;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.culturalevents.shared.api.VenuesApi;
import com.culturalevents.shared.api.model.VenueInput;
import com.culturalevents.shared.api.model.VenuePage;
import com.culturalevents.shared.pagination.PageMetadataMapper;
import com.culturalevents.venues.application.VenuesService;
import com.culturalevents.venues.domain.Venue;

@RestController
class VenuesController implements VenuesApi {

    private final VenuesService service;

    VenuesController(VenuesService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<VenuePage> listVenues(String city, String neighborhood, Integer page, Integer size) {
        Page<Venue> result = service.list(city, neighborhood, page, size);
        VenuePage body = new VenuePage(
                result.getContent().stream().map(VenuesMapper::toDto).toList(),
                PageMetadataMapper.from(result));
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<com.culturalevents.shared.api.model.Venue> createVenue(VenueInput venueInput) {
        Venue venue = service.create(VenuesMapper.toCommand(venueInput));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(venue.getPublicId()).toUri();
        return ResponseEntity.created(location).body(VenuesMapper.toDto(venue));
    }

    @Override
    public ResponseEntity<com.culturalevents.shared.api.model.Venue> getVenue(UUID id) {
        return ResponseEntity.ok(VenuesMapper.toDto(service.get(id)));
    }

    @Override
    public ResponseEntity<com.culturalevents.shared.api.model.Venue> replaceVenue(UUID id, VenueInput venueInput) {
        return ResponseEntity.ok(VenuesMapper.toDto(service.replace(id, VenuesMapper.toCommand(venueInput))));
    }

    @Override
    public ResponseEntity<Void> deleteVenue(UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
