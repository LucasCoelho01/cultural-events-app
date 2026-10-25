# Tasks

## 1. Schema, trigger e carga inicial

- [ ] 1.1 Criar `database/migrations/V003__create_categories.sql` com a tabela `category` (`id BIGSERIAL PK`, `parent_id` FK para `category(id)`, `name VARCHAR(80) NOT NULL`, `slug VARCHAR(80) NOT NULL UNIQUE`, `sort_order INT NOT NULL DEFAULT 0`, `CHECK (parent_id <> id)`), o índice em `parent_id`, a função `category_enforce_max_depth()` e o trigger `BEFORE INSERT OR UPDATE OF parent_id` (erro `check_violation`/`23514`), e o seed das 6 raízes com `sort_order` 10 a 60; verificar aplicando V001..V003 num Postgres 16 limpo e conferindo 6 linhas raiz em `category` (`MigrationNamingConventionTest` continua verde)
- [ ] 1.2 Estender `AbstractPostgresIntegrationTest` com um `@AfterEach` que remove fixtures de categoria (slug com prefixo `teste-`, subcategorias antes das raízes) e um helper `insertFixtureCategory(slug, name, parentSlug, sortOrder)`, sem tocar a carga inicial; verificar com dois testes que inserem a mesma fixture e passam em qualquer ordem
- [ ] 1.3 Criar `CategoriesSchemaIntegrationTest` (estende a classe base, JDBC) cobrindo: tabela e colunas criadas, seed com 6 raízes na ordem esperada e `outros` presente, slug duplicado rejeitado (`23505`, inclusive `outros`), pai inexistente rejeitado (`23503`), auto-pai rejeitado (`23514`), subcategoria de raiz aceita, terceiro nível na inserção rejeitado (`23514`), raiz com filhos virando filha rejeitada (`23514`), raiz sendo movida para baixo de subcategoria rejeitada (`23514`); verificar `./mvnw test -Dtest=CategoriesSchemaIntegrationTest` passando

## 2. Contrato OpenAPI (antes do código)

- [ ] 2.1 Atualizar `api/openapi.yaml` com a tag `categories`, os paths `GET /categories` (200 `Category[]`) e `GET /categories/{slug}` (`slug` com `maxLength: 80`; 200 `Category`, 400 via `BadRequest`, 404 `Problem`) e o schema recursivo `Category` (`id`, `name`, `slug`, `sortOrder`, `parentSlug` nullable, `children`); verificar com `./mvnw generate-sources` gerando `CategoriesApi` e `Category` em `target/generated-sources`
- [ ] 2.2 Conferir que a interface gerada tem `@Size(max = 80)` em `slug` e que `children` é `List<Category>` não nulo; verificar inspecionando `CategoriesApi.java` e `Category.java` gerados

## 3. Domínio, infraestrutura e aplicação

- [ ] 3.1 Criar o pacote `com.culturalevents.categories.{api,application,domain,infrastructure}` com a entidade JPA imutável `Category` (`parentId` como `Long`, sem `@ManyToOne`) e a porta `CategoryRepository` (`findAllOrdered`, `findBySlug`, `findChildrenOrdered`) em `domain`; verificar que `ArchitectureTest` passa com as classes reais
- [ ] 3.2 Implementar `CategoryRepositoryAdapter` sobre `SpringDataCategoryRepository` com ordenação `sortOrder, name, id`; verificar com `CategoriesContextIntegrationTest` (estende a classe base; contexto sobe com `ddl-auto=validate` e a tabela `category`)
- [ ] 3.3 Implementar `CategoriesService` (`listTree` montando a árvore em memória a partir de uma leitura ordenada; `getBySlug` com `parentSlug` e filhos ordenados; `NotFoundException` para slug inexistente) e o record `CategoryNode`; verificar com testes unitários (Mockito) cobrindo árvore com filhos, raiz sem filhos (lista vazia), ordem com empate em `sort_order` desempatando por nome, subcategoria por slug e slug inexistente

## 4. API

- [ ] 4.1 Implementar `CategoriesController implements CategoriesApi` e `CategoriesMapper` (`CategoryNode` → DTO, `children` nunca nulo); verificar com teste unitário do mapper (raiz, subcategoria com `parentSlug`, folha com `children` vazio)
- [ ] 4.2 Criar `CategoriesApiIntegrationTest` (estende a classe base, MockMvc) para `GET /categories`: 6 raízes da carga inicial na ordem Shows..Outros, raiz com subcategoria fixture trazendo `children` e `parentSlug`, `cinema` com `children` vazio, subcategorias com empate de `sort_order` ordenadas por nome, `POST /categories` → 405 `Problem`; fixtures removidas ao final de cada teste; verificar execução verde
- [ ] 4.3 Cobrir `GET /categories/{slug}` no mesmo teste ou em `CategoryBySlugApiIntegrationTest`: raiz com filhos (200), subcategoria (200, `parentSlug` e `children` vazio), slug inexistente 404 `Problem` com `status=404`, slug de 81 caracteres 400 `Problem` citando `slug`, `DELETE /categories/shows` → 405 `Problem` e a categoria permanece; verificar execução verde

## 5. Documentação e verificação final

- [ ] 5.1 Atualizar o `README.md` com a seção de categorias (como cadastrar categoria/subcategoria por script SQL numerado, limite de 2 níveis garantido por trigger, `outros` como destino padrão do agente, recriar o volume do Docker para aplicar V003); verificar que os comandos documentados rodam como escritos
- [ ] 5.2 Rodar `./mvnw clean verify` em `backend/` e `docker compose down -v && docker compose up -d` conferindo a tabela `category` populada; verificar build verde e `SELECT slug FROM category ORDER BY sort_order` retornando as 6 raízes na ordem

## Workflow follow-up

- Validar a change (`openspec validate add-categories --strict`) e arquivá-la após o merge.
