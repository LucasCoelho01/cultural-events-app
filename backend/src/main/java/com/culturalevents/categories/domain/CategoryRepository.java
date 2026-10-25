package com.culturalevents.categories.domain;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository {

    /** Todas as categorias, ordenadas por {@code sort_order}, {@code name} e {@code id}. */
    List<Category> findAllOrdered();

    Optional<Category> findBySlug(String slug);

    Optional<Category> findById(Long id);

    /** Subcategorias diretas de {@code parentId}, na mesma ordenação de {@link #findAllOrdered()}. */
    List<Category> findChildrenOrdered(Long parentId);
}
