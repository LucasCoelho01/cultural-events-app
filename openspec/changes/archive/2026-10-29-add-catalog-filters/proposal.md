# Proposal

## Why

A lista pública `/:uf/:cidade` mostra todos os eventos futuros da cidade, sem como restringir por categoria ou data. A API `GET /events` já aceita `category`, `from` e `to`; falta o front-end usá-los para que o público encontre o que procura (ex.: "shows neste fim de semana").

## What Changes

- Filtro de categoria na lista: select com as categorias de `GET /categories` (subcategorias indentadas) e a opção "Todas". Escolher a categoria pai inclui as filhas (comportamento da API).
- Filtro de período: atalhos "Hoje", "Este fim de semana" (sábado 00:00 a domingo 23:59) e "Próximos 7 dias", mais intervalo personalizado de datas. Intervalos calculados no fuso da cidade (`City.timezone`) e enviados como ISO 8601 com offset em `from`/`to`.
- Filtros na query string: `?categoria=<slug>`, `?periodo=hoje|fim-de-semana|7-dias` ou `?de=AAAA-MM-DD&ate=AAAA-MM-DD`, junto com `?page=`. Mudar um filtro volta à página 1; valores inválidos são ignorados sem erro; botão "Limpar filtros".
- Estado vazio com filtro ativo: "Nenhum evento para esses filtros", com ação de limpar.
- Funções puras para os intervalos (`now` e `timeZone` injetados), com testes unitários.
- Refactor: `localToIso`, `isValidLocal` e `isoToLocal` saem de `src/admin/datetime.ts` para `src/format/datetime.ts` (usados pelo admin e pelos filtros).
- Sem mudança de contrato, back-end, OpenAPI, SQL ou schema do agente de ingestão.

## Capabilities

### New Capabilities
<!-- nenhuma -->

### Modified Capabilities
- `public-catalog`: o requisito "Lista pública de eventos da cidade" passa a aceitar `category`/`from`/`to`; novos requisitos para filtro de categoria, filtro de período e filtros na query string; o estado vazio ganha variante com filtro ativo.

## Impact

- Código (`frontend/src`): `api/events.ts` (`useEvents` recebe filtros), `components/EventList.tsx` e `CityPage.tsx`, novos componentes de filtros, novo módulo de intervalos, `format/datetime.ts` (movido), `api/categories.ts` (reuso de `useCategories`/`flattenCategories`).
- Testes: unitários dos intervalos e do parsing da query string; testes de página com MSW (`zz/cidade-teste`).
- API/OpenAPI/migrações: nenhum impacto. Contrato de schema com o agente: nenhum impacto.
