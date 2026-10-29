# Design

## Context

- `event` (V005) não tem coluna de busca ("`search_vector` ... ficam para as fases 2 e 3"). `unaccent` e `pg_trgm` já existem (V001). `slugify()` usa `unaccent`, que é `STABLE`, não `IMMUTABLE`.
- `GET /events` é implementado em `EventRepositoryAdapter` com JPQL montado por strings e `EntityManager`; a ordenação usa subselects sobre `Occurrence`. `EventFilter` é um record do domínio.
- O agente Python grava direto no Postgres (`agent_writer`, só DML): qualquer regra de busca precisa viver no banco. Padrão do projeto: triggers que preenchem `slug` e `updated_at`.
- Categorias só mudam por script numerado (API somente leitura). Locais são editáveis pela API de `venues`.
- Erros de parâmetro de query: `ConstraintViolationException` de parâmetro vira 400 em `GlobalExceptionHandler`; `FieldValidationException` vira 422.

## Goals / Non-Goals

**Goals:**
- Busca por título, categoria, local e descrição, sem acento, com singular/plural, correta para eventos inseridos pelo agente.
- Ordem por relevância; ordem atual intacta sem `q`.
- Manter `ddl-auto=validate`, as regras de ArchUnit e o contrato-primeiro.

**Non-Goals:**
- Sugestões/autocomplete, destaque de trechos, busca por similaridade (`pg_trgm`) e tolerância a erro de digitação.
- Busca em outros campos (bairro, endereço) ou em outras entidades.
- Mudança na tela de admin (`useAdminEvents` não usa `q`).

## Decisions

### 1. Vetor mantido por trigger `BEFORE INSERT OR UPDATE`, não coluna gerada

`GENERATED ALWAYS AS` não aceita subconsulta (nome da categoria e do local) nem funções não `IMMUTABLE` (`unaccent`). O trigger calcula, em uma função `event_build_search_vector(title, description, category_id, venue_id)`:

```sql
setweight(to_tsvector('portuguese', unaccent(coalesce(title, ''))), 'A') ||
setweight(to_tsvector('portuguese', unaccent(coalesce(category_name, '') || ' ' || coalesce(venue_name, ''))), 'B') ||
setweight(to_tsvector('portuguese', unaccent(coalesce(description, ''))), 'C')
```

- Nome: `event_set_search_vector`, `BEFORE INSERT OR UPDATE OF title, description, category_id, venue_id`. Roda depois de `event_fill_slug` (ordem alfabética) e sobrescreve qualquer `search_vector` enviado.
- Alternativa descartada: calcular na aplicação. Quebra o requisito de o agente escrever direto.
- Alternativa descartada: `unaccent` embutido num dicionário (`CREATE TEXT SEARCH CONFIGURATION`). Funcionaria e evitaria chamar `unaccent()` na consulta, mas é mais DDL para manter; a função explícita é mais legível e a query usa a mesma expressão.
- `unaccent()` fica chamada com o dicionário padrão da extensão (schema `public`); a função usa `SET search_path` apenas se os testes mostrarem necessidade.

### 2. Atualização ao renomear: local sim, categoria não

- **Local**: trigger `venue_refresh_event_search` `AFTER UPDATE OF name ON venue` que recalcula o vetor dos eventos do local. O local é editável pela API (`PUT /venues/{id}`), então o vetor desatualizado seria um defeito visível. Custo: um `UPDATE` por evento do local, só quando o nome muda. Esse `UPDATE` passa por `set_updated_at`, então `updated_at` dos eventos avança (sem mudar `version`); aceito, pois o texto exibido na busca de fato mudou.
- **Categoria**: **limitação aceita**. A tabela só muda por script numerado e renomear é raro; um trigger tocaria potencialmente muitos eventos numa operação que já é manual. Regra: o script SQL que renomear uma categoria deve incluir `UPDATE event SET title = title WHERE category_id = ...` (o trigger recalcula). Fica no spec e em comentário da V006.
- Alternativa descartada: recalcular na consulta (join com nome atual). Invalida o índice GIN.

### 3. Backfill na própria V006

`UPDATE event SET title = title` aciona o trigger e preenche tudo, no mesmo script, depois de criar a função e o trigger. O índice GIN é criado **depois** do backfill, para construí-lo de uma vez só. Efeito colateral: `updated_at` dos eventos existentes avança uma vez (aceito, `version` não muda). A coluna fica `NOT NULL DEFAULT ''::tsvector` para não exigir tratamento de nulo (o trigger sempre preenche).

### 4. Consulta: `websearch_to_tsquery` com `unaccent`

```sql
search_vector @@ websearch_to_tsquery('portuguese', unaccent(:q))
```

`websearch_to_tsquery` nunca falha por sintaxe (aspas abertas, `-`, `OR`), sem tratamento de erro na aplicação. `q` só com palavras ignoradas (`de`, `a`) resulta em consulta vazia e zero resultados (200, vazio). Relevância: `ts_rank(search_vector, query)` (pesos padrão `{0.1, 0.2, 0.4, 1.0}` já fazem A > B > C).

### 5. Back-end: query nativa só no caminho com `q`

Opções:
1. **Query nativa (escolhida)**: no adaptador, quando `filter.query() != null`, executa SQL nativo que devolve `id` e conta o total; as entidades são carregadas por JPQL (`where e.id in :ids`) e reordenadas pela ordem dos ids. Sem `q`, o JPQL atual segue intocado, sem risco de regressão.
2. Função HQL registrada (`FunctionContributor`) + atributo `searchVector` na entidade: exige mapear `tsvector` (tipo desconhecido pelo Hibernate, falha em `validate` ou exige `@JdbcTypeCode`/tipo customizado) e registro por service loader. Muito mais infraestrutura para um único uso.
3. Spring Data `@Query(nativeQuery)` na interface JPA: o módulo usa adaptador com `EntityManager`; manteria dois estilos.

Escolha 1. O SQL nativo replica os filtros (`city_id`, `category_id IN`, `status`, `EXISTS` de sessões `from`/`to`); para evitar divergência, os testes de integração de filtros rodam com e sem `q`. A paginação (`LIMIT/OFFSET`) e o `count` rodam no SQL. A consulta nativa fica em `events.infrastructure`; `EntityManager` já é usado ali, então ArchUnit não muda. `search_vector` não é mapeado na entidade, então `validate` ignora a coluna.

Ordem: `ts_rank desc, próxima sessão asc nulls last, última sessão desc nulls last, id asc` (mesmas expressões do `ORDER BY` atual).

### 6. Contrato e validação de `q`

- `openapi.yaml`: `q` (`string`, `minLength: 2`, `maxLength: 100`) em `listEvents`, atualizado antes do código. O gerador valida os limites do texto bruto (HTTP 400 via `ConstraintViolationException`).
- O `trim` muda o resultado (`"  a "`, `"   "`): `EventsController` faz `q.strip()`, e `EventsService.list` valida o tamanho após o trim, lançando uma exceção que vira **HTTP 400** (hoje só existe 422 para `FieldValidationException`). Será adicionada em `shared.error` uma `InvalidRequestParameterException` tratada em `GlobalExceptionHandler` com o mesmo corpo de "Parâmetro de requisição inválido: q."; `FieldValidationException` não serve (422).
- `EventFilter` ganha `String query` (já aparado, ou `null`).

### 7. Front-end

- `filterParams.ts`: `q` entra em `CatalogFilters`/`FILTER_KEYS`/`parseFilters` (descarta o inválido) e em `withFilters`/`clearFilters`; `hasFilters` passa a considerar `q`.
- Novo `SearchBox` (form `role="search"`, input controlado por estado local sincronizado com `?q=`, botão "Buscar"); renderizado acima de `CatalogFilters` em `EventList`. Enviar chama `withFilters` (remove `page`).
- `useEvents` recebe `q`, que entra na `queryKey` e na requisição (`...(q && { q })`).
- Estado vazio: `filtering` passa a incluir `q`; com `q` mostra `Nenhum evento para "<termo>"`.
- `api/schema.ts` é regenerado do `openapi.yaml` (arquivo ignorado pelo git).

### 8. Postman

Nova requisição "7a. Buscar eventos por texto (q)" depois da 7, e "q curto → 400" em "Cenários de erro", com testes de status e ordem.

## Risks / Trade-offs

- [Vetor desatualizado ao renomear categoria] → limitação documentada; o script de renomeação refresca (`UPDATE event SET title = title`).
- [Backfill dentro da V006 bloqueia a tabela e avança `updated_at`] → volume pequeno nesta fase; aceito.
- [SQL nativo duplica os filtros do JPQL] → testes de integração cobrem `q` combinado com `category`/`from`/`to`/`status`/`city`.
- [`unaccent` e dicionário `portuguese` dependem da configuração do Postgres] → Testcontainers usa a mesma imagem do Docker Compose (Postgres 16); `unaccent` já é exigido pela V001.
- [Stemmer português trata "shows"/"show" e "música"/"musica" conforme o dicionário] → coberto por teste de integração; se divergir, ajustar a V006 antes do merge (ela ainda não foi mergeada).
- [Renomear local reescreve `updated_at` dos eventos] → aceito, sem efeito em `version`.

## Migration Plan

1. Aplicar `V006__add_event_search.sql` (coluna, função, triggers, backfill, índice GIN). Não edita V001-V005.
2. Subir o back-end novo; sem a V006 a busca com `q` falha, a listagem sem `q` continua (coluna não mapeada).
3. Rollback: remover os triggers de `venue` e `event`, as funções, o índice e a coluna por um script `V007` (a V006 não é editada depois do merge) e reimplantar o back-end anterior, que não conhece `q`.
