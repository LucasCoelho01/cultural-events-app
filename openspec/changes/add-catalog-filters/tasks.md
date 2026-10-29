# Tasks

> Sem mudança de API: não há tarefa de `api/openapi.yaml`, SQL ou teste com Testcontainers; os testes são Vitest + MSW no `frontend/`.

## 1. Mover utilitário de data

- [x] 1.1 Mover `src/admin/datetime.ts` e `datetime.test.ts` para `src/format/`, atualizar imports (`admin/eventDraft.ts`, testes) e verificar com `npm test` e `npm run build` sem erros

## 2. Intervalos de período (puros)

- [x] 2.1 Criar `src/format/period.ts` com `periodRange` (hoje, fim-de-semana, 7-dias) e `customRange`/`isInvalidRange` (intervalo invertido ou `ate` anterior a hoje no fuso da cidade), com `now` e `timeZone` injetados e clamp de `from` em `now`; verificar com `period.test.ts`
- [x] 2.2 Escrever `period.test.ts` (fuso `America/Recife`): virada de dia (22:30 local = dia seguinte em UTC), quarta/sexta/sábado/domingo para o fim de semana, 7 dias, intervalo personalizado só com `de`, só com `ate`, `de` depois de `ate` e `ate` anterior a hoje no fuso da cidade (inclusive virada de dia UTC) retornando `null`; `npm test` passa

## 3. Filtros na URL (puros)

- [x] 3.1 Criar `src/components/filterParams.ts` com `parseFilters`, `hasFilters`, `withFilters` e `clearFilters` (atualiza params e remove `page`); verificar com testes unitários
- [x] 3.2 Função pura que decide a categoria efetiva (`resolveCategory(slug, categoriesState)`: `loading` / slug existente / slug inexistente / falha) com testes unitários dos quatro casos; `npm test` passa
- [x] 3.3 Testes: `periodo` inválido, data inexistente (`2099-02-31`), `periodo` prevalecendo sobre `de`/`ate`, mudança de filtro removendo `page`, limpar removendo tudo; `npm test` passa

## 4. API e lista

- [x] 4.1 Estender `useEvents` com `category`, `range` (`from`/`to`) e `enabled`, incluindo-os na chave de query; sem período, manter `from = agora`; com `?categoria=` na URL, manter a consulta desabilitada até `GET /categories` terminar e só enviar `category` se o slug existir; verificar que os testes existentes de `pages.test.tsx` continuam passando
- [ ] 4.2 Criar `CatalogFilters` (select de categoria com `flattenCategories` e indentação, atalhos de período com `aria-pressed`, `input type="date"` de/até, mensagem de intervalo inválido (invertido ou `ate` anterior a hoje), "Limpar filtros" só com filtro ativo) e integrá-lo a `EventList` com `useCity().timezone`; verificar no navegador com `npm run dev`
- [x] 4.3 Estado vazio com filtro ativo ("Nenhum evento para esses filtros" + ação de limpar); `goTo` de `Pagination` preservando filtros; verificar com teste de página
- [x] 4.4 Tratar falha de `GET /categories` (categoria da URL ignorada, select oculto, lista funcional) e fuso indisponível (`ErrorMessage`); verificar com testes de página
- [x] 4.5 Estender `nextOccurrence` com o limite `to` e passar `range` ao `EventCard`, exibindo a primeira sessão `SCHEDULED` dentro do intervalo ou "Sem sessões no período"; verificar com testes unitários de `format/date.ts` (dentro, fora, `CANCELLED`/`SOLD_OUT` no intervalo, sem `to`)

## 5. Testes de página (MSW, `zz/cidade-teste`)

- [x] 5.1 Escolher categoria: query com `category=shows`, URL `?categoria=shows`, `page=0`; "Rock" indentada; "Todas" remove o filtro
- [x] 5.2 Atalhos de período: `from`/`to` com offset `-03:00` (relógio fixo com `vi.setSystemTime`), URL `?periodo=…`; intervalo personalizado com `?de=…&ate=…`; início depois do fim e `ate` anterior a hoje mostram a mensagem e não enviam `to`
- [x] 5.3 Mudar filtro estando em `?page=3` volta para a página 1 (URL sem `page`, API com `page=0`); filtros mantidos ao avançar de página
- [x] 5.4 Valores inválidos na URL ignorados sem erro; "Limpar filtros" (barra e estado vazio) zera a URL e a requisição
- [x] 5.5 Categoria na URL: `?categoria=shows` não chama `/events` antes de `/categories` responder (handler com `delay`) e depois envia `category=shows` uma vez; slug inexistente e falha 500 de `/categories` não enviam `category` e a lista carrega sem erro
- [x] 5.6 Card com período: com `?periodo=fim-de-semana` e relógio fixo, evento com sessões na quarta e no sábado mostra a data do sábado; sem sessão no intervalo mostra "Sem sessões no período"; sem período mostra a próxima sessão geral
- [x] 5.7 Erro 400 da API com `?categoria=shows` mostra `ErrorMessage` com o `detail`; partindo de `?categoria=shows&page=2`, clicar "Hoje" deixa a URL sem `page` e a API recebe `page=0`
- [x] 5.8 Rodar `npm test`, `npm run lint` e `npm run build` e verificar que passam; conferir com busca que `frontend/src` não contém "São Paulo" nem "sao-paulo"

## 6. Documentação

- [x] 6.1 Atualizar o `frontend/README.md` com os parâmetros da query string (`categoria`, `periodo`, `de`, `ate`, `page`); verificar que o texto lista todos
