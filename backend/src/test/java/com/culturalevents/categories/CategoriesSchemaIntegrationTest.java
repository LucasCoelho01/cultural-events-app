package com.culturalevents.categories;

import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import com.culturalevents.AbstractPostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** Verifica o schema, a carga inicial e o trigger de 2 níveis da migration V003 diretamente por SQL. */
class CategoriesSchemaIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final String CHECK_VIOLATION = "23514";

    @Test
    void tabelaEColunasCriadas() {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'category'",
                String.class);

        assertThat(columns).containsExactlyInAnyOrder("id", "parent_id", "name", "slug", "sort_order");
    }

    @Test
    void cargaInicialTemSeisRaizesNaOrdemEsperada() {
        List<String> slugs = jdbcTemplate.queryForList(
                "SELECT slug FROM category WHERE slug NOT LIKE ? AND parent_id IS NULL ORDER BY sort_order",
                String.class, FIXTURE_CATEGORY_PREFIX + "%");
        Integer total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM category WHERE slug NOT LIKE ?", Integer.class, FIXTURE_CATEGORY_PREFIX + "%");

        assertThat(slugs).containsExactly("shows", "teatro", "exposicoes", "danca", "cinema", "outros");
        assertThat(total).isEqualTo(6);
    }

    @Test
    void categoriaPadraoDoAgenteExiste() {
        String name = jdbcTemplate.queryForObject("SELECT name FROM category WHERE slug = 'outros'", String.class);

        assertThat(name).isEqualTo("Outros");
    }

    @Test
    void slugDuplicadoERejeitadoInclusiveODaCargaInicial() {
        assertThat(sqlStateOf(() -> insertCategory(null, "Outros 2", "outros"))).isEqualTo(UNIQUE_VIOLATION);

        insertFixtureCategory("rock", "Rock", "shows", 10);
        assertThat(sqlStateOf(() -> insertCategory(null, "Rock 2", FIXTURE_CATEGORY_PREFIX + "rock")))
                .isEqualTo(UNIQUE_VIOLATION);
    }

    @Test
    void paiInexistenteERejeitado() {
        assertThat(sqlStateOf(() -> jdbcTemplate.update(
                "INSERT INTO category (parent_id, name, slug) VALUES (?, ?, ?)", Long.MAX_VALUE, "Filha", "teste-filha")))
                .isEqualTo(FOREIGN_KEY_VIOLATION);
    }

    @Test
    void categoriaPaiDeSiMesmaERejeitada() {
        insertFixtureCategory("raiz", "Raiz", null, 10);

        assertThat(sqlStateOf(() -> jdbcTemplate.update(
                "UPDATE category SET parent_id = id WHERE slug = ?", FIXTURE_CATEGORY_PREFIX + "raiz")))
                .isEqualTo(CHECK_VIOLATION);
    }

    @Test
    void subcategoriaDeRaizEAceita() {
        insertFixtureCategory("rock", "Rock", "shows", 10);

        Long parentId = jdbcTemplate.queryForObject(
                "SELECT parent_id FROM category WHERE slug = ?", Long.class, FIXTURE_CATEGORY_PREFIX + "rock");
        Long showsId = jdbcTemplate.queryForObject("SELECT id FROM category WHERE slug = 'shows'", Long.class);
        assertThat(parentId).isEqualTo(showsId);
    }

    @Test
    void terceiroNivelNaInsercaoERejeitadoENenhumaLinhaECriada() {
        insertFixtureCategory("rock", "Rock", "shows", 10);

        assertThat(sqlStateOf(() -> insertFixtureCategory("indie", "Indie", FIXTURE_CATEGORY_PREFIX + "rock", 10)))
                .isEqualTo(CHECK_VIOLATION);
        assertThat(countBySlug(FIXTURE_CATEGORY_PREFIX + "indie")).isZero();
    }

    @Test
    void raizComSubcategoriasNaoPodeVirarSubcategoria() {
        insertFixtureCategory("raiz", "Raiz", null, 10);
        insertFixtureCategory("filha", "Filha", FIXTURE_CATEGORY_PREFIX + "raiz", 10);

        assertThat(sqlStateOf(() -> jdbcTemplate.update(
                "UPDATE category SET parent_id = (SELECT id FROM category WHERE slug = 'shows') WHERE slug = ?",
                FIXTURE_CATEGORY_PREFIX + "raiz")))
                .isEqualTo(CHECK_VIOLATION);
        Long parentId = jdbcTemplate.queryForObject(
                "SELECT parent_id FROM category WHERE slug = ?", Long.class, FIXTURE_CATEGORY_PREFIX + "raiz");
        assertThat(parentId).isNull();
    }

    @Test
    void raizNaoPodeSerMovidaParaBaixoDeSubcategoria() {
        insertFixtureCategory("rock", "Rock", "shows", 10);
        insertFixtureCategory("raiz", "Raiz", null, 20);

        assertThat(sqlStateOf(() -> jdbcTemplate.update(
                "UPDATE category SET parent_id = (SELECT id FROM category WHERE slug = ?) WHERE slug = ?",
                FIXTURE_CATEGORY_PREFIX + "rock", FIXTURE_CATEGORY_PREFIX + "raiz")))
                .isEqualTo(CHECK_VIOLATION);
    }

    @Test
    void fixtureDeUmTesteNaoVazaParaOutro() {
        // O teste anterior (qualquer ordem) insere a mesma fixture; nenhuma pode sobrar.
        Integer leftovers = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM category WHERE slug = ?", Integer.class, FIXTURE_CATEGORY_PREFIX + "vazamento");
        insertFixtureCategory("vazamento", "Vazamento", null, 10);

        assertThat(leftovers).isZero();
    }

    @Test
    void mesmaFixtureEmOutroTesteNaoColide() {
        // Espelha fixtureDeUmTesteNaoVazaParaOutro: se a limpeza falhar, o UNIQUE de slug quebra um dos dois.
        insertFixtureCategory("vazamento", "Vazamento", null, 10);

        assertThat(countBySlug(FIXTURE_CATEGORY_PREFIX + "vazamento")).isEqualTo(1);
    }

    private void insertCategory(Long parentId, String name, String slug) {
        jdbcTemplate.update("INSERT INTO category (parent_id, name, slug) VALUES (?, ?, ?)", parentId, name, slug);
    }

    private int countBySlug(String slug) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM category WHERE slug = ?", Integer.class, slug);
    }

    /** Executa a escrita esperada como falha e devolve o SQLSTATE do Postgres. */
    private static String sqlStateOf(Runnable write) {
        DataAccessException failure = catchThrowableOfType(DataAccessException.class, write::run);
        assertThat(failure).as("a escrita deveria ser rejeitada pelo banco").isNotNull();
        Throwable cause = failure;
        while (cause != null && !(cause instanceof SQLException)) {
            cause = cause.getCause();
        }
        assertThat(cause).as("causa SQL").isNotNull();
        return ((SQLException) cause).getSQLState();
    }
}
