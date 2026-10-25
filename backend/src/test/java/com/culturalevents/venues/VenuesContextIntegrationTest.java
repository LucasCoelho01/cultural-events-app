package com.culturalevents.venues;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import com.culturalevents.AbstractPostgresIntegrationTest;
import com.culturalevents.venues.domain.Venue;
import com.culturalevents.venues.domain.VenueRepository;

import static org.assertj.core.api.Assertions.assertThat;

/** Roda em transação (revertida ao final). Garante que a entidade de venues bate com o V004 sob ddl-auto=validate e que o adaptador lê o que o banco gera. */
@Transactional
class VenuesContextIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private VenueRepository venues;

    @Test
    void contextoSobeComSchemaV004ECriaLocalSemSlugLendoOGeradoPeloBanco() {
        Venue saved = venues.saveAndFlush(new Venue(cityId("SP", "sao-paulo"), "Teste Sem Slug", null, null, null,
                null, null, null, null));

        assertThat(saved.getSlug()).isEqualTo("teste-sem-slug");
        assertThat(saved.getPublicId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void buscaFiltraPorCidadeEBairroSemDiferenciarMaiusculasOrdenandoPorNome() {
        long city = cityId("SP", "sao-paulo");
        insertFixtureVenue(city, "zeta", "Zeta", "Pinheiros");
        insertFixtureVenue(city, "alfa", "Alfa", "PINHEIROS");
        insertFixtureVenue(city, "outro", "Outro", "Centro");

        var page = venues.search(city, "pinheiros", PageRequest.of(0, 50, Sort.by("name", "id")));

        assertThat(page.getContent()).extracting(Venue::getName).containsExactly("Alfa", "Zeta");
        assertThat(venues.search(city, null, PageRequest.of(0, 50)).getContent())
                .extracting(Venue::getName).contains("Alfa", "Zeta", "Outro");
    }
}
