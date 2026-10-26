package com.culturalevents.venues.application;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.culturalevents.venues.domain.Venue;
import com.culturalevents.venues.domain.VenueRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VenueLookupTest {

    private final VenueRepository venues = mock(VenueRepository.class);
    private final VenueLookup lookup = new VenueLookup(venues);

    @Test
    void localPorPublicIdVirRefComACidade() {
        UUID publicId = UUID.randomUUID();
        Venue venue = venue(7L, publicId, 3L, "Sala", "sala");
        when(venues.findByPublicId(publicId)).thenReturn(Optional.of(venue));

        assertThat(lookup.findByPublicId(publicId)).contains(new VenueRef(7L, publicId, 3L, "Sala", "sala"));
    }

    @Test
    void localInexistenteRetornaVazio() {
        UUID publicId = UUID.randomUUID();
        when(venues.findByPublicId(publicId)).thenReturn(Optional.empty());

        assertThat(lookup.findByPublicId(publicId)).isEmpty();
    }

    @Test
    void varios() {
        UUID a = UUID.randomUUID();
        Venue venue = venue(7L, a, 3L, "Sala", "sala");
        when(venues.findAllByIds(Set.of(7L))).thenReturn(List.of(venue));

        assertThat(lookup.findAllByIds(Set.of(7L))).containsExactly(new VenueRef(7L, a, 3L, "Sala", "sala"));
    }

    @Test
    void listaVaziaDeIdsNaoConsultaORepositorio() {
        assertThat(lookup.findAllByIds(Set.of())).isEmpty();
        verify(venues, never()).findAllByIds(Set.of());
    }

    private static Venue venue(long id, UUID publicId, long cityId, String name, String slug) {
        Venue venue = mock(Venue.class);
        when(venue.getId()).thenReturn(id);
        when(venue.getPublicId()).thenReturn(publicId);
        when(venue.getCityId()).thenReturn(cityId);
        when(venue.getName()).thenReturn(name);
        when(venue.getSlug()).thenReturn(slug);
        return venue;
    }
}
