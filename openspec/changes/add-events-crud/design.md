# Design

## Context

- `venues` fixou o padrão: contrato OpenAPI gera `<Tag>Api` e DTOs em `com.culturalevents.shared.api[.model]`; camadas `api -> application -> domain <- infrastructure` por ArchUnit; entidade JPA com `ddl-auto=validate`; após `saveAndFlush` o adaptador faz `EntityManager.refresh` para refletir triggers; `V004` já entrega `slugify()` e `set_updated_at()`.
- A ArchUnit proíbe `infrastructure`/`api` de depender da `application` de outra capability, mas permite `application -> application`. Por isso `events.application` consome casos de uso de `locations` (`CityLookup`, já existente), `categories` e `venues` (novos).
- Hoje `VenuesService` traduz `DataIntegrityViolationException` só pelo SQLSTATE, e a 23514 vira sempre "name". Isso não serve a `events`, que tem vários CHECKs; a tradução precisa olhar o nome da constraint. A V004 não pode ser editada, então nomes ausentes entram por `CREATE OR REPLACE` na V005.
- `GlobalExceptionHandler` já devolve 400/404/409/422 e o 422 com `errors[]` para `@Valid`; `VenuesExceptionHandler` existe só para `VenueValidationException`.
- Motivação e escopo: ver proposal.md; requisitos: specs/events e specs/venues.

## Goals / Non-Goals

**Goals:**
- Invariantes de evento (preço, domínio de `status`, cidade do local, slug, `updated_at`) no banco, para qualquer escritor.
- CRUD com sessões como parte do agregado `Event`, com lock otimista e sem acoplar `events` às classes internas de outros módulos.
- Um único caminho de erro 422 com `errors[]` para venues e events.

**Non-Goals:**
- `search_vector`, `locked_fields`, busca textual, ordenação por relevância (fases 2 e 3).
- Máquina de estados de `status`: qualquer transição é aceita.
- Grants de `agent_writer`; trigger que incrementa `version` em UPDATEs diretos (o agente deve fazer `version = version + 1`).
- Paginar as sessões de um evento; `occurrence` não tem id público.
- Corrigir o aviso W2 de venues (`PUT` idêntico não muda `updatedAt` em venues); em events o `PUT` sempre incrementa `version`.

## Decisions

**1. Arquivos e mudanças de contrato**
- SQL novo: `database/migrations/V005__create_events.sql` (V001..V004 não são editados).
- OpenAPI (`api/openapi.yaml`), tag `events`, atualizado antes do código:
  - `/events`: `GET listEvents` (query `city` com o mesmo `pattern` de `/venues`, `category` `maxLength: 80`, `from`/`to` `format: date-time`, `status` enum, `page`, `size`; respostas 200 `EventPage`, 400, 404) e `POST createEvent` (201 `Event` + `Location`, 400, 409, 422).
  - `/events/{id}` (`id` `format: uuid`): `GET getEvent` (200, 400, 404), `PUT replaceEvent` (200, 400, 404, 409, 422), `DELETE deleteEvent` (204, 400, 404).
  - Schemas: `EventInput` (`cityId`, `categoryId`, `title` obrigatórios; `venueId` uuid anulável; `slug`, `description`, `status` enum com padrão `DRAFT`, `ageRating` ≤10, `priceMinCents`/`priceMaxCents` int ≥0, `isFree` padrão false, `ticketUrl`/`imageUrl` `pattern` http(s) ≤2048, `occurrences` array de `OccurrenceInput`), `EventUpdateInput` (os mesmos campos + `version` int obrigatório; se o gerador transformar `allOf` em herança estranha, duplicam-se os campos), `OccurrenceInput` (`startsAt` obrigatório, `endsAt`, `status` enum padrão `SCHEDULED`), `Event` (campos de `EventInput` + `id`, `origin`, `version`, `createdAt`, `updatedAt`, `category` `CategoryRef`, `venue` `VenueRef` anulável, `occurrences` `Occurrence[]`), `CategoryRef {id int64, slug, name}`, `VenueRef {id uuid, name, slug}`, `Occurrence {startsAt, endsAt, status}` e `EventPage`. `ageRating` e `origin` (só leitura) são acréscimos além da lista do pedido, porque as colunas existem.
  - `components.responses`: `Conflict` e `UnprocessableEntity` já existem desde venues; `EventNotFound` é novo. `DELETE /venues/{id}` ganha a resposta 409 `Conflict` e `PUT /venues/{id}` já a tinha.
- Sem mudança em `pom.xml`.

**2. Migration V005**
- `event` e `occurrence` como pedido; `version INT NOT NULL DEFAULT 0`; índices `event_city_status_idx (city_id, status)`, `occurrence_starts_at_idx (starts_at)` e `event_category_idx (category_id)` e `event_venue_idx (venue_id)` (o Postgres não indexa FK sozinho e o filtro por categoria e o 409 do `DELETE` de local consultam essas colunas).
- Constraints nomeadas, base da tradução de erro: `event_city_slug_key` (UNIQUE), `occurrence_event_starts_at_key` (UNIQUE), `event_status_valid`, `event_origin_valid`, `event_price_min_nonneg`, `event_price_range`, `event_free_no_price` (`NOT is_free OR (COALESCE(price_min_cents,0) = 0 AND COALESCE(price_max_cents,0) = 0)`), `occurrence_time_order`, `occurrence_status_valid`.
- Triggers (todos com `RAISE ... USING ERRCODE = 'check_violation', CONSTRAINT = '<nome>'` para o nome chegar ao cliente JDBC):
  - `event_fill_slug` (`BEFORE INSERT OR UPDATE`): `slug` nulo/vazio → `left(slugify(title), 255)`; vazio → `event_slug_not_empty`.
  - `event_set_updated_at` (`BEFORE UPDATE`) reaproveitando `set_updated_at()`.
  - `event_check_venue_city` (`BEFORE INSERT OR UPDATE OF venue_id, city_id`): com `venue_id`, busca `venue.city_id` e, se diferir de `NEW.city_id`, erro `event_venue_city_match`.
- Guarda do lado do local: `venue_guard_city_change` (`BEFORE UPDATE OF city_id ON venue`): se `NEW.city_id <> OLD.city_id` e existir `event` com esse `venue_id`, `RAISE ... ERRCODE = 'restrict_violation' (23001), CONSTRAINT = 'venue_city_in_use'`. Sem ele, mudar a cidade do local quebraria a invariante que o trigger de evento só verifica na escrita do evento. É um acréscimo ao pedido (ver spec de venues).
- `CREATE OR REPLACE FUNCTION venue_fill_slug()` idêntica à da V004, apenas com `CONSTRAINT = 'venue_slug_not_empty'`.
- FKs: `event.city_id`, `category_id`, `venue_id` sem `ON DELETE` (padrão NO ACTION), de modo que apagar um local referenciado falha com 23503 (`event_venue_id_fkey`); `occurrence.event_id` `ON DELETE CASCADE`.

**3. Refatoração de erros (shared)**
- `shared.error.FieldValidationException` carrega uma lista de `FieldViolation(field, message)` (record no mesmo pacote), com construtor de conveniência para um campo. O `GlobalExceptionHandler` ganha `@ExceptionHandler(FieldValidationException)` que monta o mesmo 422 (`errors[]`) que já usa para `@Valid`, reaproveitando `unprocessableEntity`. Sai `VenueValidationException` e `VenuesExceptionHandler` (e o teste dele; o cenário vai para `GlobalExceptionHandlerTest`).
- `shared.persistence.ConstraintViolations` (helper estático, sem Spring): `sqlState(Throwable)` e `constraintName(Throwable)` percorrendo a cadeia de causas; o nome vem de `org.hibernate.exception.ConstraintViolationException#getConstraintName()` quando presente. Cada service mantém um `Map<String, FieldViolation>` de constraint → campo/mensagem e traduz: 23505 → `ConflictException` (slug) ou violação de campo (sessão duplicada); 23514/23503 → `FieldValidationException` pelo mapa; nome desconhecido → relança. Em venues, o mapa cobre `venue_latitude_range`, `venue_longitude_range`, `venue_postal_code_format`, `venue_slug_not_empty` (→ `name`) e `venue_city_id_fkey` (→ `cityId`); como a API já valida esses limites, o efeito observável só muda para escritas que contornariam o contrato.
- Alternativa descartada: tradutor genérico configurável por anotação — só dois módulos usam, e o mapa explícito é mais legível.

**4. Casos de uso entre módulos**
- `categories.application.CategoryLookup`: `findById(long)` e `findAllByIds(Collection<Long>)` → `CategoryRef(id, slug, name)`; `findIdsWithDescendantsBySlug(String)` → `Optional<List<Long>>` (a categoria e suas subcategorias diretas, no máximo 2 níveis), vazio se o slug não existir. Reaproveita `CategoryRepository`; `findAllByIds` é método novo na porta.
- `venues.application.VenueLookup`: `findByPublicId(UUID)` → `Optional<VenueRef(id, publicId, cityId, name, slug)>` e `findAllByIds(Collection<Long>)` → `List<VenueRef>`; `VenueRepository` ganha `findAllById`.
- `events.application` injeta `CityLookup`, `CategoryLookup` e `VenueLookup` e só enxerga os records `...Ref`. Regra ArchUnit nova: `..events..` não depende de `locations|categories|venues` `.domain|.api|.infrastructure`.

**5. Camadas do pacote `events`**
- `domain`: `Event` (`@Entity`, `@Version int version`, `cityId`/`categoryId`/`venueId` como `Long`, `@OneToMany(mappedBy = "event", cascade = ALL, orphanRemoval = true) @OrderBy("startsAt") @BatchSize(size = 50) List<Occurrence>`, `publicId` `@Generated(INSERT)` com `insertable = false`, `createdAt`/`updatedAt` `insertable = false, updatable = false`, `origin` `updatable = false`), `Occurrence` (`@ManyToOne event`, `startsAt`, `endsAt`, `status`), enums `EventStatus`, `OccurrenceStatus` (`@Enumerated(STRING)`), e a porta `EventRepository` (`search(EventFilter, Pageable)`, `findByPublicId`, `saveAndFlush`, `deleteAndFlush`). `EventFilter` é um record em `domain` (`cityId`, `categoryIds` ou nulo, `from`, `to`, `status`, `reference` = maior entre agora e `from`).
- `Event.replaceWith(...)` atualiza os campos e sincroniza as sessões **por `startsAt`**: sessão existente com o mesmo horário é atualizada no lugar, as ausentes saem (`orphanRemoval`), as novas entram. Isso evita violar `UNIQUE (event_id, starts_at)` quando o Hibernate faz INSERT antes de DELETE ao trocar a lista.
- `application`: `EventsService` (`@Transactional`; leitura `readOnly`) com `list`, `get`, `create`, `replace`, `delete`; `EventCommand` e `OccurrenceCommand` (records); `EventView(Event event, CategoryRef category, VenueRef venue)`, montado ainda dentro da transação (com `open-in-view=false` o `api` não pode navegar lazy). Para a listagem, categorias e locais são buscados em lote (`findAllByIds`) e anexados.
  - Validação de negócio, na ordem: cidade (`CityLookup.findById`: ausente ou inativa → `cityId`; em `replace` só se mudou), categoria (`categoryId`), local (`VenueLookup.findByPublicId`: ausente ou `cityId` diferente → `venueId`), preço (`isFree` com preço > 0 → `isFree`; `priceMaxCents < priceMinCents` → `priceMaxCents`), sessões (`endsAt < startsAt` → `occurrences[i].endsAt`; `startsAt` repetido → `occurrences[i].startsAt`; `PUBLISHED` sem `startsAt` futuro → `occurrences`). Os erros são acumulados e lançados juntos em uma `FieldValidationException`; os mesmos invariantes continuam no banco como rede de segurança e são traduzidos pelo nome.
  - "Futura" = `startsAt` posterior a `Instant.now()` (sem olhar o `status` da sessão). Um evento já `PUBLISHED` cujas sessões todas passaram só pode ser regravado como `PUBLISHED` com uma sessão futura; para o resto, mudar para `ARCHIVED`.
  - `replace`: carrega por `publicId`; se `command.version() != event.getVersion()` → `ConflictException`; aplica `replaceWith`; `entityManager.lock(event, OPTIMISTIC_FORCE_INCREMENT)` garante o incremento de `version` e o `UPDATE` (que dispara `set_updated_at`) mesmo quando só as sessões mudam ou nada muda; no flush, `ObjectOptimisticLockingFailureException` (corrida entre dois PUT) → `ConflictException`.
  - `list`: `city` ausente → `CityLookup.defaultCityId()`; `uf/slug` → `findIdByUfAndSlug` (404 se não existir); `category` → `CategoryLookup.findIdsWithDescendantsBySlug` (404 se não existir).
- `infrastructure`: `EventRepositoryAdapter` com `EntityManager` e JPQL montado dinamicamente (filtros opcionais) mais consulta de contagem. Ordenação: `order by (select min(o.startsAt) from Occurrence o where o.event = e and o.startsAt >= :reference) asc nulls last, (select max(o.startsAt) from Occurrence o where o.event = e) desc nulls last, e.id asc`. `from`/`to` viram `exists (select 1 from Occurrence o where o.event = e and o.startsAt >= :from and o.startsAt <= :to)`. Intervalo invertido (`from > to`) devolve lista vazia, sem erro.
- `api`: `EventsController implements EventsApi`, `EventsMapper` (`EventView` → DTO; ids internos nunca saem), reaproveita `PageMetadataMapper`.

**6. Erros e códigos**
- 400: `city` fora do padrão, `status`/`from`/`to` inválidos, UUID inválido, `size`/`page`; 404: evento, cidade ou categoria do filtro; 409: slug repetido na cidade (23505 em `event_city_slug_key`), `version` desatualizada, local com evento no `DELETE` de venue; 422: corpo inválido (`@Valid`), referências, preço, sessões (inclusive 23505 em `occurrence_event_starts_at_key` na corrida) e `event_slug_not_empty` (→ `title`).
- Venues: `VenuesService.delete` traduz 23503 em `ConflictException("O local possui eventos ligados.")`; `replace` traduz 23001 (`venue_city_in_use`) em `ConflictException`. Em escritas de local, 23503 `venue_city_id_fkey` continua sendo 422 em `cityId`.

**7. Testes**
- Unitários: `EventsService` (mocks de repositório e lookups: cada validação, acúmulo de erros, `version`, tradução de constraints), `CategoryLookup`, `VenueLookup`, `ConstraintViolations`, mapeadores, `GlobalExceptionHandlerTest` para `FieldValidationException`; `VenuesServiceTest` atualizado.
- Integração (`extends AbstractPostgresIntegrationTest`):
  - `EventsSchemaIntegrationTest` (JDBC): colunas, FKs, uniques, cada CHECK com o nome da constraint, `event_free_no_price`, slug gerado e vazio, `updated_at`, trigger de cidade do local (INSERT e UPDATE), guarda de cidade em `venue` (23001), cascade de `occurrence`, `venue_fill_slug` nomeado.
  - `EventsContextIntegrationTest` (`validate`) e adaptador (busca, ordenação, filtros).
  - `EventsApiIntegrationTest`: cada endpoint e erro da spec, `version` desatualizada, corrida de dois PUT (duas threads), `DELETE` em cascata; fixtures com slug `teste-*`.
  - `VenuesApiIntegrationTest` ganha 409 no `DELETE` e na troca de cidade com evento.
  - Regra ArchUnit nova com fixture de violação.
- Fixtures: `AbstractPostgresIntegrationTest` remove eventos de fixture (slug `teste-%`) antes de locais e cidades; helpers `insertFixtureEvent` e `insertFixtureOccurrence`.

## Risks / Trade-offs

- [Hibernate pode não expor o nome da constraint para erros de trigger] → o teste de `ConstraintViolations` roda contra o Postgres real; fallback é ler `PSQLException.getServerErrorMessage().getConstraint()` no helper.
- [Ordenação por subselects correlacionadas fica lenta com muitos eventos] → aceito nesta fase (poucos milhares de eventos); `occurrence_starts_at_idx` ajuda e a fase 2 pode materializar a próxima sessão.
- [`N+1` ao montar sessões/categorias/locais na listagem] → `@BatchSize` nas sessões e busca em lote de categorias e locais.
- [Regra "futura" depende do relógio e não é CHECK] → vive só na aplicação, com `Clock` injetável nos testes; escritores diretos (agente) não passam por ela, por escolha.
- [`PUT` em evento `PUBLISHED` já ocorrido exige sessão futura] → comportamento documentado; o cliente deve arquivar o evento.
- [Agente que faz UPDATE direto sem incrementar `version` fura o lock otimista] → regra no contrato com o agente; sem trigger nesta change.
- [Guarda de cidade em `venue` é acréscimo ao pedido] → isolado em um trigger e um requisito; removê-lo é simples se não for desejado.

## Migration Plan

1. Criar V005 e aplicá-lo localmente (`docker compose down -v && docker compose up -d`, pois o `initdb.d` só roda em volume novo) ou executar o script num banco existente.
2. Publicar o código; V005 não é editado após o merge (ajustes entram como V006+).
3. Rollback em ambiente descartável: reverter o código e `DROP TABLE occurrence, event; DROP FUNCTION event_fill_slug(), event_check_venue_city(), venue_guard_city_change();` mais `DROP TRIGGER venue_guard_city_change ON venue`. A função `venue_fill_slug()` substituída continua válida (só ganhou o nome da constraint).
