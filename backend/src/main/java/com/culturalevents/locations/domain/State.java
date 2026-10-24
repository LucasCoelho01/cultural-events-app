package com.culturalevents.locations.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Unidade federativa. Catálogo somente leitura; cadastro por script SQL. */
@Entity
@Immutable
@Table(name = "state")
public class State {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uf", length = 2, nullable = false)
    private String uf;

    @Column(name = "name", length = 60, nullable = false)
    private String name;

    protected State() {
    }

    public State(String uf, String name) {
        this.uf = uf;
        this.name = name;
    }

    public String getUf() {
        return uf;
    }

    public String getName() {
        return name;
    }
}
