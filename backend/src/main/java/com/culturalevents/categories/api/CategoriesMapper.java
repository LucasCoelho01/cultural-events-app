package com.culturalevents.categories.api;

import com.culturalevents.categories.application.CategoryNode;

final class CategoriesMapper {

    private CategoriesMapper() {
    }

    static com.culturalevents.shared.api.model.Category toDto(CategoryNode node) {
        return new com.culturalevents.shared.api.model.Category(
                node.category().getId(),
                node.category().getName(),
                node.category().getSlug(),
                node.category().getSortOrder(),
                node.children().stream().map(CategoriesMapper::toDto).toList())
                .parentSlug(node.parentSlug());
    }
}
