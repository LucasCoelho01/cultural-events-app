package com.culturalevents.shared.persistence;

import java.math.BigDecimal;
import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import com.culturalevents.AbstractPostgresIntegrationTest;
import com.culturalevents.venues.domain.Venue;
import com.culturalevents.venues.domain.VenueRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class ConstraintViolationsTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private VenueRepository venues;

    @Test
    void sqlStateEConstraintNameSaoLidosDaCadeiaDeCausas() {
        SQLException sql = new SQLException("x", "23514");
        ConstraintViolationException hibernate = new ConstraintViolationException("x", sql, "venue_latitude_range");
        DataIntegrityViolationException spring = new DataIntegrityViolationException("x", hibernate);

        assertThat(ConstraintViolations.sqlState(spring)).isEqualTo("23514");
        assertThat(ConstraintViolations.constraintName(spring)).isEqualTo("venue_latitude_range");
    }

    @Test
    void semSqlExceptionNemConstraintRetornaNulo() {
        RuntimeException plain = new RuntimeException("x");

        assertThat(ConstraintViolations.sqlState(plain)).isNull();
        assertThat(ConstraintViolations.constraintName(plain)).isNull();
    }

    @Test
    void checkNomeadoDoPostgresChegaComONomeDaConstraint() {
        Venue venue = new Venue(cityId("SP", "sao-paulo"), "Teste Latitude", "teste-latitude", null, null, null,
                new BigDecimal("91"), null, null);

        DataIntegrityViolationException failure =
                catchThrowableOfType(DataIntegrityViolationException.class, () -> venues.saveAndFlush(venue));

        assertThat(failure).isNotNull();
        assertThat(ConstraintViolations.sqlState(failure)).isEqualTo("23514");
        assertThat(ConstraintViolations.constraintName(failure)).isEqualTo("venue_latitude_range");
    }

    @Test
    void erroDeTriggerComRaiseNomeadoChegaComONomeDaConstraint() {
        Venue venue = new Venue(cityId("SP", "sao-paulo"), "!!!", null, null, null, null, null, null, null);

        DataIntegrityViolationException failure =
                catchThrowableOfType(DataIntegrityViolationException.class, () -> venues.saveAndFlush(venue));

        assertThat(failure).isNotNull();
        assertThat(ConstraintViolations.sqlState(failure)).isEqualTo("23514");
        assertThat(ConstraintViolations.constraintName(failure)).isEqualTo("venue_slug_not_empty");
    }

    @Test
    void nomeVemDoTextoDaMensagemQuandoOHibernateNaoOExtrai() {
        SQLException sql = new SQLException("ERROR: violates check constraint \"minha_regra\": detalhe", "23514");

        assertThat(ConstraintViolations.constraintName(new DataIntegrityViolationException("x", sql)))
                .isEqualTo("minha_regra");
    }
}
