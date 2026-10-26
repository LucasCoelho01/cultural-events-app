package com.culturalevents.events.application;

import com.culturalevents.categories.application.CategoryRef;
import com.culturalevents.events.domain.Event;
import com.culturalevents.venues.application.VenueRef;

/** Evento com a categoria e o local já resolvidos, montado dentro da transação ({@code venue} pode ser nulo). */
public record EventView(Event event, CategoryRef category, VenueRef venue) {
}
