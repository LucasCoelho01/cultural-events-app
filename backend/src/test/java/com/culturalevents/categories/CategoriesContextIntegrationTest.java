package com.culturalevents.categories;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.culturalevents.AbstractPostgresIntegrationTest;
import com.culturalevents.categories.domain.CategoryRepository;

import static org.assertj.core.api.Assertions.assertThat;

/** Garante que a entidade de categories bate com o V003 sob ddl-auto=validate e que o adaptador ordena. */
class CategoriesContextIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private CategoryRepository categories;

    @Test
    void contextoSobeComSchemaV003EOAdaptadorLeATabela() {
        assertThat(categories.findBySlug("outros")).isPresent();
        assertThat(categories.findAllOrdered())
                .filteredOn(category -> !category.getSlug().startsWith(FIXTURE_CATEGORY_PREFIX))
                .extracting(category -> category.getSlug())
                .containsExactly("shows", "teatro", "exposicoes", "danca", "cinema", "outros");
    }

    @Test
    void subcategoriasSaemOrdenadasPorSortOrderNomeEId() {
        insertFixtureCategory("zeta", "Zeta", "shows", 10);
        insertFixtureCategory("alfa", "Alfa", "shows", 10);
        insertFixtureCategory("primeira", "Primeira", "shows", 1);
        Long showsId = categories.findBySlug("shows").orElseThrow().getId();

        assertThat(categories.findChildrenOrdered(showsId))
                .extracting(category -> category.getSlug())
                .containsExactly(FIXTURE_CATEGORY_PREFIX + "primeira", FIXTURE_CATEGORY_PREFIX + "alfa",
                        FIXTURE_CATEGORY_PREFIX + "zeta");
    }
}
