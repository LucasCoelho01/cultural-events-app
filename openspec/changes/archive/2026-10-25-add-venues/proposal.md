# Proposal

## Why

Eventos acontecem em locais, e o agente de ingestão (fase 3) e a futura API de eventos precisam de um cadastro de locais por cidade, com identificador público estável e regras de slug e `updated_at` garantidas pelo banco, não só pela aplicação. Definir isso antes de `add-events-crud` evita retrabalho de schema e entrega as funções SQL reutilizáveis por `event`.

## What Changes

- Nova migration `database/migrations/V004__create_venues.sql`: tabela `venue` (`id` BIGSERIAL, `public_id` UUID único, `city_id` FK para `city`, `name`, `slug`, endereço, bairro, CEP, coordenadas, `website_url`, `created_at`/`updated_at`, `UNIQUE (city_id, slug)`) com CHECKs de latitude (-90..90), longitude (-180..180) e CEP (`00000-000`).
- Regras no banco, reutilizáveis por `event`: função `slugify(text)` (com `unaccent`), trigger que preenche `slug` a partir de `name` quando vier nulo e função/trigger genérico `set_updated_at()`.
- Novos endpoints, declarados primeiro em `api/openapi.yaml` (tag `venues`): `POST /venues`, `GET /venues`, `GET /venues/{id}`, `PUT /venues/{id}` e `DELETE /venues/{id}`; a API expõe `public_id` como `id` (UUID) e nunca o `BIGSERIAL`.
- `GET /venues` filtra por cidade (`city=uf/slug`, padrão `app.default-city`) e por `neighborhood`, paginado e ordenado por `name`.
- Escrita valida a cidade (`cityId`) por um caso de uso novo de `locations`: 422 se não existir ou estiver inativa; 409 se o slug já existir na cidade.
- Novo pacote `com.culturalevents.venues.{api,application,domain,infrastructure}`; `venues` guarda `cityId` como `Long` e não depende das classes de `locations`, só de um caso de uso na camada `application`.
- Testes de integração (Testcontainers) cobrindo triggers e cada endpoint com seus erros.
- Fora de escopo: vínculo com eventos (em `add-events-crud`, o `DELETE` passará a responder 409 se houver evento ligado), autenticação e busca textual.

## Capabilities

### New Capabilities
- `venues`: cadastro de locais por cidade (schema, regras de slug e `updated_at` no banco, CRUD e listagem paginada por API).

### Modified Capabilities
<!-- Nenhuma: o caso de uso novo em locations é interno e não altera requisitos de locations. -->

## Impact

- **Capabilities afetadas:** `venues` (nova). `locations` ganha um caso de uso interno de consulta de cidade por id e por `uf/slug`, sem mudança de requisito nem de API.
- **Banco:** nova migration V004 (tabela, CHECKs, funções `slugify` e `set_updated_at`, triggers); nenhuma alteração em tabelas existentes. Usa a extension `unaccent` já habilitada em V001.
- **Contrato de schema com o agente (fase 3):** `venue` passa a ser destino de escrita do agente (`agent_writer`, só DML, sem revisão humana). O agente pode omitir `slug` (o trigger o gera) e `public_id`/`created_at`/`updated_at` (defaults), e informa `city_id` resolvido por lookup em `city`. `UNIQUE (city_id, slug)` permite deduplicar com `ON CONFLICT`. Os grants de `agent_writer` sobre `venue` entram com a fase 3, fora desta change.
- **API:** `api/openapi.yaml` ganha 2 paths (5 operações) e os schemas `Venue`, `VenueInput` e `VenuePage`; sem breaking changes.
- **Código:** novo pacote `venues` no backend; `locations.application` ganha o caso de uso de consulta de cidade; `AbstractPostgresIntegrationTest` ganha limpeza de fixtures de `venue`.
- **Dependências:** nenhuma nova.
