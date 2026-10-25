package com.culturalevents.venues.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.culturalevents.shared.api.model.VenueInput;
import com.culturalevents.venues.application.VenueCommand;
import com.culturalevents.venues.domain.Venue;

import static org.assertj.core.api.Assertions.assertThat;

class VenuesMapperTest {

    @Test
    void entradaViraComandoComTodosOsCampos() {
        VenueInput input = new VenueInput(7L, "Sala")
                .slug("sala").addressLine("Rua A, 1").neighborhood("Centro").postalCode("01000-000")
                .latitude(new BigDecimal("-23.5")).longitude(new BigDecimal("-46.6")).websiteUrl("https://x.org");

        assertThat(VenuesMapper.toCommand(input)).isEqualTo(new VenueCommand(7L, "Sala", "sala", "Rua A, 1",
                "Centro", "01000-000", new BigDecimal("-23.5"), new BigDecimal("-46.6"), "https://x.org"));
    }

    @Test
    void opcionaisAusentesViramNulosNoComando() {
        assertThat(VenuesMapper.toCommand(new VenueInput(7L, "Sala")))
                .isEqualTo(new VenueCommand(7L, "Sala", null, null, null, null, null, null, null));
    }

    @Test
    void dtoUsaOPublicIdComoIdEMantemOpcionaisNulos() {
        Venue venue = new Venue(7L, "Sala", "sala", null, null, null, null, null, null);
        UUID publicId = UUID.randomUUID();
        // Sem persistência, os campos gerados pelo banco são preenchidos como se tivessem sido relidos.
        ReflectionTestUtils.setField(venue, "publicId", publicId);
        ReflectionTestUtils.setField(venue, "createdAt", OffsetDateTime.now());
        ReflectionTestUtils.setField(venue, "updatedAt", OffsetDateTime.now());

        var dto = VenuesMapper.toDto(venue);

        assertThat(dto.getId()).isEqualTo(publicId);
        assertThat(dto.getCityId()).isEqualTo(7L);
        assertThat(dto.getSlug()).isEqualTo("sala");
        assertThat(dto.getAddressLine()).isNull();
        assertThat(dto.getLatitude()).isNull();
        assertThat(dto.getWebsiteUrl()).isNull();
    }

    @Test
    void dtoTrazTodosOsCamposPreenchidos() {
        Venue venue = new Venue(7L, "Sala", "sala", "Rua A, 1", "Centro", "01000-000", new BigDecimal("-23.5"),
                new BigDecimal("-46.6"), "https://x.org");
        ReflectionTestUtils.setField(venue, "publicId", UUID.randomUUID());
        ReflectionTestUtils.setField(venue, "createdAt", OffsetDateTime.now());
        ReflectionTestUtils.setField(venue, "updatedAt", OffsetDateTime.now());

        var dto = VenuesMapper.toDto(venue);

        assertThat(dto.getAddressLine()).isEqualTo("Rua A, 1");
        assertThat(dto.getNeighborhood()).isEqualTo("Centro");
        assertThat(dto.getPostalCode()).isEqualTo("01000-000");
        assertThat(dto.getLatitude()).isEqualByComparingTo("-23.5");
        assertThat(dto.getLongitude()).isEqualByComparingTo("-46.6");
        assertThat(dto.getWebsiteUrl()).isEqualTo("https://x.org");
    }
}
