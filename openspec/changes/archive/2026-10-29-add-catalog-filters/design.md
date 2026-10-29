## Context

Hoje `EventList` lê só `?page=`, e `useEvents(uf, slug, page)` envia `city`, `status`, `from` (instante atual, fixo durante a vida do componente), `page` e `size`. `GET /events` já aceita `category` (slug, inclui subcategorias), `from` e `to` (ISO 8601 com fuso), então não há mudança de contrato. `localToIso` (hora local + fuso IANA → ISO com offset, correto na virada de horário de verão) vive em `src/admin/datetime.ts`. `useCategories`/`flattenCategories` já existem em `src/api/categories.ts` (usados no formulário do admin). `City.timezone` vem de `useCity`.

## Goals / Non-Goals

**Goals:**
- Filtros de categoria e período na lista, persistidos na query string, calculados no fuso da cidade.
- Lógica de intervalos e de parsing da URL em funções puras, testadas sem React.

**Non-Goals:**
- Mudanças no back-end, OpenAPI, SQL ou schema do agente.
- Busca por texto, ordenação, filtro por local/preço.

## Decisions

1. **Mover `datetime.ts` para `src/format/datetime.ts`** (com o teste). É usado pelo admin e agora pelo catálogo público, então não deve morar em `admin/`. Atualizar imports em `admin/eventDraft.ts` e nos testes. Sem mudança de comportamento.

2. **Módulo `src/format/period.ts` (puro)**, sem React:
   - `type PeriodPreset = 'hoje' | 'fim-de-semana' | '7-dias'`.
   - `periodRange(preset, now: Date, timeZone: string): { from: string; to: string }`.
   - `customRange(de: string | undefined, ate: string | undefined, now, timeZone): DateRange | null`, com `type DateRange = { from: string; to?: string }` (`from` sempre presente; `to` ausente quando só há `de`). Retorna `null` quando não há datas ou quando `isInvalidRange(de, ate, now, timeZone)`: `de > ate` ou `ate` anterior a hoje no fuso da cidade. `EventList` passa o resultado de `isInvalidRange` a `CatalogFilters`, que exibe a mensagem "A data inicial deve ser anterior à final".
   - Dia local de `now`: `isoToLocal(now.toISOString(), timeZone)` dá `AAAA-MM-DDTHH:mm`. O dia da semana e a soma de dias são feitos em aritmética UTC sobre `AAAA-MM-DD` (`Date.UTC` + `getUTCDay`), sem depender do fuso do navegador. Os limites são strings locais `…T00:00` / `…T23:59` convertidas com `localToIso`.
   - Fim de semana: com `dow = getUTCDay()` do dia local, sábado = hoje + (6 − dow) se dow < 6; se dow = 6, hoje; se dow = 0, hoje − 1. Domingo = sábado + 1.
   - `from` final = o maior entre o início do intervalo e `now` (ISO com offset de `localToIso` para o início, `now` formatado em ISO para o clamp). Comparar por `Date.parse`.
   - `to` termina em 23:59 (`localToIso` trabalha em minutos): sessões entre 23:59:01 e 23:59:59 ficam de fora. Aceitável e documentado em comentário.

3. **Parsing da URL em `src/components/filterParams.ts` (puro)**: `parseFilters(params: URLSearchParams): { categoria?: string; periodo?: PeriodPreset; de?: string; ate?: string }` e `hasFilters`. Valida `periodo` contra a lista fixa, `de`/`ate` com `isValidLocal(`${v}T00:00`)`; `periodo` válido descarta `de`/`ate`. `GET /events` responde 404 para categoria inexistente, então o slug nunca é enviado sem validação: com `?categoria=` na URL, a consulta de eventos fica desabilitada (`enabled: false`, lista em "Carregando eventos…") até `GET /categories` terminar. Depois, `category` só é enviado se o slug existir na árvore (`flattenCategories`); slug inexistente é ignorado sem erro e o select mostra "Todas". Se `GET /categories` falhar, a categoria da URL é ignorada, o select some e a lista carrega sem `category`. Sem `?categoria=`, a lista não espera as categorias.

4. **`useEvents` recebe `{ page, category?, range? }`**, onde `range = { from, to? }` já calculado. A chave de query inclui `category`, `from` e `to`. Sem período, mantém o `from = agora` arredondado ao minuto (`useFrom`). Os intervalos usam o mesmo `now` congelado do componente (`useState(() => new Date())`, já existente em `EventList`) para a chave de query ser estável entre renders.

5. **Componentes**: `CatalogFilters` (select de categoria, botões de atalho de período com `aria-pressed`, dois `input type="date"` para `de`/`ate` e "Limpar filtros") dentro de `EventList` acima da lista. Estado único na URL via `useSearchParams`; toda mudança de filtro cria novos params sem `page`. O formulário de intervalo aplica ao mudar um campo; com `de > ate`, mostra a mensagem e não aplica intervalo (a URL mantém os valores digitados para o usuário corrigir, mas `customRange` devolve `null`).

6. **Estado vazio**: `EventList` escolhe entre "Nenhum evento encontrado." e "Nenhum evento para esses filtros" (+ botão "Limpar filtros") conforme `hasFilters(filters)` efetivo (filtros válidos aplicados).

7. **Sessão exibida no card com período ativo**: `nextOccurrence(occurrences, now, to?)` em `format/date.ts` ganha o limite superior opcional `to`. `EventCard` recebe `range?: { from: Date; to?: Date }`: com período ativo usa `from` (já ≥ agora) no lugar de `now` e `to` como limite, mostrando a primeira sessão `SCHEDULED` dentro do intervalo; sem período, o comportamento atual (próxima sessão geral). Como `from`/`to` vêm das mesmas funções puras de `period.ts`, o card e a API usam o mesmo intervalo. Sessão `SCHEDULED` dentro do intervalo sempre existe para eventos devolvidos pela API; se não houver (ex.: só sessões `SOLD_OUT` no intervalo), o card exibe "Sem sessões no período".

8. **Pagination** não muda; `goTo` passa a preservar os filtros ao alterar `page`.

## Risks / Trade-offs

- [Cache/refetch ao mudar filtro] → `placeholderData: keepPreviousData` já existe; a lista antiga permanece até chegar a nova. Mitigação: nenhuma necessária.
- [Slug de categoria na URL antes do carregamento das categorias] → enviado sem validar (decisão 3); se a categoria não existir a API devolve lista vazia ou erro 400, tratado pelos estados existentes; após carregar, o slug inválido é ignorado.
- [Fim de semana em domingo mostra o sábado já passado] → mitigado pelo clamp de `from` em `now`.
- [Semântica de "Hoje" = restante do dia] → intencional (não mostrar o que já passou).

## Migration Plan

Sem migrações nem mudanças de contrato. Reversível revertendo o commit. Nenhum script SQL novo; nenhuma mudança no `api/openapi.yaml`.

## Open Questions

Nenhuma. Premissas assumidas: `de`/`ate` isolados são aceitos (só início ou só fim); `periodo` prevalece sobre `de`/`ate`; intervalo invertido ou com `ate` anterior a hoje é ignorado com mensagem, sem enviar `to`.
