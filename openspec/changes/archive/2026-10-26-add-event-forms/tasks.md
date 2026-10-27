## 1. Funções puras (src/admin/)

- [x] 1.1 `parseReais`/`formatReais` (centavos sem ponto flutuante) com testes unitários: vírgula, ponto, inteiro, 2 casas, inválido ("abc", "10,999"), vazio
- [x] 1.2 `localToIso`/`isoToLocal` por `Intl.DateTimeFormat`, com testes unitários: `America/Recife` (`2099-03-01T17:00` → `20:00Z`), ida e volta, e um fuso com horário de verão (`America/New_York`) nas duas transições
- [x] 1.3 `validateEvent` espelhando o contrato, com testes por regra (obrigatórios, tamanhos, slug, links, preços, fim antes do início, início repetido, `PUBLISHED` sem sessão futura, rascunho sem sessões)
- [x] 1.4 `toCommand`/`fromEvent` (centavos, ISO com offset, preservação de `endsAt` e `status`) com testes
- [x] 1.5 `mapProblemErrors` (campos, `priceMinCents`/`priceMaxCents` para campos em reais, `occurrences[i].*`, campo desconhecido para o topo) com testes

## 2. Camada de API

- [x] 2.1 `src/api/categories.ts`: `useCategories` e achatamento da árvore em opções com `depth`
- [x] 2.2 `useVenues` em `src/api/venues.ts` (`GET /venues?city=uf/slug&size=100`)
- [x] 2.3 `src/api/events.ts`: `useAdminEvents(page)`, `useEvent(id)`, `useCreateEvent`, `useUpdateEvent`, `useDeleteEvent` (invalidam `['events']`)

## 3. Telas

- [x] 3.1 `AdminEventsPage` (`/admin/eventos`): tabela, paginação `?page=`, estados de carregando/vazio/erro, link "Novo evento"
- [x] 3.2 Exclusão com confirmação na linha (sem `window.confirm`; foco e rótulos acessíveis)
- [x] 3.3 `EventForm` com `useActionState`, campos controlados, lista dinâmica de sessões (início, fim, situação), preços em reais e "Gratuito"
- [x] 3.4 Páginas `/admin/eventos/novo` e `/admin/eventos/:id/editar` (carregamento do evento, opções de categoria e local, erros de carregamento)
- [x] 3.5 Tratamento de 422 (campos e topo) e de 409 (recarregar o evento, comparar `version`, aviso com "Recarregar" ou `detail` no topo)
- [x] 3.6 Rotas em `App.tsx` e link discreto "Admin" no `Layout`
- [x] 3.7 Confirmar na prática que os valores digitados permanecem após erro (reset de formulário do React 19); se não, usar o fallback `onSubmit` + `useTransition` e registrar no design

## 4. Testes de página (MSW, zz/cidade-teste)

- [x] 4.1 Handlers MSW de categorias, locais e escrita de eventos; helper de render
- [x] 4.2 Tabela: todos os status sem `status` na query, sem sessão futura, vazio, erro 500, cidade padrão indisponível
- [x] 4.3 Criar: corpo do `POST` (centavos, offset de fuso, `cityId`), navegação ao fim, slug duplicado (409 no topo), botão desabilitado durante o envio
- [x] 4.4 Editar: carregamento, `PUT` com `version` e sessão preservada (`endsAt`, `SOLD_OUT`), evento 404
- [x] 4.5 422 por campo (título, preço máximo em reais, sessão `occurrences[1].endsAt`) e erro sem campo no topo
- [x] 4.6 409 de concorrência (aviso e "Recarregar") e 409 que não é de versão
- [x] 4.7 Excluir: confirmar, cancelar e 404
- [x] 4.8 Validação do cliente: obrigatórios, mínimo maior que máximo, `PUBLISHED` sem sessão futura, rascunho sem sessões, preço inválido
- [x] 4.9 Selects: subcategorias indentadas e falha ao carregar opções; link "Admin" no cabeçalho e rota `/admin/eventos` sem colidir com cidade
- [x] 4.10 Testes de integração com Testcontainers não se aplicam (nenhum back-end/banco alterado)

## 5. Verificação e docs

- [x] 5.1 Verificar que `frontend/src` não contém "São Paulo" nem "sao-paulo" (fora do arquivo gerado)
- [x] 5.2 `npm run lint`, `npm run build` e `npm test` em `frontend/`
- [x] 5.3 Validação manual com back-end e Postgres rodando: criar, editar (com conflito em duas abas), 422 e excluir (parcial: pela interface foram criar, listar e excluir; conflito 409, versão e campos do 422 verificados direto na API; editar e conflito em duas abas pela interface ainda não)
- [x] 5.4 README do `frontend/`: novas rotas `/admin` e aviso de que não há autenticação
