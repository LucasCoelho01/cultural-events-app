package com.culturalevents.venues.api;

import com.culturalevents.shared.api.model.VenueInput;
import com.culturalevents.venues.application.VenueCommand;
import com.culturalevents.venues.domain.Venue;

final class VenuesMapper {

    private VenuesMapper() {
    }

    static com.culturalevents.shared.api.model.Venue toDto(Venue venue) {
        return new com.culturalevents.shared.api.model.Venue(
                venue.getPublicId(),
                venue.getCityId(),
                venue.getName(),
                venue.getSlug(),
                venue.getCreatedAt(),
                venue.getUpdatedAt())
                .addressLine(venue.getAddressLine())
                .neighborhood(venue.getNeighborhood())
                .postalCode(venue.getPostalCode())
                .latitude(venue.getLatitude())
                .longitude(venue.getLongitude())
                .websiteUrl(venue.getWebsiteUrl());
    }

    static VenueCommand toCommand(VenueInput input) {
        return new VenueCommand(
                input.getCityId(),
                input.getName(),
                input.getSlug(),
                input.getAddressLine(),
                input.getNeighborhood(),
                input.getPostalCode(),
                input.getLatitude(),
                input.getLongitude(),
                input.getWebsiteUrl());
    }
}
