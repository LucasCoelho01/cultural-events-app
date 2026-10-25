package com.culturalevents.venues.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Local (casa de show, teatro, museu) de uma cidade. A cidade é referenciada só por {@code cityId}.
 * {@code publicId}, {@code slug} (quando nulo) e as datas são preenchidos pelo banco e relidos após gravar.
 */
@Entity
@Table(name = "venue")
public class Venue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Generated(event = EventType.INSERT)
    @Column(name = "public_id", insertable = false, updatable = false)
    private UUID publicId;

    @Column(name = "city_id", nullable = false)
    private Long cityId;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    // Sem nullable=false: o slug pode ir nulo e o trigger do banco o preenche a partir do nome.
    // Não é @Generated (isso impediria gravar o slug informado); o adaptador relê a linha após gravar.
    @Column(name = "slug", length = 200)
    private String slug;

    @Column(name = "address_line", length = 255)
    private String addressLine;

    @Column(name = "neighborhood", length = 120)
    private String neighborhood;

    @Column(name = "postal_code", length = 9)
    private String postalCode;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "website_url")
    private String websiteUrl;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    protected Venue() {
    }

    public Venue(Long cityId, String name, String slug, String addressLine, String neighborhood, String postalCode,
            BigDecimal latitude, BigDecimal longitude, String websiteUrl) {
        replaceWith(cityId, name, slug, addressLine, neighborhood, postalCode, latitude, longitude, websiteUrl);
    }

    /** Substitui todos os campos editáveis; {@code slug} nulo faz o banco regenerá-lo a partir do nome. */
    public void replaceWith(Long cityId, String name, String slug, String addressLine, String neighborhood,
            String postalCode, BigDecimal latitude, BigDecimal longitude, String websiteUrl) {
        this.cityId = cityId;
        this.name = name;
        this.slug = slug;
        this.addressLine = addressLine;
        this.neighborhood = neighborhood;
        this.postalCode = postalCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.websiteUrl = websiteUrl;
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

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public String getNeighborhood() {
        return neighborhood;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
