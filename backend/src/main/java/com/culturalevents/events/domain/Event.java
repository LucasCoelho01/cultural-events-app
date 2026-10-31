package com.culturalevents.events.domain;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

/**
 * Evento cultural com suas sessões. Cidade, categoria e local são referenciados só por id.
 * {@code publicId}, {@code slug} (quando nulo) e as datas são preenchidos pelo banco e relidos após gravar.
 */
@Entity
@Table(name = "event")
public class Event {

    /** Nome especial em {@code locked_fields} para sessões alteradas à mão (não é coluna de {@code event}). */
    public static final String LOCKED_OCCURRENCES = "occurrences";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Generated(event = EventType.INSERT)
    @Column(name = "public_id", insertable = false, updatable = false)
    private UUID publicId;

    @Column(name = "city_id", nullable = false)
    private Long cityId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "venue_id")
    private Long venueId;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    // Sem nullable=false: o slug pode ir nulo e o trigger do banco o preenche a partir do título.
    @Column(name = "slug", length = 255)
    private String slug;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private EventStatus status;

    @Column(name = "age_rating", length = 10)
    private String ageRating;

    @Column(name = "price_min_cents")
    private Integer priceMinCents;

    @Column(name = "price_max_cents")
    private Integer priceMaxCents;

    @Column(name = "is_free", nullable = false)
    private boolean free;

    @Column(name = "ticket_url")
    private String ticketUrl;

    @Column(name = "image_url")
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", length = 20, nullable = false, updatable = false)
    private EventOrigin origin = EventOrigin.MANUAL;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    // Não é @Generated em UPDATE: se o UPDATE perde a corrida de versão (0 linhas), o Hibernate lançaria um erro de
    // "valores gerados" em vez de StaleObjectStateException. O adaptador relê a linha (refresh) após gravar.
    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    /** Colunas (e {@link #LOCKED_OCCURRENCES}) alteradas à mão num evento do agente; ele não as sobrescreve. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "locked_fields", nullable = false, columnDefinition = "text[]")
    private List<String> lockedFields = new ArrayList<>();

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startsAt ASC")
    @BatchSize(size = 50)
    private List<Occurrence> occurrences = new ArrayList<>();

    protected Event() {
    }

    public Event(EventFields fields, List<OccurrenceFields> occurrences) {
        replaceWith(fields, occurrences);
    }

    /**
     * Substitui os campos e as sessões. Sessões são casadas por {@code startsAt}: a existente com o mesmo horário é
     * atualizada no lugar, as ausentes saem e as novas entram. Isso evita violar {@code UNIQUE (event_id, starts_at)},
     * porque o Hibernate faz INSERT antes de DELETE ao trocar a lista.
     */
    public void replaceWith(EventFields fields, List<OccurrenceFields> newOccurrences) {
        // "Correção manual vence": num evento do agente, o que esta substituição muda passa a ficar travado.
        // Calculado antes de atribuir, para que um único UPDATE grave os campos e os locks.
        boolean agent = origin == EventOrigin.AGENT;
        Set<String> changed = agent ? changedColumns(fields) : Set.of();
        boolean sessionsChanged = agent && sessionsDiffer(newOccurrences);

        this.cityId = fields.cityId();
        this.categoryId = fields.categoryId();
        this.venueId = fields.venueId();
        this.title = fields.title();
        this.slug = fields.slug();
        this.description = fields.description();
        this.status = fields.status();
        this.ageRating = fields.ageRating();
        this.priceMinCents = fields.priceMinCents();
        this.priceMaxCents = fields.priceMaxCents();
        this.free = fields.free();
        this.ticketUrl = fields.ticketUrl();
        this.imageUrl = fields.imageUrl();
        syncOccurrences(newOccurrences);
        if (sessionsChanged) {
            changed = new LinkedHashSet<>(changed);
            changed.add(LOCKED_OCCURRENCES);
        }
        lock(changed);
    }

    /**
     * Transição {@code PUBLISHED → DRAFT}, a mesma do {@link #replaceWith} com outro {@code status}: num evento do
     * agente, {@code status} passa a ficar travado (correção manual). Sem efeito se o evento não está publicado.
     */
    public void unpublish() {
        if (status != EventStatus.PUBLISHED) {
            return;
        }
        if (origin == EventOrigin.AGENT) {
            lock(Set.of("status"));
        }
        this.status = EventStatus.DRAFT;
    }

    /** Nomes das colunas cujo valor muda com {@code fields}, na ordem das colunas. */
    private Set<String> changedColumns(EventFields fields) {
        Set<String> changed = new LinkedHashSet<>();
        mark(changed, "city_id", cityId, fields.cityId());
        mark(changed, "category_id", categoryId, fields.categoryId());
        mark(changed, "venue_id", venueId, fields.venueId());
        mark(changed, "title", title, fields.title());
        // Slug nulo: o banco regenera a partir do título; só um slug informado e diferente conta como mudança.
        if (fields.slug() != null) {
            mark(changed, "slug", slug, fields.slug());
        }
        mark(changed, "description", description, fields.description());
        mark(changed, "status", status, fields.status());
        mark(changed, "age_rating", ageRating, fields.ageRating());
        mark(changed, "price_min_cents", priceMinCents, fields.priceMinCents());
        mark(changed, "price_max_cents", priceMaxCents, fields.priceMaxCents());
        mark(changed, "is_free", free, fields.free());
        mark(changed, "ticket_url", ticketUrl, fields.ticketUrl());
        mark(changed, "image_url", imageUrl, fields.imageUrl());
        return changed;
    }

    private static void mark(Set<String> changed, String column, Object current, Object incoming) {
        if (!Objects.equals(current, incoming)) {
            changed.add(column);
        }
    }

    /** Compara por instante (e não por {@code OffsetDateTime}, que diferencia o deslocamento) início, fim e status. */
    private boolean sessionsDiffer(List<OccurrenceFields> newOccurrences) {
        Set<Slot> current = occurrences.stream()
                .map(o -> new Slot(o.getStartsAt(), o.getEndsAt(), o.getStatus()))
                .collect(Collectors.toSet());
        Set<Slot> incoming = newOccurrences.stream()
                .map(o -> new Slot(o.startsAt(), o.endsAt(), o.status()))
                .collect(Collectors.toSet());
        return !current.equals(incoming);
    }

    private record Slot(java.time.Instant startsAt, java.time.Instant endsAt, OccurrenceStatus status) {

        Slot(OffsetDateTime startsAt, OffsetDateTime endsAt, OccurrenceStatus status) {
            this(key(startsAt).toInstant(), endsAt == null ? null : key(endsAt).toInstant(), status);
        }
    }

    /** União sem duplicatas; troca a lista (e não a altera no lugar) para o Hibernate perceber a mudança. */
    private void lock(Set<String> names) {
        if (lockedFields.containsAll(names)) {
            return;
        }
        Set<String> merged = new LinkedHashSet<>(lockedFields);
        merged.addAll(names);
        lockedFields = new ArrayList<>(merged);
    }

    private void syncOccurrences(List<OccurrenceFields> newOccurrences) {
        Map<OffsetDateTime, Occurrence> remaining = new HashMap<>();
        for (Occurrence occurrence : occurrences) {
            remaining.put(key(occurrence.getStartsAt()), occurrence);
        }
        List<Occurrence> kept = new ArrayList<>();
        List<Occurrence> added = new ArrayList<>();
        for (OccurrenceFields fields : newOccurrences) {
            OccurrenceFields normalized = new OccurrenceFields(
                    key(fields.startsAt()), fields.endsAt() == null ? null : key(fields.endsAt()), fields.status());
            Occurrence current = remaining.remove(normalized.startsAt());
            if (current == null) {
                added.add(new Occurrence(this, normalized));
            } else {
                current.update(normalized);
                kept.add(current);
            }
        }
        // orphanRemoval apaga as que sobraram em "remaining"; a lista gerenciada é alterada no lugar.
        occurrences.removeAll(remaining.values());
        occurrences.addAll(added);
    }

    /** O Postgres guarda microssegundos; a chave de comparação usa a mesma precisão. */
    private static OffsetDateTime key(OffsetDateTime value) {
        return value.truncatedTo(ChronoUnit.MICROS);
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public Long getCityId() {
        return cityId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public Long getVenueId() {
        return venueId;
    }

    public String getTitle() {
        return title;
    }

    public String getSlug() {
        return slug;
    }

    public String getDescription() {
        return description;
    }

    public EventStatus getStatus() {
        return status;
    }

    public String getAgeRating() {
        return ageRating;
    }

    public Integer getPriceMinCents() {
        return priceMinCents;
    }

    public Integer getPriceMaxCents() {
        return priceMaxCents;
    }

    public boolean isFree() {
        return free;
    }

    public String getTicketUrl() {
        return ticketUrl;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public EventOrigin getOrigin() {
        return origin;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public int getVersion() {
        return version;
    }

    /** Campos travados por correção manual (colunas de {@code event} e {@link #LOCKED_OCCURRENCES}). */
    public List<String> getLockedFields() {
        return List.copyOf(lockedFields);
    }

    /** Sessões ordenadas por {@code startsAt} (a ordem vem do banco após gravar ou carregar). */
    public List<Occurrence> getOccurrences() {
        return List.copyOf(occurrences);
    }
}
