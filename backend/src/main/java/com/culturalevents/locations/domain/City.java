package com.culturalevents.locations.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Cidade do catálogo de localidades. Somente leitura; cadastro por script SQL. */
@Entity
@Immutable
@Table(name = "city")
public class City {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "ibge_code", nullable = false, unique = true)
    private Integer ibgeCode;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "state_uf", length = 2, nullable = false)
    private String stateUf;

    @Column(name = "name", length = 120, nullable = false)
    private String name;

    @Column(name = "slug", length = 120, nullable = false)
    private String slug;

    @Column(name = "timezone", length = 40, nullable = false)
    private String timezone;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected City() {
    }

    public Long getId() {
        return id;
    }

    public Integer getIbgeCode() {
        return ibgeCode;
    }

    public String getStateUf() {
        return stateUf;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getTimezone() {
        return timezone;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public boolean isActive() {
        return active;
    }
}
