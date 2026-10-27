## Context

- O front-end tem rotas `/:uf/:cidade` (hoje só um título), `useCity`, `ErrorMessage` e cliente `openapi-fetch` tipado, com retry apenas para rede e 5xx (`src/api/queryClient.ts`).
- `GET /events` já filtra por `city`, `status` e `from` (sessões com `startsAt >= from`), ordena pela próxima sessão e devolve `EventPage` com `PageMetadata`. Cada `Event` traz `category`, `venue` (`VenueRef`: apenas `id`, `name`, `slug`) e `occurrences`.
- `GET /events/{id}` é por UUID e não filtra status; não serve como detalhe público (revelaria rascunhos).
- `GET /venues/{id}` já é público e devolve `addressLine` e `neighborhood`.
- Backend: `EventsController` implementa a interface gerada `EventsApi`; `EventsService.list` já resolve cidade por `uf/slug` via `CityLookup`; `EventRepository.findByPublicId` existe, mas não há busca por cidade e slug.

## Goals / Non-Goals

**Goals:** lista paginada, card, detalhe, endpoint público de detalhe, formatação pura testada.
**Non-Goals:** filtros (categoria, data), busca textual, imagens (`imageUrl`), SEO/SSR, mudança no schema `VenueRef`, autenticação.

## Decisions

**Contrato (`api/openapi.yaml`)**
- Novo path `/cities/{uf}/{slug}/events/{eventSlug}` com `get`, `operationId: getPublishedEvent`, tag `events` (cai em `EventsApi`/`EventsController`). Parâmetros `uf` (`^[A-Za-z]{2}$`, como `getCity`), `slug` e `eventSlug` (`maxLength: 200`). Respostas: 200 `Event`, 400 `BadRequest`, 404 `EventNotFound`. O contrato é atualizado antes do código.
- Nenhum schema novo ou alterado. Nenhum script SQL novo.

**Backend**
- `EventRepository.findByCityIdAndSlug(long cityId, String slug)` (JPQL, mesma forma de `findByPublicId`); a unicidade `(city_id, slug)` já existe no schema.
- `EventsService.getPublished(uf, slug, eventSlug)`: resolve a cidade (404 "Cidade não encontrada" se não existir), busca o evento e lança `NotFoundException` se ausente **ou** `status != PUBLISHED`, com a mesma mensagem, para não vazar a existência. Reaproveita `view(...)` (sessões, categoria e local).
- `EventsController.getPublishedEvent` mapeia com `EventsMapper.toDto`. O 404 e o 400 vêm do `GlobalExceptionHandler` existente.
- Cidade inativa: o endpoint não a rejeita (consistente com `getCity`, que retorna cidades ativas ou não).

**Front-end**
- Rotas: `/:uf/:cidade` → `CityPage` (lista) e `/:uf/:cidade/eventos/:slug` → `EventPage`.
- `src/api/events.ts`: `useEvents(uf, cidade, page)` (`GET /events` com `city=uf/cidade`, `status=PUBLISHED`, `from`, `page`, `size` padrão), `usePublishedEvent(uf, cidade, slug)` (novo endpoint), e `useVenue(id)` (`GET /venues/{id}`) em `src/api/venues.ts`.
- `from` = instante atual em ISO 8601, fixado por montagem do componente (`useMemo`/estado inicial) e entrando na query key arredondado ao minuto, para que a página não refaça a consulta a cada render. `placeholderData: keepPreviousData` na troca de página.
- Página atual na query string (`?page=N`, base 1 na URL, base 0 na API), via `useSearchParams`, para permitir voltar/compartilhar.
- Fuso: `useCity(uf, cidade).data.timezone` alimenta a formatação; enquanto a cidade carrega, a lista aguarda (estado de carregando), sem formatar com fuso errado.
- Funções puras em `src/format/`: `formatDateTime(iso, timeZone)` (`Intl.DateTimeFormat('pt-BR', { dateStyle: 'medium', timeStyle: 'short', timeZone })`, com fallback ao fuso do navegador em `RangeError`), `formatPrice({ isFree, priceMinCents, priceMaxCents })` (`Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })`, separador " – "), `nextOccurrence(occurrences, now)` e `futureOccurrences(occurrences, now)`; `now` é parâmetro, para testes determinísticos.
- Componentes: `EventList`, `EventCard` (`Link`), `Pagination`, `EventDetail`; reuso de `ErrorMessage`. Estados: carregando (`aria-busy`), vazio, erro.
- Endereço do local: `VenueRef` não traz endereço; o detalhe faz uma segunda chamada `GET /venues/{id}` (já público). Alternativa descartada: estender `VenueRef` com endereço, que alteraria o contrato compartilhado e o payload de todas as listagens. Se o local falhar, o detalhe mostra só o nome (degradação).
- Testes: Vitest + RTL + MSW com `zz/cidade-teste` (handlers em `src/test/server.ts`); formatação com testes unitários, incluindo fuso `America/Recife` e entradas inválidas. Teste de integração do endpoint com Testcontainers, no estilo de `EventsListAndReplaceApiIntegrationTest`.

**Premissas registradas**
- "Sessão futura" no card e no detalhe considera `startsAt` posterior ao instante do navegador; no card apenas `SCHEDULED`; no detalhe todas as futuras, com o status (cancelada/esgotada) visível.
- `size` fixo em 12 na lista.
- Textos de interface em português.

## Risks / Trade-offs

- [Relógio do navegador diferente do servidor] → a lista usa `from` do cliente, mas o servidor também ordena por seu próprio "agora"; divergência só afeta eventos no limite do instante, aceitável.
- [Segunda chamada para o endereço] → pequena latência extra; mitigada pela degradação e pelo cache do TanStack Query.
- [Evento que deixa de ser publicado entre lista e detalhe] → detalhe responde 404 e mostra `ErrorMessage`.
- [Dependência do `Intl` no Node dos testes] → Node 24 inclui ICU completo; os testes fixam `pt-BR` e comparam textos exatos, normalizando espaços não separáveis (U+00A0) do formato de moeda.

## Open Questions

Nenhuma bloqueante.
