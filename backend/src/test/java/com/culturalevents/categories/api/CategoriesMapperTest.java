package com.culturalevents.categories.api;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import com.culturalevents.categories.application.CategoryNode;
import com.culturalevents.categories.domain.Category;

import static org.assertj.core.api.Assertions.assertThat;

class CategoriesMapperTest {

    private static Category category(long id, Long parentId, String name, String slug, int sortOrder) {
        Category category = BeanUtils.instantiateClass(Category.class);
        ReflectionTestUtils.setField(category, "id", id);
        ReflectionTestUtils.setField(category, "parentId", parentId);
        ReflectionTestUtils.setField(category, "name", name);
        ReflectionTestUtils.setField(category, "slug", slug);
        ReflectionTestUtils.setField(category, "sortOrder", sortOrder);
        return category;
    }

    @Test
    void raizMapeiaCamposEFilhasComParentSlug() {
        CategoryNode rock = new CategoryNode(category(3, 1L, "Rock", "rock", 10), "shows", List.of());
        CategoryNode shows = new CategoryNode(category(1, null, "Shows", "shows", 10), null, List.of(rock));

        var dto = CategoriesMapper.toDto(shows);

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getName()).isEqualTo("Shows");
        assertThat(dto.getSlug()).isEqualTo("shows");
        assertThat(dto.getSortOrder()).isEqualTo(10);
        assertThat(dto.getParentSlug()).isNull();
        assertThat(dto.getChildren()).hasSize(1);
        assertThat(dto.getChildren().get(0).getSlug()).isEqualTo("rock");
        assertThat(dto.getChildren().get(0).getParentSlug()).isEqualTo("shows");
    }

    @Test
    void folhaTemChildrenVazioENuncaNulo() {
        CategoryNode cinema = new CategoryNode(category(2, null, "Cinema", "cinema", 50), null, List.of());

        assertThat(CategoriesMapper.toDto(cinema).getChildren()).isNotNull().isEmpty();
    }
}
