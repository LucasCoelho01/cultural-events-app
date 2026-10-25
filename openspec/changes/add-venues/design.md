# Design

## Context

- `locations` e `categories` fixaram o padrão: openapi-generator (`spring`, `interfaceOnly`, `useTags`) gera `VenuesApi` e os DTOs em `com.culturalevents.shared.api[.model]`; o controller em `<capability>.api` implementa a interface; camadas `api -> application -> domain <- infrastructure` verificadas por ArchUnit; entidade JPA compatível com `ddl-auto=validate`; `NotFoundException`/`ConflictException` de `shared.error` viram 404/409 `Problem` no `GlobalExceptionHandler`, que também já devolve 422 com `errors[]` para `@Valid` no corpo e 400 para parâmetro de path/query inválido (incluindo UUID malformado e `size`/`page` fora do limite).
- `locations.application` já tem `DefaultCityResolver` (lê `app.default-city`, lança `DefaultCityMisconfiguredException` → 500 via `LocationsExceptionHandler`) e `LocationsService`; `CityRepository` só tem `search` e `findByUfAndSlug`.
- A regra ArchUnit usa `com.culturalevents.(*).<camada>..`, então um pacote de infraestrutura ou de API **não** pode depender de `application` de outra capability, nem `domain` de `application`. Já `application -> application` entre capabilities é permitido. Por isso a ponte com `locations` fica em `venues.application`.
- `agent_writer` (fase 3) escreve direto em `venue`: slug e `updated_at` precisam viver no banco. Motivação e escopo: ver proposal.md; requisitos: ver specs/venues/spec.md.

## Goals / Non-Goals

**Goals:**
- Regras de slug e `updated_at` no banco, válidas para qualquer escritor e reutilizáveis por `event`.
- CRUD no mesmo padrão de camadas de `locations`, sem acoplar `venues` às classes de `locations`.

**Non-Goals:**
- Vínculo com eventos e o 409 no `DELETE` (entram em `add-events-crud`, que trocará o `DELETE` e a FK).
- Soft delete, concorrência otimista (`PUT` é substituição idempotente), busca textual, índices de listagem além dos de PK/unique (a listagem filtra por `city_id`, coberto pelo prefixo de `UNIQUE (city_id, slug)`).
- Grants de `agent_writer` sobre `venue` (fase 3).
- Gerar slug único com sufixo numérico: colisão de slug gerado é 409, o cliente informa outro `slug`.

## Decisions

**1. Arquivos e mudanças de contrato**
- SQL novo: `database/migrations/V004__create_venues.sql` (V001..V003 não são editados).
- OpenAPI (`api/openapi.yaml`), tag `venues`, atualizado antes do código:
  - `/venues`: `GET listVenues` (query `city` com `pattern: ^[A-Za-z]{2}/[^/\s]+$`, `neighborhood` `maxLength: 120`, `page`, `size` iguais a `/cities`; respostas 200 `VenuePage`, 400 `BadRequest`, 404 `Problem`) e `POST createVenue` (201 `Venue` + header `Location`, 400, 409, 422).
  - `/venues/{id}` (`id` `type: string, format: uuid`): `GET getVenue` (200, 400, 404), `PUT replaceVenue` (200, 400, 404, 409, 422), `DELETE deleteVenue` (204, 400, 404).
  - Schemas: `VenueInput` (`cityId` int64 e `name` obrigatórios, `maxLength: 200`, `minLength: 1`; `slug` `maxLength: 200`, `pattern: ^[a-z0-9]+(-[a-z0-9]+)*$`, anulável; `addressLine` ≤255; `neighborhood` ≤120; `postalCode` `pattern: ^\d{5}-\d{3}$`; `latitude` -90..90; `longitude` -180..180; `websiteUrl` `pattern: ^https?://\S+$`, ≤2048), `Venue` (todos os campos de `VenueInput` + `id` uuid, `slug` obrigatório, `createdAt`/`updatedAt` `date-time`) e `VenuePage` (`items`, `page`).
  - Respostas 409 e 422 novas em `components.responses` (`Conflict`, `UnprocessableEntity`), ambas `application/problem+json` → `Problem`.
- Sem mudança em `pom.xml`; o gerador já cobre o arquivo único.
- O formato `00000-000` e o padrão do slug são validados na API (422) e, para CEP, também por CHECK. O formato do slug não é CHECK no banco, para não restringir o agente nesta change.

**2. Migration V004 e regras no banco**
- Tabela `venue` exatamente como pedido; `public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid()` (nativo no Postgres 16, sem extension).
- CHECKs nomeados: `venue_latitude_range`, `venue_longitude_range`, `venue_postal_code_format` (`postal_code ~ '^\d{5}-\d{3}$'`; nulo passa).
- `slugify(text) RETURNS text LANGUAGE sql STABLE` (`unaccent` não é `IMMUTABLE`, então a função também não é): `trim(both '-' from regexp_replace(lower(unaccent($1)), '[^a-z0-9]+', '-', 'g'))`.
- `set_updated_at() RETURNS trigger` genérica: `NEW.updated_at := clock_timestamp(); RETURN NEW;`. Usa `clock_timestamp()` e não `now()` para que o valor mude mesmo quando INSERT e UPDATE ocorrem na mesma transação, o que torna o teste determinístico. Trigger `venue_set_updated_at BEFORE UPDATE ON venue FOR EACH ROW`.
- Slug: função `venue_fill_slug() RETURNS trigger` e trigger `BEFORE INSERT OR UPDATE ON venue`: se `NEW.slug IS NULL OR btrim(NEW.slug) = ''`, faz `NEW.slug := left(slugify(NEW.name), 200)`; se o resultado for vazio, `RAISE EXCEPTION ... USING ERRCODE = 'check_violation'`. O trigger roda antes da checagem de NOT NULL e da unicidade.
- Alternativa descartada: função de trigger de slug genérica por `TG_ARGV`/`jsonb_populate_record` — o campo de origem muda por tabela (`name` vs `title`) e a versão genérica seria mais opaca. `event` cria o próprio trigger chamando `slugify`; só `slugify` e `set_updated_at` são genéricas.
- `created_at` não é protegido contra UPDATE por trigger; a aplicação o mapeia como `updatable = false`.

**3. Ponte com `locations` sem acoplamento de classes**
- `locations.application` ganha um bean `CityLookup` (use case) com: `Optional<CityRef> findById(long id)`, `Optional<Long> findIdByUfAndSlug(String uf, String slug)` e `long defaultCityId()` (delegando ao `DefaultCityResolver`); `CityRef` é um record `(long id, boolean active)` na própria `application`. `CityRepository` ganha `findById(Long)`; nenhuma API nem requisito de `locations` muda.
- `venues.application` injeta `CityLookup` diretamente (permitido por ArchUnit) e só enxerga `CityRef` e `Long`. Nada em `venues` importa `locations.domain`, `.api` ou `.infrastructure`; uma regra ArchUnit nova garante isso. A FK `city_id` continua no banco, então uma corrida de remoção de cidade falha com `23503` (aceito: cidades só são alteradas por script SQL).
- Alternativa descartada: porta `CityGateway` em `venues.domain` com adaptador em `venues.infrastructure` — ArchUnit proíbe `infrastructure -> locations.application`, e a porta só reembrulharia o mesmo caso de uso.

**4. Camadas do pacote `venues`**
- `domain`: entidade JPA mutável `Venue` (`@GeneratedValue(IDENTITY)` em `id`; `publicId` UUID com `@Generated(event = INSERT)`, `insertable = false`; `cityId` `Long`; `slug` com `@Generated(event = {INSERT, UPDATE})`; `createdAt`/`updatedAt` `OffsetDateTime`, `insertable = false, updatable = false`, `@Generated`), `VenueRepository` (`search(cityId, neighborhood, pageable)`, `findByPublicId`, `saveAndFlush`, `delete`). O conflito de slug é traduzido na `application` (ver abaixo), não no domínio.
  - Como o banco gera `public_id`, `slug` nulo, `created_at` e `updated_at`, o Hibernate relê esses valores após INSERT/UPDATE com `@Generated`; `slug` não usa `nullable = false` na entidade para o Hibernate aceitar o nulo antes do trigger.
- `application`: `VenuesService` (`@Transactional`, leitura `readOnly`) com `list(city, neighborhood, page, size)`, `get(id)`, `create(cmd)`, `replace(id, cmd)`, `delete(id)`; `VenueCommand` (record de entrada); `VenueValidationException(field, message)`.
  - `list`: `city` ausente → `defaultCityId()`; presente → parse `uf/slug` (formato inválido é 400 já pelo `pattern` do contrato) e `findIdByUfAndSlug`, `NotFoundException` se não existir. Cidade inativa **não** é rejeitada na listagem. `neighborhood` filtra por `lower(neighborhood) = lower(?)`. Ordenação `name, id` (o `id` desempata páginas).
  - `create`/`replace`: valida a cidade (`findById`; ausente ou inativa → `VenueValidationException("cityId", ...)`; em `replace` só se `cityId` mudou), grava com `saveAndFlush` e traduz `DataIntegrityViolationException` pelo SQLSTATE: `23505` na constraint `(city_id, slug)` → `ConflictException`; `23514` do trigger de slug → `VenueValidationException("name", "não gera um slug válido")`; `23503` → `VenueValidationException("cityId", ...)`.
  - `replace` carrega a entidade, sobrescreve todos os campos (`slug` em branco vira `null` para regenerar) e `saveAndFlush`.
- `infrastructure`: `VenueRepositoryAdapter` sobre `SpringDataVenueRepository` (`@Query` para a busca com `neighborhood` opcional).
- `api`: `VenuesController implements VenuesApi` (cria a URI de `Location` a partir do `id` UUID), `VenuesMapper` (entidade ↔ DTO; `BigDecimal` ↔ `Double`/`BigDecimal` conforme o gerador) e `VenuesExceptionHandler` (`@RestControllerAdvice` limitado ao pacote, como `LocationsExceptionHandler`) que converte `VenueValidationException` em 422 `Problem` com `errors[]`.

**5. Erros e códigos**
- 422 do corpo vem do `@Valid` gerado a partir do contrato (`errors[]` por campo, já tratado pelo `GlobalExceptionHandler`); 422 de cidade e de slug vazio vêm de `VenueValidationException`; 409 de `ConflictException`; 404 de `NotFoundException`; 400 de UUID/`city`/paginação inválidos pelo handler global.
- `PUT` mantendo o próprio slug não conflita: a unicidade do banco ignora a própria linha.

**6. Testes**
- Unitários: `VenuesService` com repositório e `CityLookup` mockados (cidade ausente/inativa, troca de cidade, tradução de `23505`/`23514`, cidade padrão, listagem); `VenuesMapper`.
- Integração (`extends AbstractPostgresIntegrationTest`):
  - `VenuesSchemaIntegrationTest` (JDBC): colunas e constraints, FK `23503`, unicidade `23505`, CHECKs `23514` (limites aceitos), `slugify` (acentos, pontuação, hífens nas pontas), trigger de slug (gerado, preservado, `'!!!'` rejeitado), `updated_at` maior após UPDATE com `created_at` igual, `set_updated_at` reutilizável por uma tabela temporária.
  - `VenuesContextIntegrationTest` (`ddl-auto=validate`).
  - `VenuesApiIntegrationTest` (MockMvc): cada endpoint com sucesso e todos os erros da spec (400, 404, 409, 422, 500 de cidade padrão mal configurada via propriedade de teste) e conferência de que o `BIGSERIAL` não aparece.
  - Regra ArchUnit nova em `ArchitectureTest`: classes de `venues` não dependem de `locations.{domain,api,infrastructure}`.
- Fixtures: `venue` de teste recebe `name` com prefixo `Teste ` e slug `teste-*`; a classe base ganha helper `insertFixtureVenue(...)` e um `@AfterEach` que remove `venue` com slug `teste-%` **antes** de remover cidades de fixture (FK).

## Risks / Trade-offs

- [Reler colunas geradas via `@Generated` com `IDENTITY` pode falhar ou custar uma consulta extra] → `saveAndFlush` e testes de criação/substituição afirmam `slug`, `publicId` e `updatedAt` do retorno; fallback é um `refresh` explícito no adaptador.
- [Slug gerado colide e o cliente recebe 409 sem sugestão] → aceito; o cliente reenvia com `slug` explícito (cenário coberto em spec).
- [Remoção de cidade concorrente com criação de local] → FK no banco rejeita; cidades só mudam por script SQL, então a janela é desprezível.
- [`DELETE` físico será barrado por FK quando `event` existir] → `add-events-crud` introduz o 409 explicitamente; nesta change não há dependentes.
- [Trigger PL/pgSQL não aparece no `validate` do Hibernate] → coberto por `VenuesSchemaIntegrationTest` sobre as migrations reais.
- [`unaccent` depende da extension de V001 e do dicionário do Postgres] → V004 é aplicado após V001; teste cobre acentos em português.

## Migration Plan

1. Criar V004 e aplicá-lo localmente (`docker compose down -v && docker compose up -d`, pois o `initdb.d` só roda em volume novo) ou executar o script num banco existente.
2. Publicar o código; V004 não é editado após o merge (ajustes entram como V005+).
3. Rollback: reverter o código e, em ambientes descartáveis, `DROP TABLE venue; DROP FUNCTION venue_fill_slug(), set_updated_at(), slugify(text);`; não há dependentes ainda. Depois que `event` reutilizar `slugify`/`set_updated_at`, essas funções não podem mais ser removidas junto.
