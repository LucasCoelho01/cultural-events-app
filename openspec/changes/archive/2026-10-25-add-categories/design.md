# Design

## Context

- `locations` fixou o padrão a seguir: openapi-generator (`spring`, `interfaceOnly`, `useTags`) gera `CategoriesApi` e os DTOs em `com.culturalevents.shared.api[.model]`; o controller em `<capability>.api` implementa a interface; camadas `api -> application -> domain <- infrastructure` verificadas por ArchUnit; entidades JPA imutáveis compatíveis com `ddl-auto=validate`; `NotFoundException` do `shared.error` vira 404 `Problem` pelo handler global.
- `GlobalExceptionHandler` já devolve 400 para parâmetro de path/query inválido e 405 para método não suportado; nada a mudar nele.
- Migrations são aplicadas por Docker Compose (`initdb.d`, via `psql`) e, nos testes, por `AbstractPostgresIntegrationTest`, que executa o arquivo inteiro com `Statement.execute` (JDBC do Postgres aceita múltiplos comandos e blocos `$$`).
- `category` pertence ao back-end e só muda por scripts SQL numerados; o agente da fase 3 apenas lê (lookup por `slug`, fallback em `outros`). Como scripts manuais escrevem direto no banco, sem passar pela aplicação, a invariante de 2 níveis precisa viver no banco.

Motivação e escopo: ver proposal.md.

## Goals / Non-Goals

**Goals:**
- Taxonomia de 2 níveis, com invariantes garantidas pelo banco independentemente de quem grava.
- Leitura em árvore e por slug, no mesmo padrão de `locations`.

**Non-Goals:**
- Endpoints de escrita; cadastro de subcategorias na carga inicial (entram por scripts V00N futuros).
- Paginação, filtros, busca textual ou cache: a taxonomia tem dezenas de linhas.
- Grants da role `agent_writer` (o SELECT em `category` é definido na fase 3; escrita nunca é concedida) e vínculo de eventos a categorias.
- Impedir por trigger a remoção de uma raiz com filhos ou da categoria `outros`: a FK padrão (`NO ACTION`) já bloqueia remover pai com filhos; proteger `outros` fica para quando houver quem remova linhas.

## Decisions

**1. Arquivos e mudanças de contrato**
- SQL novo: `database/migrations/V003__create_categories.sql`, com:
  - tabela `category` exatamente como pedido (`id BIGSERIAL PK`, `parent_id BIGINT REFERENCES category(id)`, `name VARCHAR(80) NOT NULL`, `slug VARCHAR(80) NOT NULL UNIQUE`, `sort_order INT NOT NULL DEFAULT 0`, `CHECK (parent_id <> id)`);
  - índice `CREATE INDEX category_parent_id_idx ON category (parent_id)`, porque o Postgres não indexa FKs sozinho e o trigger e a busca de filhos filtram por `parent_id`;
  - função PL/pgSQL `category_enforce_max_depth()` e trigger `BEFORE INSERT OR UPDATE OF parent_id ON category FOR EACH ROW` (ver Decisão 2);
  - seed das 6 raízes com `sort_order` 10, 20, 30, 40, 50, 60 (Shows, Teatro, Exposições, Dança, Cinema, Outros; slugs `shows`, `teatro`, `exposicoes`, `danca`, `cinema`, `outros`). Os saltos de 10 deixam espaço para inserir categorias entre as existentes sem renumerar.
- OpenAPI (`api/openapi.yaml`), tag `categories`:
  - `GET /categories` → `200 Category[]`; `GET /categories/{slug}` (`slug` string `maxLength: 80`) → `200 Category`, `400 Problem` (`$ref` à resposta `BadRequest`), `404 Problem`.
  - Schema novo `Category {id, name, slug, sortOrder, parentSlug (nullable), children: Category[]}`; auto-referência suportada pelo gerador. `parentSlug` é `nullable`; os demais campos, incluindo `children`, são obrigatórios na resposta.
- Sem mudança em `pom.xml`: a execução do gerador já cobre o arquivo único; saem `CategoriesApi` e `Category` em `com.culturalevents.shared.api[.model]`, e o controller em `categories.api` implementa `CategoriesApi`.

**2. Limite de 2 níveis por trigger**
- A função, ao inserir ou atualizar `parent_id`, lança `RAISE EXCEPTION ... USING ERRCODE = 'check_violation'` (SQLSTATE `23514`, a mesma família da `CHECK`) quando:
  1. `NEW.parent_id` não é nulo e o pai referenciado tem `parent_id` não nulo (terceiro nível); ou
  2. `NEW.parent_id` não é nulo, a operação é `UPDATE` e já existem linhas com `parent_id = NEW.id` (raiz com filhos virando filha).
- A consulta ao pai usa `SELECT ... FOR SHARE`, que bloqueia reparentar o pai por outra transação até o commit e reduz a janela de corrida entre escritores simultâneos. A corrida residual (filho inserido enquanto o pai vira filho em transações simultâneas) é aceita: só scripts SQL numerados e revisados escrevem em `category`, e eles rodam sequencialmente.
- Alternativas descartadas: (a) coluna `level` com `CHECK` composto — duplica informação e exigiria FK composta `(parent_id, 0)`; (b) FK composta `(parent_id, parent_level)` com `level SMALLINT CHECK (level IN (1,2))` — exigiria mudar as colunas pedidas e expõe um campo derivado; (c) validar só na aplicação — scripts SQL manuais escrevem direto no banco.
- `UPDATE OF parent_id` evita rodar o trigger em mudanças de `name`/`sort_order`.

**3. Camadas do pacote `categories`**
- `domain`: entidade JPA `Category` (`@Entity @Immutable`, só `jakarta.persistence`/Hibernate; `parentId` como coluna `Long` simples, **sem** `@ManyToOne`, igual a `City.stateUf`) e a porta `CategoryRepository` (`findAllOrdered()`, `findBySlug(slug)`, `findChildrenOrdered(parentId)`).
- `application`: `CategoriesService` (`@Transactional(readOnly = true)`) com `listTree()` e `getBySlug(slug)`; devolve `CategoryNode` (record: categoria, `parentSlug` e `children`), montado em memória.
- `infrastructure`: `CategoryRepositoryAdapter` sobre `SpringDataCategoryRepository` (ordenação `sortOrder, name, id`).
- `api`: `CategoriesController implements CategoriesApi` e `CategoriesMapper` (record `CategoryNode` → DTO). `getBySlug` desconhecido lança `NotFoundException` (404 pelo handler global).
- Alternativa descartada: `@OneToMany children` lazy na entidade — traria `LazyInitializationException` e N+1; com poucas dezenas de linhas, uma única leitura de todas as categorias e agrupamento por `parentId` em memória é mais simples e usa 1 consulta.

**4. Montagem da árvore**
- `listTree()` lê todas as linhas ordenadas por `sort_order, name, id` e agrupa por `parent_id`; raízes = `parent_id` nulo. Como a leitura já vem ordenada, filhos e raízes herdam a ordem sem reordenar. Um `parent_id` apontando para pai inexistente é impossível pela FK; linhas em nível 3 são impossíveis pelo trigger, então a montagem em 2 níveis é total.
- `getBySlug(slug)`: lê a categoria por slug, o slug do pai (se houver) e os filhos ordenados (vazio para subcategorias). São até 3 consultas simples por chamada.

**5. Mapeamento JPA compatível com `validate`**
- `id` com `GenerationType.IDENTITY`; `name`/`slug` `@Column(length = 80, nullable = false)`; `sort_order` como `int`; `parent_id` como `Long` anulável. A entidade é imutável (`@Immutable`, sem setters), como `City`.

**6. Testes**
- Unitários: `CategoriesService` com repositório mockado (árvore, ordenação com empate, 404) e `CategoriesMapper`.
- Integração (`extends AbstractPostgresIntegrationTest`, `@AutoConfigureMockMvc`): endpoints (árvore da carga inicial, raiz com filhos, subcategoria, 404, slug de 81 caracteres → 400, 405 em POST/DELETE) e um teste de schema/trigger via `JdbcTemplate` (slug duplicado, pai inexistente, auto-pai, terceiro nível na inserção, reparentar raiz com filhos, filho de subcategoria por atualização) afirmando `SQLSTATE` `23505`/`23503`/`23514`.
- Fixtures: slugs com prefixo `teste-`; a classe base ganha um `@AfterEach` que remove fixtures de categoria (primeiro as subcategorias, depois as raízes) sem tocar a carga inicial. Isso muda `AbstractPostgresIntegrationTest` (`src/test`), mantendo o contrato de que nenhum teste deixa dados.
- ArchUnit existente passa a ter também as classes de `categories`.

## Risks / Trade-offs

- [Trigger PL/pgSQL não é executado pelo Hibernate nem aparece no `validate`] → coberto pelos testes de integração do trigger, que rodam sobre as migrations reais.
- [Corrida residual entre escritores simultâneos pode gerar 3 níveis] → `FOR SHARE` reduz a janela; documentado como aceito dado o regime de escrita (apenas scripts SQL numerados, executados em sequência).
- [Carga inicial só com raízes; subcategorias dependem de scripts futuros] → a API devolve `children` vazio e os testes criam subcategorias como fixture.
- [`children` aninhado no schema OpenAPI recursivo] → o gerador suporta `$ref` recursivo; se gerar problema, fallback é `Category` (raiz) com `children: Subcategory[]`, sem mudar o JSON.
- [Ordenar por `name` sem collation explícita varia por locale do banco] → usada só como desempate; o `sort_order` é a ordem de produto.

## Migration Plan

1. Criar V003 e aplicá-lo localmente (`docker compose down -v && docker compose up -d`, pois o `initdb.d` só roda em volume novo) ou executar o script manualmente num banco existente.
2. Publicar o código; V003 não é editado após o merge (ajustes entram como V004+).
3. Rollback: reverter o código e, em ambientes descartáveis, `DROP TABLE category; DROP FUNCTION category_enforce_max_depth();`; não há dependentes ainda.
