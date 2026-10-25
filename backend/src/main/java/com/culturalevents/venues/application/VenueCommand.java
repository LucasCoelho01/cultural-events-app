package com.culturalevents.venues.application;

import java.math.BigDecimal;

/** Dados de criação ou substituição de um local; {@code slug} nulo ou em branco é gerado a partir de {@code name}. */
public record VenueCommand(
        Long cityId,
        String name,
        String slug,
        String addressLine,
        String neighborhood,
        String postalCode,
        BigDecimal latitude,
        BigDecimal longitude,
        String websiteUrl) {
}
