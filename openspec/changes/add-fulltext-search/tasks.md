# Tasks

## 1. Contrato (antes do código)

- [ ] 1.1 Adicionar o parâmetro `q` (`string`, `minLength: 2`, `maxLength: 100`, descrição com a regra do trim e da ordenação por relevância) a `listEvents` em `api/openapi.yaml`. Verificar: `./mvnw generate-sources` gera `listEvents(..., String q, ...)` sem erro e o `openapi.yaml` valida.
- [ ] 1.2 Regenerar `frontend/src/api/schema.ts` a partir do `openapi.yaml`. Verificar: o script de geração do front termina sem erro e `GET /events` aceita `q` na tipagem.

## 2. Banco: V006

- [ ] 2.1 Criar `database/migrations/V006__add_event_search.sql`: coluna `event.search_vector tsvector NOT NULL DEFAULT ''`, função `event_build_search_vector` (pesos A título, B categoria+local, C descrição; `portuguese` + `unaccent`) e trigger `event_set_search_vector` em `BEFORE INSERT OR UPDATE OF title, description, category_id, venue_id`. Verificar: aplicar a V006 em um Postgres limpo (V001-V005) sem erro.
- [ ] 2.2 Na mesma V006: trigger `venue_refresh_event_search` (`AFTER UPDATE OF name ON venue`) que recalcula os eventos do local; comentário registrando a limitação de categoria e o `UPDATE event SET title = title` de refresh; backfill (`UPDATE event SET title = title`) e depois o índice GIN `event_search_vector_idx`. Verificar: `\d event` mostra o índice GIN e eventos pré-existentes têm vetor não vazio.
- [ ] 2.3 Teste de integração (Testcontainers) de schema em `EventsSchemaIntegrationTest` ou novo `EventsSearchSchemaIntegrationTest`: backfill de eventos criados com V001-V005, vetor sobrescrito quando o escritor envia `search_vector`, evento sem descrição/local, "musica" casa "Música", "show" casa "shows", evento inserido por SQL direto (simulando o agente) tem vetor, renomear local atualiza a busca, renomear categoria por `UPDATE` direto não atualiza (documenta a limitação). Verificar: testes passam.
- [ ] 2.4 Garantir que `MigrationNamingConventionTest` e `PlatformFoundationIntegrationTest` continuam passando com a V006. Verificar: `./mvnw test -Dtest=MigrationNamingConventionTest,PlatformFoundationIntegrationTest`.

## 3. Back-end

- [ ] 3.1 Criar `InvalidRequestParameterException` em `shared.error` e tratá-la em `GlobalExceptionHandler` como HTTP 400 `Problem` ("Parâmetro de requisição inválido: q."). Teste unitário em `GlobalExceptionHandlerTest`. Verificar: o teste passa.
- [ ] 3.2 Adicionar `String query` a `EventFilter`; `EventsController.listEvents` repassa `q` com `strip()` e `EventsService.list` valida 2 a 100 caracteres após o trim (lança a exceção de 3.1) e monta o filtro. Testes unitários em `EventsServiceTest` (trim, 1 e 101 caracteres, vazio, ausente). Verificar: testes passam.
- [ ] 3.3 Implementar a busca nativa em `EventRepositoryAdapter` (só quando `query != null`): SQL com `search_vector @@ websearch_to_tsquery('portuguese', unaccent(:q))`, os mesmos filtros do JPQL, `count`, `ORDER BY ts_rank desc, próxima sessão, última sessão, id`, `LIMIT/OFFSET`, carga das entidades por ids e reordenação. Sem `q`, o caminho JPQL permanece. Verificar: `EventsContextIntegrationTest` (`ddl-auto=validate`) e `ArchitectureTest` passam.
- [ ] 3.4 Testes de integração em novo `EventsSearchApiIntegrationTest` (Testcontainers): acento, plural ("shows" e "show"), peso do título acima da descrição (inclusive com sessão mais tardia), empate resolvido pela próxima sessão, `q` combinado com `category`, `from`/`to` e `status`, paginação com `q`, `q` sem resultado e só com palavra ignorada, sintaxe especial (`"show -rock`) sem 500, `q` com 1, `"  a "`, vazio e 101 caracteres → 400, `q` com 2, 100 e `"  show  "` → 200, categoria inexistente com `q` → 404, evento inserido por SQL direto aparece na busca, e listagem sem `q` inalterada. Verificar: `./mvnw test` verde.

## 4. Front-end

- [ ] 4.1 `filterParams.ts`: `q` em `CatalogFilters`/`FILTER_KEYS`, `parseFilters` ignora `q` com menos de 2 ou mais de 100 caracteres após trim, `withFilters`/`clearFilters` tratam `q` e removem `page`. Testes em `filterParams.test.ts`. Verificar: `npm test` passa.
- [ ] 4.2 `api/events.ts`: `useEvents` aceita `q`, inclui na `queryKey` e na requisição. Verificar: teste de página confere `q` na query do `GET /events`.
- [ ] 4.3 Componente `SearchBox` (form `role="search"`, rótulo "Buscar eventos", botão "Buscar", mensagem "Digite ao menos 2 caracteres", `maxLength` 100, sincronizado com `?q=`) acima de `CatalogFilters` em `EventList`; aplicar só ao enviar. Verificar: testes de 4.5.
- [ ] 4.4 `EventList`: `filtering`/`hasFilters` incluem `q`; estado vazio `Nenhum evento para "<termo>"` com busca ativa; "Limpar filtros" limpa também o campo. Verificar: testes de 4.5.
- [ ] 4.5 Testes com MSW e `zz/cidade-teste` em `search.test.tsx`: Enter e botão aplicam; digitar não requisita; busca na página 3 volta à página 1 mantendo `categoria`; termo de 1 caractere bloqueado; campo vazio remove `q`; URL com `q` válido preenche o campo e envia `q`; `q` inválido na URL ignorado; "Próxima" mantém `q`; "Limpar filtros" (também só com `q`) esvazia campo e URL; vazio com busca mostra `Nenhum evento para "<termo>"`; HTTP 400 mostra `ErrorMessage`. Verificar: `npm test`, `npm run lint` e `npm run build` verdes.

## 5. Postman

- [ ] 5.1 Em `postman/cultural-events.postman_collection.json`: adicionar "7a. Buscar eventos por texto (q)" (`{{baseUrl}}/events?q=musica`, testes de status 200 e `items` como lista) após a 7, e "q curto → 400" (`?q=a`, status 400 e `status` do `Problem`) em "Cenários de erro". Verificar: coleção é JSON válido, importa no Postman/`postman collection run` e as duas requisições passam contra o back-end local.

## 6. Verificação final

- [ ] 6.1 Rodar `./mvnw verify` (back-end), `npm test && npm run lint && npm run build` (front) e `openspec validate add-fulltext-search`. Verificar: tudo verde; teste manual: `GET /api/v1/events?q=musica` com um evento "Música" criado via SQL direto.
