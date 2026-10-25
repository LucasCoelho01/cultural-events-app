package com.culturalevents.categories.application;

import java.util.List;

import com.culturalevents.categories.domain.Category;

/** Categoria com o slug do pai (nulo nas raízes) e suas subcategorias já ordenadas. */
public record CategoryNode(Category category, String parentSlug, List<CategoryNode> children) {
}
