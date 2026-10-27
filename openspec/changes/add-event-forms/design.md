## Context

- O front-end tem rotas de cidade (`/:uf/:cidade`, `/:uf/:cidade/eventos/:slug`), cliente `openapi-fetch` tipado (`src/api/client.ts`), `useDefaultCity`, `ErrorMessage`, `Pagination`/`parsePage` e funções de formato em `src/format/`.
- A API de escrita existe: `POST /events` (`EventInput`, 201), `PUT /events/{id}` (`EventUpdateInput` com `version` e `status` obrigatórios; substituição completa: campos opcionais ausentes viram nulos e sessões ausentes são removidas), `DELETE /events/{id}` (204), `GET /events/{id}`, `GET /categories` (árvore com `children`), `GET /venues` (paginado, `size` até 100).
- Erros: `Problem` com `errors[]` (`field`, `message`) no 422; 409 serve a dois casos distintos (lock otimista e slug já existente), ambos `Problem` sem código que os diferencie.
- Regras do servidor relevantes: `PUBLISHED` exige ao menos uma sessão futura (rascunhos podem não ter sessões); `isFree` não admite preço maior que zero; preço máximo não pode ser menor que o mínimo; sessões não podem repetir o início; o fim não pode ser anterior ao início.
- Motivação e non-goals em `proposal.md`.

## Goals / Non-Goals

**Goals:** tabela, formulário único (criar/editar), exclusão com confirmação na página, validação de cliente, tratamento de 409 e 422.
**Non-Goals:** autenticação (rotas `/admin` e a API de escrita ficam abertas nesta fase), upload de imagem, outra cidade além da padrão, administração de locais/categorias, mudança de contrato ou de back-end.

## Decisions

**Rotas e cidade.** `/admin/eventos`, `/admin/eventos/novo` e `/admin/eventos/:id/editar` dentro do `Layout`. Rotas estáticas vencem as dinâmicas do React Router, então `/admin/eventos` não cai em `/:uf/:cidade` (UF real tem 2 letras, então `admin` nunca colide). A cidade administrada é a padrão (`useDefaultCity`): fornece `id` (para `cityId`), `timezone` e `uf`/`slug` (para `GET /venues?city=`). O cabeçalho continua mostrando a cidade padrão nessas rotas.

**Formulário sem biblioteca.** React 19 basta: `useActionState` gerencia o envio assíncrono (resultado, `isPending`) e `<form action>` o dispara. Os campos são controlados (estado local do formulário, com `name`), porque a lista dinâmica de sessões, a desabilitação dos preços por "Gratuito" e a edição sobre dados carregados precisam de estado; um formulário só de `FormData` não reterria os valores após o envio com erro. Não se adiciona react-hook-form/zod: o formulário tem um único schema, as regras de validação são poucas e puras, e o servidor continua sendo a autoridade. Risco: a reinicialização automática de formulários do React 19 ao fim de uma action; mitiga-se mantendo os campos controlados e conferindo em teste que os valores permanecem após um 422. Se isso falhar, o fallback é `onSubmit` + `useTransition`, ainda sem dependência.

**Modelo do formulário e funções puras** (em `src/admin/`, com testes unitários):
- `parseReais(text) -> cents | null | 'invalid'` e `formatReais(cents)` (vírgula ou ponto, até 2 casas; arredondamento por inteiros, sem `parseFloat` * 100).
- `localToIso(local: 'YYYY-MM-DDTHH:mm', timeZone) -> string` e `isoToLocal(iso, timeZone) -> string`: conversão por `Intl.DateTimeFormat` com `timeZone` (método de dois passes para achar o offset, correto em transições de horário de verão), sem biblioteca de datas; a saída é ISO com offset (ex.: `2099-03-01T17:00:00-03:00`).
- `validateEvent(draft, now) -> FieldErrors` espelhando o contrato: título não em branco ≤ 255, categoria obrigatória, slug `^[a-z0-9]+(-[a-z0-9]+)*$` ≤ 255, descrição ≤ 20000, classificação ≤ 10, links `^https?://\S+$` ≤ 2048, preços ≥ 0 e mínimo ≤ máximo, fim ≥ início, início não repetido, `PUBLISHED` com ao menos uma sessão futura.
- `toCommand(draft, cityId, timeZone)` monta `EventInput`/`EventUpdateInput` e `fromEvent(event, timeZone)` faz o caminho inverso.
- `mapProblemErrors(problem) -> { fields, top }`: converte `errors[].field` em chaves do formulário (`priceMinCents`/`priceMaxCents` → campos de preço em reais; `occurrences[i].startsAt|endsAt|status` → a sessão `i`) e envia o restante, mais o `detail`, ao topo.

**Divergência consciente da solicitação.** O pedido cita "pelo menos uma sessão". O contrato aceita rascunhos sem sessões e só exige sessão futura para `PUBLISHED`; validar mais do que o servidor impediria salvar rascunhos válidos. A regra espelhada é, portanto, "`PUBLISHED` exige ao menos uma sessão futura".

**Sessões completas.** O `PUT` substitui as sessões, e perder `endsAt`/`status` ao editar seria perda silenciosa de dados. Cada linha de sessão tem início, fim opcional e situação (`SCHEDULED`/`CANCELLED`/`SOLD_OUT`), além do pedido original (data e hora), e a edição reenvia o que foi carregado.

**409 de concorrência vs. outros 409.** O servidor usa 409 tanto para versão desatualizada quanto para slug duplicado, sem código distinto. Não se interpreta o texto de `detail` (frágil): em um 409 no `PUT`, o formulário refaz `GET /events/{id}`; se a `version` retornada difere da enviada, é concorrência (aviso "O evento foi alterado por outra pessoa" + "Recarregar", mantendo os valores digitados até o clique); se é igual, mostra o `detail` no topo. No `POST`, 409 é sempre o `detail` no topo (slug).

**Dados e cache.** `src/api/events.ts` ganha `useAdminEvents(page)` (`GET /events` com `size` e sem `status`; a cidade vai omitida, a API usa a padrão), `useEvent(id)` (`GET /events/{id}`), `useCreateEvent`, `useUpdateEvent`, `useDeleteEvent` (mutações do TanStack Query que invalidam `['events']`). `src/api/categories.ts` (`useCategories`, com achatamento da árvore em opções com `depth`) e `useVenues` em `src/api/venues.ts` (`GET /venues?city=uf/slug&size=100`; limite de 100 locais por cidade é uma limitação conhecida desta fase). Mutações não são repetidas (padrão do TanStack Query).

**Exclusão.** Estado local por linha (`confirmingId`): "Excluir" mostra "Confirmar"/"Cancelar" na linha, `role="alertdialog"` com rótulo, foco no botão "Cancelar" ao abrir. Sem `window.confirm`.

**Layout.** Link "Admin" (`text-sm text-gray-500`) no cabeçalho ao lado da cidade.

**Testes.** Vitest + RTL + MSW com `zz/cidade-teste`: criação (corpo do `POST` conferido: centavos, offset, `cityId`), edição (`PUT` com `version` e sessão preservada), 422 por campo (e topo), 409 de concorrência (e 409 de slug), exclusão (confirmar/cancelar/404), carregamento de opções, validação do cliente e unitários das funções puras. Testes de integração com Testcontainers não se aplicam (sem back-end/banco).

**Artefatos.** Scripts SQL novos: nenhum. Mudanças no OpenAPI: nenhuma.

## Risks / Trade-offs

- [Rotas `/admin` e API de escrita sem proteção] → documentado como non-goal; qualquer pessoa com acesso à aplicação pode alterar eventos. Recomenda-se uma change de autenticação antes de expor fora do ambiente interno.
- [Conversão de fuso sem biblioteca] → coberta por testes com `America/Recife`; incluir também um fuso com horário de verão (ex.: `America/New_York`) nos testes unitários para exercitar a transição.
- [Comparar `version` após 409 tem uma janela de corrida] → se o evento mudar entre o 409 e o `GET`, a `version` ainda difere, e o aviso é exibido corretamente; o único falso negativo exigiria a versão voltar ao valor enviado, o que o contador não permite.
- [Mais de 100 locais na cidade] → o select mostra só os 100 primeiros por nome; aceito nesta fase.
- [Reset de formulário do React 19] → ver "Formulário sem biblioteca".

## Open Questions

Nenhuma bloqueante. Premissas registradas: cidade administrada é a padrão; início e fim das sessões são digitados com `datetime-local`; texto da interface em português.
