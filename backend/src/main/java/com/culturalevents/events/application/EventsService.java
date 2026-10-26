package com.culturalevents.events.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.culturalevents.categories.application.CategoryLookup;
import com.culturalevents.categories.application.CategoryRef;
import com.culturalevents.events.domain.Event;
import com.culturalevents.events.domain.EventFields;
import com.culturalevents.events.domain.EventFilter;
import com.culturalevents.events.domain.EventRepository;
import com.culturalevents.events.domain.EventStatus;
import com.culturalevents.events.domain.OccurrenceFields;
import com.culturalevents.locations.application.CityLookup;
import com.culturalevents.locations.application.CityRef;
import com.culturalevents.shared.error.ConflictException;
import com.culturalevents.shared.error.FieldValidationException;
import com.culturalevents.shared.error.FieldViolation;
import com.culturalevents.shared.error.NotFoundException;
import com.culturalevents.shared.persistence.ConstraintViolations;
import com.culturalevents.venues.application.VenueLookup;
import com.culturalevents.venues.application.VenueRef;

@Service
@Transactional
public class EventsService {

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final String CHECK_VIOLATION = "23514";

    /** Constraints que o banco pode rejeitar mesmo após a validação de negócio, identificadas pelo nome. */
    private static final Map<String, FieldViolation> CONSTRAINT_VIOLATIONS = Map.ofEntries(
            Map.entry("event_slug_not_empty", new FieldViolation("title", "o título não gera um slug válido")),
            Map.entry("event_venue_city_match",
                    new FieldViolation("venueId", "o local pertence a outra cidade que não a do evento")),
            Map.entry("event_free_no_price",
                    new FieldViolation("isFree", "evento gratuito não pode ter preço maior que zero")),
            Map.entry("event_price_range",
                    new FieldViolation("priceMaxCents", "o preço máximo não pode ser menor que o mínimo")),
            Map.entry("event_price_min_nonneg",
                    new FieldViolation("priceMinCents", "o preço mínimo não pode ser negativo")),
            Map.entry("event_status_valid", new FieldViolation("status", "status inválido")),
            Map.entry("occurrence_time_order",
                    new FieldViolation("occurrences", "o fim de uma sessão não pode ser anterior ao início")),
            Map.entry("occurrence_status_valid", new FieldViolation("occurrences", "status de sessão inválido")),
            Map.entry("occurrence_event_starts_at_key",
                    new FieldViolation("occurrences", "há duas sessões com o mesmo início")),
            Map.entry("event_city_id_fkey", new FieldViolation("cityId", "a cidade informada não existe")),
            Map.entry("event_category_id_fkey", new FieldViolation("categoryId", "a categoria informada não existe")),
            Map.entry("event_venue_id_fkey", new FieldViolation("venueId", "o local informado não existe")));

    private final EventRepository events;
    private final CityLookup cities;
    private final CategoryLookup categories;
    private final VenueLookup venues;
    private final Clock clock;

    public EventsService(EventRepository events, CityLookup cities, CategoryLookup categories, VenueLookup venues,
            Clock clock) {
        this.events = events;
        this.cities = cities;
        this.categories = categories;
        this.venues = venues;
        this.clock = clock;
    }

    /**
     * @param city {@code uf/slug} (formato já validado pelo contrato) ou {@code null} para a cidade padrão
     * @param category slug da categoria (inclui subcategorias) ou {@code null}
     */
    @Transactional(readOnly = true)
    public Page<EventView> list(String city, String category, OffsetDateTime from, OffsetDateTime to,
            EventStatus status, int page, int size) {
        long cityId = city == null ? cities.defaultCityId() : resolveCityId(city);
        List<Long> categoryIds = category == null ? null : categories.findIdsWithDescendantsBySlug(category)
                .orElseThrow(() -> new NotFoundException("Categoria não encontrada: " + category));
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime reference = from != null && from.isAfter(now) ? from : now;

        Page<Event> result = events.search(new EventFilter(cityId, categoryIds, from, to, status, reference),
                PageRequest.of(page, size));
        return new PageImpl<>(views(result.getContent()), result.getPageable(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public EventView get(UUID id) {
        return view(find(id));
    }

    public EventView create(EventCommand command) {
        Long venueInternalId = validate(command, null);
        Event event = new Event(fields(command, venueInternalId), occurrenceFields(command));
        return view(write(() -> events.create(event)));
    }

    public EventView replace(UUID id, EventCommand command) {
        Event event = find(id);
        if (command.version() == null || command.version() != event.getVersion()) {
            throw new ConflictException("A versão informada está desatualizada; releia o evento e tente novamente.");
        }
        Long venueInternalId = validate(command, event);
        event.replaceWith(fields(command, venueInternalId), occurrenceFields(command));
        return view(write(() -> events.update(event)));
    }

    public void delete(UUID id) {
        events.deleteAndFlush(find(id));
    }

    // --- validação

    /** Valida as referências e as regras de negócio, acumulando os erros; devolve o id interno do local, se houver. */
    private Long validate(EventCommand command, Event current) {
        List<FieldViolation> violations = new ArrayList<>();

        if (current == null || !command.cityId().equals(current.getCityId())) {
            Optional<CityRef> city = cities.findById(command.cityId());
            if (city.isEmpty()) {
                violations.add(new FieldViolation("cityId", "a cidade informada não existe"));
            } else if (!city.get().active()) {
                violations.add(new FieldViolation("cityId", "a cidade informada está inativa"));
            }
        }

        if (categories.findById(command.categoryId()).isEmpty()) {
            violations.add(new FieldViolation("categoryId", "a categoria informada não existe"));
        }

        Long venueInternalId = null;
        if (command.venueId() != null) {
            Optional<VenueRef> venue = venues.findByPublicId(command.venueId());
            if (venue.isEmpty()) {
                violations.add(new FieldViolation("venueId", "o local informado não existe"));
            } else if (venue.get().cityId() != command.cityId()) {
                violations.add(new FieldViolation("venueId", "o local pertence a outra cidade que não a do evento"));
            } else {
                venueInternalId = venue.get().id();
            }
        }

        validatePrices(command, violations);
        validateOccurrences(command, violations);

        if (!violations.isEmpty()) {
            throw new FieldValidationException(violations);
        }
        return venueInternalId;
    }

    private static void validatePrices(EventCommand command, List<FieldViolation> violations) {
        Integer min = command.priceMinCents();
        Integer max = command.priceMaxCents();
        if (command.free() && ((min != null && min > 0) || (max != null && max > 0))) {
            violations.add(new FieldViolation("isFree", "evento gratuito não pode ter preço maior que zero"));
        }
        if (min != null && max != null && max < min) {
            violations.add(new FieldViolation("priceMaxCents", "o preço máximo não pode ser menor que o mínimo"));
        }
    }

    private void validateOccurrences(EventCommand command, List<FieldViolation> violations) {
        Set<OffsetDateTime> seen = new HashSet<>();
        boolean hasFuture = false;
        OffsetDateTime now = OffsetDateTime.now(clock);
        for (int i = 0; i < command.occurrences().size(); i++) {
            OccurrenceCommand occurrence = command.occurrences().get(i);
            if (occurrence.endsAt() != null && occurrence.endsAt().isBefore(occurrence.startsAt())) {
                violations.add(new FieldViolation("occurrences[" + i + "].endsAt",
                        "o fim da sessão não pode ser anterior ao início"));
            }
            if (!seen.add(occurrence.startsAt().truncatedTo(ChronoUnit.MICROS))) {
                violations.add(new FieldViolation("occurrences[" + i + "].startsAt",
                        "já existe uma sessão com esse início"));
            }
            hasFuture |= occurrence.startsAt().isAfter(now);
        }
        if (command.status() == EventStatus.PUBLISHED && !hasFuture) {
            violations.add(new FieldViolation("occurrences",
                    "um evento publicado precisa de ao menos uma sessão futura"));
        }
    }

    // --- montagem e gravação

    private static EventFields fields(EventCommand command, Long venueInternalId) {
        String slug = command.slug() == null || command.slug().isBlank() ? null : command.slug();
        return new EventFields(command.cityId(), command.categoryId(), venueInternalId, command.title(), slug,
                command.description(), command.status(), command.ageRating(), command.priceMinCents(),
                command.priceMaxCents(), command.free(), command.ticketUrl(), command.imageUrl());
    }

    private static List<OccurrenceFields> occurrenceFields(EventCommand command) {
        return command.occurrences().stream()
                .map(o -> new OccurrenceFields(o.startsAt(), o.endsAt(), o.status()))
                .toList();
    }

    private Event find(UUID id) {
        return events.findByPublicId(id).orElseThrow(() -> new NotFoundException("Evento não encontrado: " + id));
    }

    private long resolveCityId(String city) {
        int separator = city.indexOf('/');
        return cities.findIdByUfAndSlug(city.substring(0, separator), city.substring(separator + 1))
                .orElseThrow(() -> new NotFoundException("Cidade não encontrada: " + city));
    }

    private EventView view(Event event) {
        return views(List.of(event)).getFirst();
    }

    /** Anexa categoria e local em lote (uma consulta de cada), para não fazer N+1 na listagem. */
    private List<EventView> views(List<Event> list) {
        // As sessões são carregadas sob demanda; inicializa-as aqui, dentro da transação, para o mapeamento na API.
        list.forEach(event -> event.getOccurrences());
        Set<Long> categoryIds = list.stream().map(Event::getCategoryId).collect(Collectors.toSet());
        Set<Long> venueIds = list.stream().map(Event::getVenueId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, CategoryRef> categoriesById = categories.findAllByIds(categoryIds).stream()
                .collect(Collectors.toMap(CategoryRef::id, c -> c));
        Map<Long, VenueRef> venuesById = venues.findAllByIds(venueIds).stream()
                .collect(Collectors.toMap(VenueRef::id, v -> v));
        return list.stream()
                .map(event -> new EventView(event, categoriesById.get(event.getCategoryId()),
                        event.getVenueId() == null ? null : venuesById.get(event.getVenueId())))
                .toList();
    }

    /** Executa a gravação traduzindo violações de constraint, identificadas pelo nome, em erros de negócio. */
    private Event write(Supplier<Event> write) {
        try {
            return write.get();
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new ConflictException("O evento foi alterado por outra requisição; releia e tente novamente.");
        } catch (DataIntegrityViolationException ex) {
            String state = ConstraintViolations.sqlState(ex);
            String name = ConstraintViolations.constraintName(ex);
            if (UNIQUE_VIOLATION.equals(state) && !"occurrence_event_starts_at_key".equals(name)) {
                throw new ConflictException("Já existe um evento com esse slug na cidade.");
            }
            if (UNIQUE_VIOLATION.equals(state) || CHECK_VIOLATION.equals(state)
                    || FOREIGN_KEY_VIOLATION.equals(state)) {
                FieldViolation violation = CONSTRAINT_VIOLATIONS.get(name);
                if (violation != null) {
                    throw new FieldValidationException(violation.field(), violation.message());
                }
            }
            throw ex;
        }
    }
}
