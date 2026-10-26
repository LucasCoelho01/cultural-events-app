package com.culturalevents.categories.application;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.culturalevents.categories.domain.Category;
import com.culturalevents.categories.domain.CategoryRepository;

/** Caso de uso de consulta de categoria para outras capabilities, sem expor as classes de domínio. */
@Service
@Transactional(readOnly = true)
public class CategoryLookup {

    private final CategoryRepository categories;

    public CategoryLookup(CategoryRepository categories) {
        this.categories = categories;
    }

    public Optional<CategoryRef> findById(long id) {
        return categories.findById(id).map(CategoryLookup::toRef);
    }

    public List<CategoryRef> findAllByIds(Collection<Long> ids) {
        return ids.isEmpty() ? List.of() : categories.findAllByIds(ids).stream().map(CategoryLookup::toRef).toList();
    }

    /** Ids da categoria do slug e de suas subcategorias diretas (no máximo 2 níveis); vazio se o slug não existe. */
    public Optional<List<Long>> findIdsWithDescendantsBySlug(String slug) {
        return categories.findBySlug(slug).map(category -> Stream.concat(
                        Stream.of(category.getId()),
                        categories.findChildrenOrdered(category.getId()).stream().map(Category::getId))
                .toList());
    }

    private static CategoryRef toRef(Category category) {
        return new CategoryRef(category.getId(), category.getSlug(), category.getName());
    }
}
