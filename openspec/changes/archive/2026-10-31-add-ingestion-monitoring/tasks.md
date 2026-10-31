# Tasks

## 1. Contrato da API

- [x] 1.1 Adicionar ao `api/openapi.yaml` a tag `ingestion`, as operações `listIngestionRuns`, `getIngestionRun`, `unpublishIngestionRun` e `listIngestionSources` e os schemas listados no design (respostas 400, 404 e 409 com `Problem`); verificar com o lint/validação do OpenAPI e com a geração das interfaces (`mvnw` compila sem erro)
- [x] 1.2 Regenerar `frontend/src/api/schema.d.ts` a partir do contrato; verificar que `npm run build` (ou `tsc`) passa

## 2. Back-end: leitura

- [x] 2.1 Criar o módulo `ingestion` com as entidades somente leitura de `ingestion_run`, `source` e `event_source_link` e o repositório de consulta; verificar com um teste de integração (Testcontainers) que lê uma execução criada por SQL
- [x] 2.2 Implementar a listagem paginada com filtros `source` e `status`, ordem `started_at DESC, id DESC` e `durationSeconds`; testes de integração: sem filtro, filtro combinado, status inválido (400), fonte sem execuções (200 vazio), execução `RUNNING` com datas nulas
- [x] 2.3 Implementar `GET /admin/ingestion-sources`; teste de integração com a fonte `sympla`
- [x] 2.4 Implementar a consulta de eventos criados com os critérios do design (janela de `created_at`, `event_id` não nulo, `origin = AGENT`, vínculo mais antigo); testes de integração: evento criado, evento só atualizado (mesmo com `last_run_id` igual), vínculo por deduplicação de outra fonte e de evento do admin, lápide, execução sem `finished_at`
- [x] 2.5 Implementar o detalhe com o parsing de `errors` (código, `externalId`, `detail`, mensagem sem código, item malformado); testes unitários do parser e teste de integração do 200, do 404 e do 400 para id inválido

## 3. Back-end: despublicação em lote

- [x] 3.1 Implementar `POST /admin/ingestion-runs/{id}/unpublish` reaproveitando o domínio de `events` (`PUBLISHED → DRAFT`, `version` e `locked_fields` como no `PUT`), 404 e 409 para `RUNNING`; testes de integração: dois publicados e um rascunho, segunda chamada devolve 0, nenhum evento nem vínculo excluído, execução inexistente, execução `RUNNING`
- [x] 3.2 Cobrir o conflito de versão: teste de integração em que um evento muda no meio do lote e a resposta é 409 sem nenhum evento alterado
- [x] 3.3 Atualizar `postman/cultural-events.postman_collection.json` com os requests das quatro operações (com asserts de status); verificar executando a coleção com o Postman CLI/Newman contra o ambiente local

## 4. Front-end: lista

- [x] 4.1 Criar `src/api/ingestion.ts` com os hooks de listagem, de fontes, de detalhe e da mutação de despublicação (com invalidação das consultas); verificar com testes dos hooks via MSW
- [x] 4.2 Criar `AdminRunsPage` em `/admin/execucoes` com a tabela, o badge por status, os contadores, a duração ("Em andamento" quando `RUNNING`), os filtros por fonte e status na URL e a paginação; testes de componente para tabela, filtro, vazio e erro (`ErrorMessage`)
- [x] 4.3 Adicionar a rota e o link "Execuções do agente" na navegação do admin; verificar com o teste de rotas do `App`

## 5. Front-end: detalhe

- [x] 5.1 Criar `AdminRunDetailPage` em `/admin/execucoes/:id` com resumo, erros, eventos criados com link de edição e estados vazio, 404 e erro; testes de componente
- [x] 5.2 Implementar o botão "Despublicar eventos desta execução" com confirmação na página mostrando a quantidade, desabilitado em `RUNNING` ou sem evento publicado, e tratamento do 409; testes: confirmar, cancelar (sem `POST`), conflito e botão desabilitado

## 6. Fechamento

- [x] 6.1 Rodar a suíte completa do back-end (`mvnw verify`) e do front-end (`npm test`, lint e build) e fazer um teste manual de ponta a ponta no Docker Compose, com uma execução criada por SQL ou pelo agente, anotando o resultado na descrição do PR
- [x] 6.2 Rodar `openspec validate add-ingestion-monitoring --strict`; verificar que passa sem avisos

## Workflow follow-up

- Arquivar a mudança depois da revisão e do merge.
- Quando o `add-admin-auth` chegar, proteger as rotas `/admin/ingestion-*`.
