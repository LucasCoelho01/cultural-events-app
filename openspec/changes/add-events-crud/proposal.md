# Proposal

## Why

Eventos são o núcleo do produto: categorias e locais já existem, mas ainda não há como cadastrar um show, uma peça ou uma exposição com suas sessões. Sem isso a fase 2 (front-end e busca) e a fase 3 (agente de ingestão) não têm o que consumir nem onde gravar.

## What Changes

- Nova migration `database/migrations/V005__create_events.sql`: tabelas `event` e `occurrence` (colunas, FKs, `UNIQUE (city_id, slug)`, `UNIQUE (event_id, starts_at)`, `version` para lock otimista, índices `(city_id, status)` e `starts_at`) com todos os CHECKs nomeados.
- Regras no banco, válidas para qualquer escritor: slug gerado de `title` com `slugify()` quando nulo, `updated_at` por `set_updated_at()`, evento gratuito sem preço positivo, e trigger que exige `venue.city_id = event.city_id`. Na mesma migration, um guarda impede mudar a cidade de um local que já tem eventos e `venue_fill_slug()` passa a nomear sua constraint (`CREATE OR REPLACE`, sem editar a V004).
- Novos endpoints, declarados primeiro em `api/openapi.yaml` (tag `events`, `id` = `public_id` UUID): `POST /events`, `GET /events`, `GET /events/{id}`, `PUT /events/{id}` (substituição completa com sessões e `version`) e `DELETE /events/{id}`.
- `GET /events` filtra por cidade (`city=uf/slug`, padrão `app.default-city`), categoria (`category=slug`, inclui subcategorias), intervalo de sessões (`from`/`to`) e `status`; é paginado e ordenado pela próxima sessão.
- `PUT` usa lock otimista (`@Version`): `version` desatualizada responde 409.
- **Refatoração:** `VenueValidationException` vira `FieldValidationException` em `shared.error`, tratada pelo `GlobalExceptionHandler` (422 com `errors[]`) e usada por venues e events. A tradução de violações 23514/23505/23503 passa a identificar a constraint pelo nome, num helper compartilhado.
- **Modifica venues:** `DELETE /venues/{id}` responde 409 se houver evento ligado; `PUT /venues/{id}` responde 409 ao tentar mudar a cidade de um local com eventos.
- Novos casos de uso de leitura: `CategoryLookup` em `categories.application` e `VenueLookup` em `venues.application`; `events` só depende da camada `application` de `locations`, `categories` e `venues`, garantido por regra ArchUnit.
- Novo pacote `com.culturalevents.events.{api,application,domain,infrastructure}`.
- Testes de integração (Testcontainers) para triggers e constraints, cada endpoint com seus erros, 409 de `version` e o 409 no `DELETE` de local.
- Fora de escopo: `search_vector` e `locked_fields` (fases 2 e 3), máquina de estados de `status` (qualquer transição é aceita), autenticação.

## Capabilities

### New Capabilities
- `events`: cadastro de eventos com sessões (schema e regras no banco, CRUD, listagem filtrada e paginada, lock otimista).

### Modified Capabilities
- `venues`: `DELETE /venues/{id}` passa a responder 409 quando há evento ligado, e a cidade de um local com eventos não pode mudar.

## Impact

- **Capabilities afetadas:** `events` (nova) e `venues` (requisito de remoção modificado e um requisito novo). `categories` e `locations` não mudam de requisito; `categories` ganha só um caso de uso interno.
- **Banco:** V005 cria `event`, `occurrence`, funções e triggers de evento, o guarda de cidade em `venue` e substitui `venue_fill_slug()`; reutiliza `slugify()` e `set_updated_at()` da V004.
- **Contrato de schema com o agente (fase 3):** `event` e `occurrence` passam a ser destino de escrita do agente (`agent_writer`, só DML, sem revisão humana), com `origin = 'AGENT'`. O agente pode omitir `slug`, `public_id`, `status` (padrão `DRAFT`) e datas. Ele deve informar `city_id`, `category_id` (lookup por `slug`, fallback `outros`) e, se houver, `venue_id` da mesma cidade (o banco rejeita outra cidade). Deve gravar `is_free = false` ou preços zero em eventos gratuitos, e incrementar `version` (`version = version + 1`) em UPDATEs para não furar o lock otimista da API. Dedupe por `ON CONFLICT (city_id, slug)` e `ON CONFLICT (event_id, starts_at)`. Os grants de `agent_writer` entram com a fase 3, fora desta change.
- **API:** `api/openapi.yaml` ganha 2 paths (5 operações), schemas de evento e novas respostas; sem breaking changes. `DELETE /venues/{id}` ganha a resposta 409 e `PUT /venues/{id}` ganha uma causa nova de 409.
- **Código:** novo pacote `events`; `shared.error` ganha `FieldValidationException` e o helper de constraint; `venues` perde `VenueValidationException` e `VenuesExceptionHandler`; `AbstractPostgresIntegrationTest` ganha fixtures e limpeza de eventos.
- **Dependências:** nenhuma nova.
