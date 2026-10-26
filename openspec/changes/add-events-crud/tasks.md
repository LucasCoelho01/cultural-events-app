# Tasks

## 1. Contrato OpenAPI (antes do código)

- [ ] 1.1 Atualizar `api/openapi.yaml` com a tag `events`, os paths `/events` (`GET listEvents`, `POST createEvent`) e `/events/{id}` (`GET getEvent`, `PUT replaceEvent`, `DELETE deleteEvent`), os schemas `EventInput`, `EventUpdateInput`, `OccurrenceInput`, `Event`, `CategoryRef`, `VenueRef`, `Occurrence` e `EventPage` com as validações da Decisão 1, a resposta `EventNotFound` e o 409 `Conflict` em `DELETE /venues/{id}`; verificar com `./mvnw generate-sources` gerando `EventsApi` e os DTOs em `target/generated-sources` e inspecionando que `id` é `UUID`, `version` é `Integer` obrigatório em `EventUpdateInput` e `occurrences` é `List<OccurrenceInput>`

## 2. Refatoração de erros compartilhados

- [ ] 2.1 Criar `FieldValidationException` e `FieldViolation` em `shared.error`, tratar a exceção no `GlobalExceptionHandler` (422 com `errors[]`) e criar `shared.persistence.ConstraintViolations` (`sqlState`, `constraintName`); verificar com testes unitários do helper e do handler (um e vários campos, sem stack trace) e com um teste de integração que provoca uma violação de CHECK nomeada e confirma que o nome da constraint chega ao helper
- [ ] 2.2 Migrar venues para a nova exceção: `VenuesService` passa a usar `FieldValidationException` e traduz violações por nome de constraint (`venue_latitude_range`, `venue_longitude_range`, `venue_postal_code_format`, `venue_slug_not_empty`, `venue_city_id_fkey`), removendo `VenueValidationException`, `VenuesExceptionHandler` e seu teste; verificar com `VenuesServiceTest` e `VenuesApiIntegrationTest` existentes passando (a parte de `venue_slug_not_empty` depende da 3.1)

## 3. Schema, triggers e fixtures

- [ ] 3.1 Criar `database/migrations/V005__create_events.sql` conforme a Decisão 2 (tabelas, índices, constraints nomeadas, triggers `event_fill_slug`, `event_set_updated_at`, `event_check_venue_city`, guarda `venue_guard_city_change` e `CREATE OR REPLACE` de `venue_fill_slug()` com constraint nomeada); verificar aplicando V001..V005 num Postgres 16 limpo e inspecionando com `\d event`, `\d occurrence` (`MigrationNamingConventionTest` continua verde)
- [ ] 3.2 Estender `AbstractPostgresIntegrationTest` com `insertFixtureEvent`, `insertFixtureOccurrence` e a remoção de eventos de fixture (slug `teste-%`) antes de locais e cidades, e criar `EventsSchemaIntegrationTest` (JDBC) cobrindo FKs (`23503`), uniques (`23505`), cada CHECK pelo nome da constraint, evento gratuito com preço positivo, slug gerado e título sem alfanuméricos, `updated_at`, trigger de cidade do local em INSERT e UPDATE, guarda de cidade em `venue` (`23001`), cascade de `occurrence` e fixtures sem vazamento entre testes; verificar `./mvnw test -Dtest=EventsSchemaIntegrationTest` passando

## 4. Casos de uso entre módulos e regras de venues

- [ ] 4.1 Criar `CategoryLookup` (`findById`, `findAllByIds`, `findIdsWithDescendantsBySlug`) em `categories.application` e `VenueLookup` (`findByPublicId`, `findAllByIds`) em `venues.application`, com os métodos novos nas portas de repositório e adaptadores; verificar com testes unitários (Mockito) cobrindo raiz com filhos, subcategoria, slug inexistente, id inexistente e local de outra cidade
- [ ] 4.2 Implementar em `VenuesService` o 409 no `delete` (23503) e na troca de cidade com eventos (23001 `venue_city_in_use`); verificar com testes unitários e com novos casos em `VenuesApiIntegrationTest` (evento ligado → `DELETE` 409 e local permanece; sem eventos → 204; `PUT` trocando a cidade com evento → 409; sem eventos → 200; remover o evento libera o `DELETE`)

## 5. Domínio, infraestrutura e aplicação de events

- [ ] 5.1 Criar o pacote `com.culturalevents.events.{api,application,domain,infrastructure}` com as entidades `Event` (com `@Version` e sincronização de sessões por `startsAt`) e `Occurrence`, os enums, `EventFilter` e a porta `EventRepository` em `domain`, e o `EventRepositoryAdapter` com JPQL dinâmico (cidade, categorias, `from`/`to`, `status`, ordenação pela próxima sessão); verificar com `EventsContextIntegrationTest` (contexto sobe com `ddl-auto=validate`) e testes do adaptador cobrindo cada filtro, a ordenação (próxima sessão, sem sessão futura, desempate por `id`), intervalo invertido vazio e a paginação
- [ ] 5.2 Implementar `EventsService` (`list`, `get`, `create`, `replace`, `delete`), `EventCommand`, `OccurrenceCommand` e `EventView` com as validações acumuladas da Decisão 5, o controle de `version` com `OPTIMISTIC_FORCE_INCREMENT`, a tradução de constraints por nome e `Clock` injetável; verificar com testes unitários (Mockito) cobrindo cada erro da spec (cidade, categoria, local, preço, sessões, publicação sem sessão futura, `version` desatualizada, slug duplicado, sessão duplicada na corrida) e o caminho feliz de cada operação
- [ ] 5.3 Adicionar a regra ArchUnit `..events..` sem dependência de `domain`/`api`/`infrastructure` de `locations`, `categories` e `venues` em `ArchitectureTest`, com fixture de violação em `ArchitectureRuleViolationTest`; verificar `./mvnw test -Dtest='Architecture*'` passando

## 6. API

- [ ] 6.1 Implementar `EventsController implements EventsApi` (201 com `Location` `/api/v1/events/{id}`, 204 no `DELETE`) e `EventsMapper`; verificar com testes unitários do mapper (evento com e sem local, sessões ordenadas, `id` é o UUID, ids internos ausentes)
- [ ] 6.2 Criar `EventsApiIntegrationTest` (estende a classe base, MockMvc) para `POST`, `GET /events/{id}` e `DELETE`: criação mínima e completa, 422 por campo, referências inválidas acumuladas, preço, sessões, publicação sem sessão futura, 409 de slug, 400 de JSON e UUID, 404, `DELETE` em cascata; verificar execução verde
- [ ] 6.3 Cobrir `GET /events` (cidade padrão, `city`, `category` com subcategorias, `from`/`to`, `status`, ordenação, paginação, 400, 404, 500 de cidade padrão mal configurada) e `PUT /events/{id}` (substituição, `PUT` idêntico incrementa `version`, `version` desatualizada → 409, corrida de dois `PUT` com a mesma `version`, sessão mantida por `startsAt`, 404, 409 de slug, 422); verificar execução verde

## 7. Documentação e verificação final

- [ ] 7.1 Atualizar o `README.md` com a seção de eventos (endpoints, `version` e lock otimista, regras no banco, contrato com o agente incluindo `version = version + 1` e `ON CONFLICT`, 409 no `DELETE` de local, recriar o volume do Docker para aplicar V005) e o `api/openapi.yaml` conferido contra a API; verificar que os comandos documentados rodam como escritos
- [ ] 7.2 Rodar `./mvnw verify` em `backend/` e `docker compose down -v && docker compose up -d` (ou aplicar o V005 manualmente), criando um local e um evento via API e conferindo `SELECT slug, version, updated_at FROM event`; verificar build verde e o `DELETE` do local respondendo 409

## Workflow follow-up

- Validar a change (`openspec validate add-events-crud --strict`) e arquivá-la após o merge.
