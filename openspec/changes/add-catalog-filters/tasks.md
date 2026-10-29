# Tasks

> Sem mudança de API: não há tarefa de `api/openapi.yaml`, SQL ou teste com Testcontainers; os testes são Vitest + MSW no `frontend/`.

## 1. Mover utilitário de data

- [ ] 1.1 Mover `src/admin/datetime.ts` e `datetime.test.ts` para `src/format/`, atualizar imports (`admin/eventDraft.ts`, testes) e verificar com `npm test` e `npm run build` sem erros

## 2. Intervalos de período (puros)

- [ ] 2.1 Criar `src/format/period.ts` com `periodRange` (hoje, fim-de-semana, 7-dias) e `customRange`, com `now` e `timeZone` injetados e clamp de `from` em `now`; verificar com `period.test.ts`
- [ ] 2.2 Escrever `period.test.ts` (fuso `America/Recife`): virada de dia (22:30 local = dia seguinte em UTC), quarta/sexta/sábado/domingo para o fim de semana, 7 dias, intervalo personalizado só com `de`, só com `ate`, e `de` depois de `ate` retornando `null`; `npm test` passa

## 3. Filtros na URL (puros)

- [ ] 3.1 Criar `src/components/catalogFilters.ts` com `parseFilters`, `isActive` e `withFilters` (atualiza params e remove `page`); verificar com testes unitários
- [ ] 3.2 Testes: `periodo` inválido, data inexistente (`2099-02-31`), `periodo` prevalecendo sobre `de`/`ate`, mudança de filtro removendo `page`, limpar removendo tudo; `npm test` passa

## 4. API e lista

- [ ] 4.1 Estender `useEvents` com `category` e `range` (`from`/`to`) e incluí-los na chave de query; sem período, manter `from = agora`; verificar que os testes existentes de `pages.test.tsx` continuam passando
- [ ] 4.2 Criar `CatalogFilters` (select de categoria com `flattenCategories` e indentação, atalhos de período com `aria-pressed`, `input type="date"` de/até, mensagem de intervalo invertido, "Limpar filtros" só com filtro ativo) e integrá-lo a `EventList` com `useCity().timezone`; verificar no navegador com `npm run dev`
- [ ] 4.3 Estado vazio com filtro ativo ("Nenhum evento para esses filtros" + ação de limpar); `goTo` de `Pagination` preservando filtros; verificar com teste de página
- [ ] 4.4 Tratar falha de `GET /categories` (select oculto, lista funcional) e fuso indisponível (`ErrorMessage`); verificar com testes de página

## 5. Testes de página (MSW, `zz/cidade-teste`)

- [ ] 5.1 Escolher categoria: query com `category=shows`, URL `?categoria=shows`, `page=0`; "Rock" indentada; "Todas" remove o filtro
- [ ] 5.2 Atalhos de período: `from`/`to` com offset `-03:00` (relógio fixo com `vi.setSystemTime`), URL `?periodo=…`; intervalo personalizado com `?de=…&ate=…`; início depois do fim mostra a mensagem e não envia `to`
- [ ] 5.3 Mudar filtro estando em `?page=3` volta para a página 1 (URL sem `page`, API com `page=0`); filtros mantidos ao avançar de página
- [ ] 5.4 Valores inválidos na URL ignorados sem erro; "Limpar filtros" (barra e estado vazio) zera a URL e a requisição
- [ ] 5.5 Rodar `npm test`, `npm run lint` e `npm run build` e verificar que passam; conferir com busca que `frontend/src` não contém "São Paulo" nem "sao-paulo"

## 6. Documentação

- [ ] 6.1 Atualizar o `frontend/README.md` com os parâmetros da query string (`categoria`, `periodo`, `de`, `ate`, `page`); verificar que o texto lista todos
