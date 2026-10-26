package com.culturalevents.venues.application;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.culturalevents.locations.application.CityLookup;
import com.culturalevents.locations.application.CityRef;
import com.culturalevents.shared.error.ConflictException;
import com.culturalevents.shared.error.FieldValidationException;
import com.culturalevents.shared.error.FieldViolation;
import com.culturalevents.shared.error.NotFoundException;
import com.culturalevents.shared.persistence.ConstraintViolations;
import com.culturalevents.venues.domain.Venue;
import com.culturalevents.venues.domain.VenueRepository;

@Service
@Transactional
public class VenuesService {

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final String CHECK_VIOLATION = "23514";
    private static final String RESTRICT_VIOLATION = "23001";

    /** Constraints de venue que o banco pode rejeitar mesmo após a validação do contrato, por nome. */
    private static final Map<String, FieldViolation> CONSTRAINT_VIOLATIONS = Map.of(
            "venue_latitude_range", new FieldViolation("latitude", "a latitude deve estar entre -90 e 90"),
            "venue_longitude_range", new FieldViolation("longitude", "a longitude deve estar entre -180 e 180"),
            "venue_postal_code_format", new FieldViolation("postalCode", "o CEP deve ter o formato 00000-000"),
            "venue_slug_not_empty", new FieldViolation("name", "o nome não gera um slug válido"),
            "venue_city_id_fkey", new FieldViolation("cityId", "a cidade informada não existe"));

    private static final Sort VENUE_ORDER = Sort.by("name", "id");

    private final VenueRepository venues;
    private final CityLookup cities;

    public VenuesService(VenueRepository venues, CityLookup cities) {
        this.venues = venues;
        this.cities = cities;
    }

    /**
     * @param city {@code uf/slug} (formato já validado pelo contrato) ou {@code null} para a cidade padrão
     * @param neighborhood bairro (sem diferenciar maiúsculas) ou {@code null}
     */
    @Transactional(readOnly = true)
    public Page<Venue> list(String city, String neighborhood, int page, int size) {
        long cityId = city == null ? cities.defaultCityId() : resolveCityId(city);
        return venues.search(cityId, neighborhood, PageRequest.of(page, size, VENUE_ORDER));
    }

    @Transactional(readOnly = true)
    public Venue get(UUID id) {
        return find(id);
    }

    public Venue create(VenueCommand command) {
        requireActiveCity(command.cityId());
        Venue venue = new Venue(command.cityId(), command.name(), normalizeSlug(command.slug()),
                command.addressLine(), command.neighborhood(), command.postalCode(), command.latitude(),
                command.longitude(), command.websiteUrl());
        return write(() -> venues.saveAndFlush(venue));
    }

    public Venue replace(UUID id, VenueCommand command) {
        Venue venue = find(id);
        if (!command.cityId().equals(venue.getCityId())) {
            requireActiveCity(command.cityId());
        }
        venue.replaceWith(command.cityId(), command.name(), normalizeSlug(command.slug()), command.addressLine(),
                command.neighborhood(), command.postalCode(), command.latitude(), command.longitude(),
                command.websiteUrl());
        return write(() -> venues.saveAndFlush(venue));
    }

    public void delete(UUID id) {
        Venue venue = find(id);
        try {
            venues.deleteAndFlush(venue);
        } catch (DataIntegrityViolationException ex) {
            if (FOREIGN_KEY_VIOLATION.equals(ConstraintViolations.sqlState(ex))) {
                throw new ConflictException("O local possui eventos ligados e não pode ser removido.");
            }
            throw ex;
        }
    }

    private Venue find(UUID id) {
        return venues.findByPublicId(id).orElseThrow(() -> new NotFoundException("Local não encontrado: " + id));
    }

    private long resolveCityId(String city) {
        int separator = city.indexOf('/');
        String uf = city.substring(0, separator);
        String slug = city.substring(separator + 1);
        return cities.findIdByUfAndSlug(uf, slug)
                .orElseThrow(() -> new NotFoundException("Cidade não encontrada: " + city));
    }

    private void requireActiveCity(Long cityId) {
        Optional<CityRef> city = cities.findById(cityId);
        if (city.isEmpty()) {
            throw new FieldValidationException("cityId", "a cidade informada não existe");
        }
        if (!city.get().active()) {
            throw new FieldValidationException("cityId", "a cidade informada está inativa");
        }
    }

    private static String normalizeSlug(String slug) {
        return slug == null || slug.isBlank() ? null : slug;
    }

    /** Executa a gravação traduzindo violações de constraint do banco, identificadas pelo nome, em erros de negócio. */
    private Venue write(Supplier<Venue> write) {
        try {
            return write.get();
        } catch (DataIntegrityViolationException ex) {
            String state = ConstraintViolations.sqlState(ex);
            if (UNIQUE_VIOLATION.equals(state)) {
                throw new ConflictException("Já existe um local com esse slug na cidade.");
            }
            if (RESTRICT_VIOLATION.equals(state)) {
                throw new ConflictException("O local possui eventos ligados e não pode mudar de cidade.");
            }
            if (CHECK_VIOLATION.equals(state) || FOREIGN_KEY_VIOLATION.equals(state)) {
                FieldViolation violation = CONSTRAINT_VIOLATIONS.get(ConstraintViolations.constraintName(ex));
                if (violation != null) {
                    throw new FieldValidationException(violation.field(), violation.message());
                }
            }
            throw ex;
        }
    }
}
