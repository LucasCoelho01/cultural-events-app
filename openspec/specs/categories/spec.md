# categories Specification

## Purpose
A capability `categories` mantém a taxonomia somente leitura de categorias e subcategorias de eventos (até 2 níveis) e expõe sua consulta por API. É o conjunto fechado de classificações que eventos e o agente de ingestão usarão, com `outros` como destino padrão.

## Requirements

### Requirement: Schema de categorias
O banco SHALL ter a tabela `category` (`id` BIGSERIAL PK, `parent_id` BIGINT referenciando `category(id)`, `name` VARCHAR(80) NOT NULL, `slug` VARCHAR(80) NOT NULL UNIQUE, `sort_order` INT NOT NULL DEFAULT 0, `CHECK (parent_id <> id)`), criada pelo script `V003__create_categories.sql`.

#### Scenario: Migration aplicada em banco limpo
- **WHEN** os scripts de `database/migrations` são aplicados em ordem a um Postgres vazio
- **THEN** a tabela `category` existe com as colunas, chaves e restrições especificadas

#### Scenario: Slug duplicado é rejeitado
- **WHEN** é inserida uma categoria com `slug` já existente, em qualquer nível
- **THEN** o banco rejeita a inserção por violação de unicidade

#### Scenario: Categoria pai inexistente é rejeitada
- **WHEN** é inserida uma categoria com `parent_id` que não existe em `category`
- **THEN** o banco rejeita a inserção por violação da chave estrangeira

#### Scenario: Categoria pai de si mesma é rejeitada
- **WHEN** uma categoria existente é atualizada com `parent_id` igual ao próprio `id`
- **THEN** o banco rejeita a atualização por violação da restrição `parent_id <> id`

### Requirement: Limite de dois níveis garantido no banco
O banco SHALL rejeitar, por trigger em inserção e atualização de `category`, qualquer linha que resulte em mais de 2 níveis: uma categoria com `parent_id` só é aceita se o pai for raiz (`parent_id` nulo) e se ela própria não tiver subcategorias.

#### Scenario: Subcategoria de uma raiz é aceita
- **WHEN** é inserida uma categoria cujo `parent_id` aponta para uma categoria raiz
- **THEN** o banco aceita a inserção

#### Scenario: Terceiro nível na inserção é rejeitado
- **WHEN** é inserida uma categoria cujo `parent_id` aponta para uma subcategoria
- **THEN** o banco rejeita a inserção com violação de restrição (SQLSTATE `23514`) e nenhuma linha é criada

#### Scenario: Raiz com subcategorias não pode virar subcategoria
- **WHEN** uma categoria raiz que já tem subcategorias é atualizada com um `parent_id` não nulo
- **THEN** o banco rejeita a atualização com violação de restrição (SQLSTATE `23514`) e a linha permanece raiz

#### Scenario: Subcategoria não pode ganhar subcategoria por atualização
- **WHEN** uma categoria raiz é atualizada para ter como `parent_id` uma subcategoria
- **THEN** o banco rejeita a atualização com violação de restrição (SQLSTATE `23514`)

### Requirement: Carga inicial das categorias raiz
O script `V003__create_categories.sql` SHALL inserir exatamente 6 categorias raiz, nesta ordem de `sort_order` crescente: Shows (`shows`), Teatro (`teatro`), Exposições (`exposicoes`), Dança (`danca`), Cinema (`cinema`) e Outros (`outros`), todas sem pai.

#### Scenario: Carga inicial presente
- **WHEN** as migrations são aplicadas em banco limpo
- **THEN** `category` contém exatamente 6 linhas, todas com `parent_id` nulo, cujos slugs ordenados por `sort_order` são `shows`, `teatro`, `exposicoes`, `danca`, `cinema`, `outros`

#### Scenario: Categoria padrão do agente existe
- **WHEN** as migrations são aplicadas em banco limpo
- **THEN** existe a categoria de slug `outros` e nome `Outros`, usada como destino quando não há correspondência

#### Scenario: Slug de carga inicial não pode ser reaproveitado
- **WHEN** é inserida uma categoria com slug `outros`
- **THEN** o banco rejeita a inserção por violação de unicidade

### Requirement: Listagem da árvore de categorias
A API SHALL expor `GET /api/v1/categories` (tag `categories`) retornando HTTP 200 com a lista das categorias raiz, cada uma com `id`, `name`, `slug`, `sortOrder`, `parentSlug` (nulo nas raízes) e `children` (suas subcategorias, com `children` vazio).

#### Scenario: Retorna as raízes com suas subcategorias
- **WHEN** existem as 6 raízes da carga inicial e a subcategoria `rock` sob `shows`, e o cliente envia `GET /api/v1/categories`
- **THEN** a API responde HTTP 200 com as 6 raízes, e a raiz `shows` traz `children` com um item de `slug=rock` e `parentSlug=shows`

#### Scenario: Raiz sem subcategorias traz children vazio
- **WHEN** o cliente envia `GET /api/v1/categories` e a raiz `cinema` não tem subcategorias
- **THEN** a API responde HTTP 200 e `cinema` traz `children` igual a lista vazia, nunca nulo

#### Scenario: Método de escrita é rejeitado
- **WHEN** o cliente envia `POST /api/v1/categories`
- **THEN** a API responde HTTP 405 com corpo `Problem` (`status=405`) e nenhuma linha é criada

### Requirement: Ordenação da árvore
A API SHALL ordenar as raízes e, dentro de cada raiz, as subcategorias por `sort_order` crescente e, em empate, por `name` crescente.

#### Scenario: Raízes seguem sort_order
- **WHEN** o cliente envia `GET /api/v1/categories` sobre a carga inicial
- **THEN** a API responde HTTP 200 com as raízes na ordem Shows, Teatro, Exposições, Dança, Cinema, Outros

#### Scenario: Empate em sort_order desempata por nome
- **WHEN** duas subcategorias da mesma raiz têm o mesmo `sort_order` e nomes `Zeta` e `Alfa`
- **THEN** a API responde HTTP 200 com `Alfa` antes de `Zeta` em `children`

### Requirement: Consulta de categoria por slug
A API SHALL expor `GET /api/v1/categories/{slug}` retornando HTTP 200 com a categoria de slug informado (raiz ou subcategoria), incluindo `parentSlug` e `children` ordenados, ou HTTP 404 com `Problem` quando o slug não existe. O parâmetro `slug` SHALL aceitar no máximo 80 caracteres.

#### Scenario: Raiz traz suas subcategorias
- **WHEN** o cliente envia `GET /api/v1/categories/shows` e `shows` tem a subcategoria `rock`
- **THEN** a API responde HTTP 200 com `slug=shows`, `parentSlug` nulo e `children` contendo `rock`

#### Scenario: Subcategoria traz o pai e children vazio
- **WHEN** o cliente envia `GET /api/v1/categories/rock`
- **THEN** a API responde HTTP 200 com `slug=rock`, `parentSlug=shows` e `children` vazio

#### Scenario: Slug inexistente retorna 404
- **WHEN** o cliente envia `GET /api/v1/categories/nao-existe`
- **THEN** a API responde HTTP 404 com corpo `Problem` (`status=404`, `title`, `detail` indicando que a categoria não foi encontrada)

#### Scenario: Slug acima de 80 caracteres retorna 400
- **WHEN** o cliente envia `GET /api/v1/categories/{slug}` com slug de 81 caracteres
- **THEN** a API responde HTTP 400 com corpo `Problem` (`status=400`, `detail` indicando o parâmetro `slug`) e nenhuma consulta é feita

#### Scenario: Método de escrita é rejeitado
- **WHEN** o cliente envia `DELETE /api/v1/categories/shows`
- **THEN** a API responde HTTP 405 com corpo `Problem` (`status=405`) e a categoria permanece
