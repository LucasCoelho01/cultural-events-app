## Why

A fundação do front-end (`setup-frontend-foundation`) já resolve a cidade e as rotas `/:uf/:cidade`, mas a página da cidade ainda é um título. O produto precisa das primeiras telas de negócio: o visitante vê os eventos publicados da cidade e abre o detalhe de um deles. O detalhe público precisa de um endpoint que não exponha eventos em rascunho, cancelados ou arquivados, o que o `GET /events/{id}` atual (administrativo, por UUID, sem filtro de status) não garante.

## What Changes

- **Front-end:**
  - Lista em `/:uf/:cidade`: eventos `PUBLISHED` com sessões futuras, paginada (anterior/próxima via `PageMetadata`), com estados de carregando, vazio e erro (`ErrorMessage`).
  - Card com título, categoria, local, próxima sessão e preço, ligado ao detalhe.
  - Detalhe em `/:uf/:cidade/eventos/:slug`: descrição, sessões futuras, local com endereço, classificação etária e link de ingresso.
  - Datas formatadas com `Intl.DateTimeFormat('pt-BR')` no fuso da cidade (de `useCity`); preço como "Grátis", "R$ x" ou "R$ x – R$ y". Formatação em funções puras com testes unitários.
- **Contrato:** novo endpoint público `GET /cities/{uf}/{slug}/events/{eventSlug}` em `api/openapi.yaml`, que devolve o `Event` somente se estiver `PUBLISHED` naquela cidade e 404 (`EventNotFound`) caso contrário.
- **Back-end:** implementação desse endpoint no módulo `events`, com teste de integração (Testcontainers).
- Nenhum script SQL novo; nenhuma mudança em schemas existentes do OpenAPI. Sem **BREAKING**.

## Capabilities

### New Capabilities

- `public-catalog`: páginas públicas de eventos por cidade (lista, card, detalhe) e o endpoint público de detalhe de evento publicado.

### Modified Capabilities

Nenhuma. A capability `events` não muda (o endpoint novo só lê eventos já existentes). O endpoint é pedido como parte de `public-catalog`.

## Impact

- Código: `frontend/src` (rotas, `src/api/events.ts`, componentes de lista/card/detalhe, funções puras de formatação); `backend` módulo `com.culturalevents.events` (`EventsController`, `EventsService`, `EventRepository`/adapter com busca por cidade e slug); `api/openapi.yaml`; coleção Postman em `postman/` (nova requisição).
- Contrato de schema com o agente de ingestão: nenhum impacto (sem scripts SQL; leitura de dados existentes).
- Dependências novas: nenhuma.
