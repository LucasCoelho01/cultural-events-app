package com.culturalevents.categories.application;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import com.culturalevents.categories.domain.Category;
import com.culturalevents.categories.domain.CategoryRepository;
import com.culturalevents.shared.error.NotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CategoriesServiceTest {

    private final CategoryRepository repository = mock(CategoryRepository.class);
    private final CategoriesService service = new CategoriesService(repository);

    /** A entidade é imutável e sem construtor público; os testes a montam por reflexão. */
    static Category category(long id, Long parentId, String name, String slug, int sortOrder) {
        Category category = BeanUtils.instantiateClass(Category.class);
        ReflectionTestUtils.setField(category, "id", id);
        ReflectionTestUtils.setField(category, "parentId", parentId);
        ReflectionTestUtils.setField(category, "name", name);
        ReflectionTestUtils.setField(category, "slug", slug);
        ReflectionTestUtils.setField(category, "sortOrder", sortOrder);
        return category;
    }

    @Test
    void arvoreAgrupaSubcategoriasSobSuasRaizesMantendoAOrdemDoRepositorio() {
        Category shows = category(1, null, "Shows", "shows", 10);
        Category cinema = category(2, null, "Cinema", "cinema", 20);
        Category alfa = category(3, 1L, "Alfa", "alfa", 5);
        Category zeta = category(4, 1L, "Zeta", "zeta", 5);
        when(repository.findAllOrdered()).thenReturn(List.of(shows, cinema, alfa, zeta));

        List<CategoryNode> tree = service.listTree();

        assertThat(tree).extracting(node -> node.category().getSlug()).containsExactly("shows", "cinema");
        assertThat(tree.get(0).parentSlug()).isNull();
        assertThat(tree.get(0).children()).extracting(node -> node.category().getSlug())
                .containsExactly("alfa", "zeta");
        assertThat(tree.get(0).children()).extracting(CategoryNode::parentSlug).containsOnly("shows");
    }

    @Test
    void raizSemSubcategoriasTemChildrenVazio() {
        when(repository.findAllOrdered()).thenReturn(List.of(category(2, null, "Cinema", "cinema", 20)));

        List<CategoryNode> tree = service.listTree();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).children()).isNotNull().isEmpty();
    }

    @Test
    void bancoSemCategoriasDevolveArvoreVazia() {
        when(repository.findAllOrdered()).thenReturn(List.of());

        assertThat(service.listTree()).isEmpty();
    }

    @Test
    void raizPorSlugTrazFilhasEParentSlugNulo() {
        Category shows = category(1, null, "Shows", "shows", 10);
        when(repository.findBySlug("shows")).thenReturn(Optional.of(shows));
        when(repository.findChildrenOrdered(1L)).thenReturn(List.of(category(3, 1L, "Rock", "rock", 10)));

        CategoryNode node = service.getBySlug("shows");

        assertThat(node.parentSlug()).isNull();
        assertThat(node.children()).extracting(child -> child.category().getSlug()).containsExactly("rock");
    }

    @Test
    void subcategoriaPorSlugTrazParentSlugEChildrenVazio() {
        Category rock = category(3, 1L, "Rock", "rock", 10);
        when(repository.findBySlug("rock")).thenReturn(Optional.of(rock));
        when(repository.findById(1L)).thenReturn(Optional.of(category(1, null, "Shows", "shows", 10)));
        when(repository.findChildrenOrdered(3L)).thenReturn(List.of());

        CategoryNode node = service.getBySlug("rock");

        assertThat(node.parentSlug()).isEqualTo("shows");
        assertThat(node.children()).isEmpty();
    }

    @Test
    void slugInexistenteLancaNotFound() {
        when(repository.findBySlug("nao-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBySlug("nao-existe"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("nao-existe");
    }
}
