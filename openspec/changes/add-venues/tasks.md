# Tasks

## 1. Schema, funções e triggers

- [ ] 1.1 Criar `database/migrations/V004__create_venues.sql` com a tabela `venue` (colunas, FK, `UNIQUE (city_id, slug)`, `public_id` único com `gen_random_uuid()`), os CHECKs nomeados de latitude, longitude e CEP, `slugify(text)` (STABLE, com `unaccent`), `set_updated_at()` (`clock_timestamp()`), `venue_fill_slug()` (erro `check_violation` se o slug gerado for vazio) e os triggers `BEFORE INSERT OR UPDATE` (slug) e `BEFORE UPDATE` (`updated_at`); verificar aplicando V001..V004 num Postgres 16 limpo e inspecionando a tabela com `\d venue` (`MigrationNamingConventionTest` continua verde)
- [ ] 1.2 Estender `AbstractPostgresIntegrationTest` com o helper `insertFixtureVenue(...)` e um `@AfterEach` que remove `venue` de fixture (slug `teste-%`) antes das cidades de fixture, sem tocar a carga inicial; verificar com dois testes que inserem a mesma fixture e passam em qualquer ordem
- [ ] 1.3 Criar `VenuesSchemaIntegrationTest` (estende a classe base, JDBC) cobrindo: colunas e constraints, FK inexistente (`23503`), slug duplicado na cidade (`23505`) e aceito em outra cidade, CHECKs rejeitando 90.5/-180.5/`01310100` (`23514`) e aceitando -90, 90, -180, 180, `01310-100` e nulos, `slugify` (acentos, pontuação, hífens nas pontas), slug gerado com `name` acentuado, slug informado preservado, `name` `'!!!'` rejeitado (`23514`), `updated_at` maior após UPDATE com `created_at` igual, e `set_updated_at()` reutilizada por uma tabela temporária; verificar `./mvnw test -Dtest=VenuesSchemaIntegrationTest` passando

## 2. Contrato OpenAPI (antes do código)

- [ ] 2.1 Atualizar `api/openapi.yaml` com a tag `venues`, os paths `/venues` (`GET listVenues`, `POST createVenue`) e `/venues/{id}` (`GET getVenue`, `PUT replaceVenue`, `DELETE deleteVenue`), os schemas `VenueInput`, `Venue` e `VenuePage` com as validações da Decisão 1 e as respostas compartilhadas `Conflict` (409) e `UnprocessableEntity` (422); verificar com `./mvnw generate-sources` gerando `VenuesApi`, `Venue`, `VenueInput` e `VenuePage` em `target/generated-sources`
- [ ] 2.2 Conferir nas classes geradas que `VenueInput` tem `@Valid`/`@NotNull`/`@Size`/`@Pattern`/`@DecimalMin`/`@DecimalMax` esperados, que `id` é `UUID` e que `createdAt`/`updatedAt` são `OffsetDateTime`; verificar inspecionando `VenuesApi.java`, `VenueInput.java` e `Venue.java`

## 3. Caso de uso de locations

- [ ] 3.1 Adicionar `findById` a `CityRepository` (e adaptador) e criar `CityLookup` e o record `CityRef(long id, boolean active)` em `locations.application` com `findById`, `findIdByUfAndSlug` (UF sem diferenciar maiúsculas) e `defaultCityId()` (via `DefaultCityResolver`, mantendo os erros de configuração); verificar com teste unitário (Mockito) cobrindo cidade ativa, inativa, inexistente, UF em minúsculas e cidade padrão mal configurada, e `NoHardcodedCityTest` continua verde

## 4. Domínio, infraestrutura e aplicação de venues

- [ ] 4.1 Criar o pacote `com.culturalevents.venues.{api,application,domain,infrastructure}` com a entidade JPA `Venue` (colunas geradas com `@Generated`, `createdAt` não atualizável, `cityId` como `Long`) e a porta `VenueRepository` em `domain`; implementar `VenueRepositoryAdapter` sobre `SpringDataVenueRepository` (busca por cidade e bairro sem diferenciar maiúsculas, ordenação `name, id`); verificar com `VenuesContextIntegrationTest` (contexto sobe com `ddl-auto=validate`) e um teste de adaptador que cria um local sem slug e lê de volta `slug`, `publicId` e `updatedAt` gerados pelo banco
- [ ] 4.2 Implementar `VenuesService`, `VenueCommand` e `VenueValidationException` com listagem (cidade padrão, `uf/slug`, 404 de cidade), `get`, `create`, `replace` (cidade só revalidada se mudou, `slug` em branco regenerado) e `delete`, traduzindo `23505` em `ConflictException`, `23514` e `23503` em `VenueValidationException`; verificar com testes unitários (Mockito) cobrindo cada ramo e erro da spec
- [ ] 4.3 Adicionar a regra ArchUnit em `ArchitectureTest` proibindo `com.culturalevents.venues..` de depender de `com.culturalevents.locations.{domain,api,infrastructure}..` e um caso em `ArchitectureRuleViolationTest` (fixture de violação) provando que a regra falha; verificar `./mvnw test -Dtest='Architecture*'` passando

## 5. API

- [ ] 5.1 Implementar `VenuesController implements VenuesApi` (201 com `Location` `/api/v1/venues/{id}`, 204 no `DELETE`), `VenuesMapper` e `VenuesExceptionHandler` (422 `Problem` com `errors[]` para `VenueValidationException`); verificar com testes unitários do mapper (todos os campos, opcionais nulos, `id` é o UUID) e do handler
- [ ] 5.2 Criar `VenuesApiIntegrationTest` (estende a classe base, MockMvc) para `POST`, `GET /venues/{id}` e `DELETE`: 201 com slug gerado e informado, `Location` sem o `BIGSERIAL`, 422 por campo (nome em branco, slug, CEP, latitude, URL, `cityId` ausente/inexistente/inativo, nome sem alfanuméricos), 409 de slug duplicado (informado e gerado) e 201 do mesmo slug em outra cidade, 400 de JSON ilegível e de UUID inválido, 404 em `GET`/`DELETE` inexistente e repetido, 204 e `GET` posterior 404; verificar execução verde
- [ ] 5.3 No mesmo teste ou em `VenuesListAndReplaceApiIntegrationTest`, cobrir `GET /venues` (padrão = cidade padrão, `city=uf/slug`, `neighborhood` sem diferenciar maiúsculas, ordenação por `name`, paginação, `city` malformado 400, cidade inexistente 404, `size`/`page` inválidos 400, `app.default-city` inexistente/inativa 500 via propriedade de teste) e `PUT` (200 com `id`/`createdAt` mantidos e `updatedAt` maior, opcionais limpos, próprio slug 200, 404, 409, 422, troca para cidade inativa 422, cidade inativa mantida 200); verificar execução verde

## 6. Documentação e verificação final

- [ ] 6.1 Atualizar o `README.md` com a seção de locais (endpoints, `id` UUID, regras de slug e `updated_at` no banco, funções `slugify`/`set_updated_at` reutilizáveis, que o agente pode omitir `slug`, recriar o volume do Docker para aplicar V004); verificar que os comandos documentados rodam como escritos
- [ ] 6.2 Rodar `./mvnw clean verify` em `backend/` e `docker compose down -v && docker compose up -d`, criando um local via `POST /api/v1/venues` e conferindo `SELECT slug, updated_at FROM venue`; verificar build verde e o slug gerado pelo banco

## Workflow follow-up

- Validar a change (`openspec validate add-venues --strict`) e arquivá-la após o merge.
