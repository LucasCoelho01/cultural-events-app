package com.culturalevents.categories.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/** Categoria ou subcategoria de eventos (no máximo 2 níveis). Somente leitura; cadastro por script SQL. */
@Entity
@Immutable
@Table(name = "category")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "name", length = 80, nullable = false)
    private String name;

    @Column(name = "slug", length = 80, nullable = false, unique = true)
    private String slug;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected Category() {
    }

    public Long getId() {
        return id;
    }

    public Long getParentId() {
        return parentId;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
