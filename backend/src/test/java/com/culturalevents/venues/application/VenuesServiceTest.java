package com.culturalevents.venues.application;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.culturalevents.locations.application.CityLookup;
import com.culturalevents.locations.application.CityRef;
import com.culturalevents.shared.error.ConflictException;
import com.culturalevents.shared.error.NotFoundException;
import com.culturalevents.venues.domain.Venue;
import com.culturalevents.venues.domain.VenueRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VenuesServiceTest {

    private final VenueRepository venues = mock(VenueRepository.class);
    private final CityLookup cities = mock(CityLookup.class);
    private final VenuesService service = new VenuesService(venues, cities);

    // --- list

    @Test
    void listagemSemCidadeUsaACidadePadraoOrdenandoPorNomeEId() {
        when(cities.defaultCityId()).thenReturn(10L);
        Page<Venue> page = new PageImpl<>(List.of());
        when(venues.search(eq(10L), eq(null), any(Pageable.class))).thenReturn(page);

        assertThat(service.list(null, null, 2, 5)).isSameAs(page);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(venues).search(eq(10L), eq(null), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageable.getValue().getSort().toString()).isEqualTo("name: ASC,id: ASC");
    }

    @Test
    void listagemComCidadeResolveUfESlugEPassaOBairro() {
        when(cities.findIdByUfAndSlug("xx", "cidade")).thenReturn(Optional.of(3L));
        when(venues.search(eq(3L), eq("Centro"), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.list("xx/cidade", "Centro", 0, 20);

        verify(venues).search(eq(3L), eq("Centro"), any(Pageable.class));
        verify(cities, never()).defaultCityId();
    }

    @Test
    void listagemComCidadeInexistenteLancaNotFound() {
        when(cities.findIdByUfAndSlug("xx", "nada")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.list("xx/nada", null, 0, 20)).isInstanceOf(NotFoundException.class);
    }

    // --- get / delete

    @Test
    void getDeLocalInexistenteLancaNotFound() {
        UUID id = UUID.randomUUID();
        when(venues.findByPublicId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteRemoveOLocalEncontrado() {
        UUID id = UUID.randomUUID();
        Venue venue = venue(1L);
        when(venues.findByPublicId(id)).thenReturn(Optional.of(venue));

        service.delete(id);

        verify(venues).deleteAndFlush(venue);
    }

    @Test
    void deleteDeLocalInexistenteLancaNotFound() {
        UUID id = UUID.randomUUID();
        when(venues.findByPublicId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(NotFoundException.class);
        verify(venues, never()).deleteAndFlush(any());
    }

    // --- create

    @Test
    void createComCidadeAtivaGrava() {
        when(cities.findById(1L)).thenReturn(Optional.of(new CityRef(1L, true)));
        when(venues.saveAndFlush(any(Venue.class))).thenAnswer(call -> call.getArgument(0));

        Venue created = service.create(command(1L, "Sala", "  "));

        assertThat(created.getName()).isEqualTo("Sala");
        assertThat(created.getSlug()).as("slug em branco vira nulo para o banco gerar").isNull();
    }

    @Test
    void createComCidadeInexistenteLancaValidacaoNoCampoCityId() {
        when(cities.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(command(1L, "Sala", null)))
                .isInstanceOfSatisfying(VenueValidationException.class,
                        ex -> assertThat(ex.getField()).isEqualTo("cityId"));
        verify(venues, never()).saveAndFlush(any());
    }

    @Test
    void createComCidadeInativaLancaValidacaoNoCampoCityId() {
        when(cities.findById(1L)).thenReturn(Optional.of(new CityRef(1L, false)));

        assertThatThrownBy(() -> service.create(command(1L, "Sala", null)))
                .isInstanceOfSatisfying(VenueValidationException.class, ex -> {
                    assertThat(ex.getField()).isEqualTo("cityId");
                    assertThat(ex.getMessage()).contains("inativa");
                });
    }

    @Test
    void slugDuplicadoVira409() {
        when(cities.findById(1L)).thenReturn(Optional.of(new CityRef(1L, true)));
        when(venues.saveAndFlush(any())).thenThrow(violation("23505"));

        assertThatThrownBy(() -> service.create(command(1L, "Sala", "sala")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void nomeQueNaoGeraSlugVira422NoCampoName() {
        when(cities.findById(1L)).thenReturn(Optional.of(new CityRef(1L, true)));
        when(venues.saveAndFlush(any())).thenThrow(violation("23514"));

        assertThatThrownBy(() -> service.create(command(1L, "!!!", null)))
                .isInstanceOfSatisfying(VenueValidationException.class,
                        ex -> assertThat(ex.getField()).isEqualTo("name"));
    }

    @Test
    void cidadeRemovidaEmCorridaVira422NoCampoCityId() {
        when(cities.findById(1L)).thenReturn(Optional.of(new CityRef(1L, true)));
        when(venues.saveAndFlush(any())).thenThrow(violation("23503"));

        assertThatThrownBy(() -> service.create(command(1L, "Sala", null)))
                .isInstanceOfSatisfying(VenueValidationException.class,
                        ex -> assertThat(ex.getField()).isEqualTo("cityId"));
    }

    @Test
    void violacaoDesconhecidaEPropagada() {
        when(cities.findById(1L)).thenReturn(Optional.of(new CityRef(1L, true)));
        when(venues.saveAndFlush(any())).thenThrow(violation("23502"));

        assertThatThrownBy(() -> service.create(command(1L, "Sala", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- replace

    @Test
    void replaceMantendoACidadeNaoRevalidaACidade() {
        UUID id = UUID.randomUUID();
        Venue venue = venue(1L);
        when(venues.findByPublicId(id)).thenReturn(Optional.of(venue));
        when(venues.saveAndFlush(venue)).thenReturn(venue);

        Venue result = service.replace(id, command(1L, "Novo Nome", "novo"));

        assertThat(result.getName()).isEqualTo("Novo Nome");
        assertThat(result.getSlug()).isEqualTo("novo");
        verify(cities, never()).findById(any(Long.class));
    }

    @Test
    void replaceTrocandoParaCidadeInativaLancaValidacao() {
        UUID id = UUID.randomUUID();
        when(venues.findByPublicId(id)).thenReturn(Optional.of(venue(1L)));
        when(cities.findById(2L)).thenReturn(Optional.of(new CityRef(2L, false)));

        assertThatThrownBy(() -> service.replace(id, command(2L, "Sala", null)))
                .isInstanceOfSatisfying(VenueValidationException.class,
                        ex -> assertThat(ex.getField()).isEqualTo("cityId"));
        verify(venues, never()).saveAndFlush(any());
    }

    @Test
    void replaceTrocandoParaCidadeAtivaGrava() {
        UUID id = UUID.randomUUID();
        Venue venue = venue(1L);
        when(venues.findByPublicId(id)).thenReturn(Optional.of(venue));
        when(cities.findById(2L)).thenReturn(Optional.of(new CityRef(2L, true)));
        when(venues.saveAndFlush(venue)).thenReturn(venue);

        assertThat(service.replace(id, command(2L, "Sala", null)).getCityId()).isEqualTo(2L);
    }

    @Test
    void replaceDeLocalInexistenteLancaNotFoundSemGravar() {
        UUID id = UUID.randomUUID();
        when(venues.findByPublicId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.replace(id, command(1L, "Sala", null)))
                .isInstanceOf(NotFoundException.class);
        verify(venues, never()).saveAndFlush(any());
    }

    @Test
    void replaceComSlugDeOutroLocalVira409() {
        UUID id = UUID.randomUUID();
        Venue venue = venue(1L);
        when(venues.findByPublicId(id)).thenReturn(Optional.of(venue));
        when(venues.saveAndFlush(venue)).thenThrow(violation("23505"));

        assertThatThrownBy(() -> service.replace(id, command(1L, "Sala", "ocupado")))
                .isInstanceOf(ConflictException.class);
    }

    private static VenueCommand command(Long cityId, String name, String slug) {
        return new VenueCommand(cityId, name, slug, null, null, null, null, null, null);
    }

    private static Venue venue(Long cityId) {
        return new Venue(cityId, "Antigo", "antigo", null, null, null, null, null, null);
    }

    private static DataIntegrityViolationException violation(String sqlState) {
        return new DataIntegrityViolationException("violação", new SQLException("x", sqlState));
    }
}
