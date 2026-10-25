package com.culturalevents.categories.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.culturalevents.categories.domain.Category;
import com.culturalevents.categories.domain.CategoryRepository;
import com.culturalevents.shared.error.NotFoundException;

@Service
@Transactional(readOnly = true)
public class CategoriesService {

    private final CategoryRepository categories;

    public CategoriesService(CategoryRepository categories) {
        this.categories = categories;
    }

    /** Árvore completa: raízes com suas subcategorias, na ordem em que o repositório devolve. */
    public List<CategoryNode> listTree() {
        Map<Long, List<Category>> childrenByParent = new HashMap<>();
        List<Category> roots = new ArrayList<>();
        for (Category category : categories.findAllOrdered()) {
            if (category.getParentId() == null) {
                roots.add(category);
            } else {
                childrenByParent.computeIfAbsent(category.getParentId(), id -> new ArrayList<>()).add(category);
            }
        }
        return roots.stream()
                .map(root -> new CategoryNode(root, null, leaves(root, childrenByParent.get(root.getId()))))
                .toList();
    }

    public CategoryNode getBySlug(String slug) {
        Category category = categories.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Categoria não encontrada: " + slug));
        String parentSlug = category.getParentId() == null
                ? null
                : categories.findById(category.getParentId()).map(Category::getSlug).orElse(null);
        return new CategoryNode(category, parentSlug,
                leaves(category, categories.findChildrenOrdered(category.getId())));
    }

    private static List<CategoryNode> leaves(Category parent, List<Category> children) {
        if (children == null) {
            return List.of();
        }
        return children.stream()
                .map(child -> new CategoryNode(child, parent.getSlug(), List.of()))
                .toList();
    }
}
