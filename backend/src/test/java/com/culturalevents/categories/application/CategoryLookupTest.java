package com.culturalevents.categories.application;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.culturalevents.categories.domain.Category;
import com.culturalevents.categories.domain.CategoryRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoryLookupTest {

    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final CategoryLookup lookup = new CategoryLookup(categories);

    @Test
    void categoriaPorIdVirRef() {
        Category shows = category(1L, "shows", "Shows");
        when(categories.findById(1L)).thenReturn(Optional.of(shows));

        assertThat(lookup.findById(1L)).contains(new CategoryRef(1L, "shows", "Shows"));
    }

    @Test
    void categoriaInexistentePorIdRetornaVazio() {
        when(categories.findById(9L)).thenReturn(Optional.empty());

        assertThat(lookup.findById(9L)).isEmpty();
    }

    @Test
    void varias() {
        Category shows = category(1L, "shows", "Shows");
        Category teatro = category(2L, "teatro", "Teatro");
        when(categories.findAllByIds(Set.of(1L, 2L))).thenReturn(List.of(shows, teatro));

        assertThat(lookup.findAllByIds(Set.of(1L, 2L)))
                .containsExactly(new CategoryRef(1L, "shows", "Shows"), new CategoryRef(2L, "teatro", "Teatro"));
    }

    @Test
    void listaVaziaDeIdsNaoConsultaORepositorio() {
        assertThat(lookup.findAllByIds(Set.of())).isEmpty();
        verify(categories, never()).findAllByIds(Set.of());
    }

    @Test
    void raizTrazASiMesmaESuasSubcategorias() {
        Category shows = category(1L, "shows", "Shows");
        when(categories.findBySlug("shows")).thenReturn(Optional.of(shows));
        List<Category> children = List.of(category(5L, "rock", "Rock"), category(6L, "jazz", "Jazz"));
        when(categories.findChildrenOrdered(1L)).thenReturn(children);

        assertThat(lookup.findIdsWithDescendantsBySlug("shows")).contains(List.of(1L, 5L, 6L));
    }

    @Test
    void subcategoriaTrazSoASiMesma() {
        Category rock = category(5L, "rock", "Rock");
        when(categories.findBySlug("rock")).thenReturn(Optional.of(rock));
        when(categories.findChildrenOrdered(5L)).thenReturn(List.of());

        assertThat(lookup.findIdsWithDescendantsBySlug("rock")).contains(List.of(5L));
    }

    @Test
    void slugInexistenteRetornaVazio() {
        when(categories.findBySlug("nada")).thenReturn(Optional.empty());

        assertThat(lookup.findIdsWithDescendantsBySlug("nada")).isEmpty();
    }

    private static Category category(long id, String slug, String name) {
        Category category = mock(Category.class);
        when(category.getId()).thenReturn(id);
        when(category.getSlug()).thenReturn(slug);
        when(category.getName()).thenReturn(name);
        return category;
    }
}
