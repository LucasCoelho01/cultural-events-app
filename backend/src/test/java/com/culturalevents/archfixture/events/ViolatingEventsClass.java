package com.culturalevents.archfixture.events;

import com.culturalevents.venues.domain.Venue;

/**
 * Classe de fixture que viola deliberadamente a regra de isolamento de events (dependendo de
 * venues.domain), usada apenas para provar que a {@code ArchRule} correspondente de fato
 * rejeita essa violação. Nunca deve existir em {@code src/main}.
 */
public class ViolatingEventsClass {

    public Class<?> venueType() {
        return Venue.class;
    }
}
