package com.culturalevents.archfixture.venues;

import com.culturalevents.locations.domain.City;

/**
 * Classe de fixture que viola deliberadamente a regra de isolamento de venues (dependendo de
 * locations.domain), usada apenas para provar que a {@code ArchRule} correspondente de fato
 * rejeita essa violação. Nunca deve existir em {@code src/main}.
 */
public class ViolatingVenuesClass {

    public Class<?> cityType() {
        return City.class;
    }
}
