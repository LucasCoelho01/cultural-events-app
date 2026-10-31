# Proposal

## Why

O agente de ingestão grava direto no Postgres, sem revisão humana. Hoje a única forma de saber se uma execução funcionou, ou de desfazer uma importação ruim, é consultar o banco à mão. O admin precisa mostrar as execuções, os erros e os eventos que cada uma criou, e permitir tirar do ar, de uma vez, o que uma execução publicou por engano.

## What Changes

- Três operações novas em `/api/v1/admin/ingestion-runs`, definidas primeiro no `api/openapi.yaml`:
  - `GET /admin/ingestion-runs`: lista paginada (página base 0, como as demais listagens), mais recentes primeiro, com filtros opcionais `source` (code da fonte) e `status`.
  - `GET /admin/ingestion-runs/{id}`: detalhe com os erros (coluna `errors`) e os eventos criados na execução.
  - `POST /admin/ingestion-runs/{id}/unpublish`: despublica em lote os eventos criados na execução que ainda estão `PUBLISHED` e devolve a quantidade. Execução `RUNNING` responde 409.
- "Eventos criados na execução" é derivado sem mudar o schema: `event_source_link` da fonte da execução, com `created_at` entre `started_at` e `finished_at` (ou `now()` se ainda não terminou), `event_id` não nulo e `event.origin = 'AGENT'`. Um critério a mais: o vínculo precisa ser o mais antigo do evento. Vínculos criados por deduplicação apontam para eventos de outra fonte, e sem esse critério o lote despublicaria o evento de outra fonte.
- A despublicação em lote usa a mesma transição de status e o mesmo controle de `version` do `PUT /events/{id}`: `PUBLISHED` passa para `DRAFT`. Nada é excluído, para não criar lápides.
- Front-end: página "Execuções do agente" (`/admin/execucoes`) com tabela, badge de status, contadores, duração e filtros por fonte e status; página de detalhe (`/admin/execucoes/:id`) com resumo, erros, eventos criados e o botão "Despublicar eventos desta execução", com confirmação que mostra a quantidade.
- Teste manual: requests novos na coleção Postman.

Fora do escopo: autenticação (vem no `add-admin-auth`), reexecutar o agente pelo app e editar erros.

## Capabilities

### New Capabilities
- `ingestion-monitoring`: consulta das execuções do agente (lista, detalhe, erros, eventos criados) e despublicação em lote dos eventos de uma execução, na API e no admin do front-end.

### Modified Capabilities

Nenhuma. A regra de despublicação reutiliza o comportamento já especificado em `events` e `event-admin`, sem alterar os requisitos deles.

## Impact

- **Contrato de schema com o agente:** nenhum. Não há script SQL novo, `schema_contract` continua em `1.0` e o agente não muda. A feature só lê `ingestion_run`, `source` e `event_source_link`, que já fazem parte do contrato (V007). Ela depende de uma regra do contrato, a de que `event_source_link.created_at` marca o momento em que o agente cria o vínculo; o agente não pode regravar essa coluna.
- **Efeito sobre o agente:** a despublicação em lote usa o mesmo caminho do `PUT`, então registra `status` em `event.locked_fields`. O agente já não escreve `status`, e a trava vale como marca de correção manual.
- **Back-end:** novo módulo `ingestion` (api, application, domain, infrastructure) em `com.culturalevents`, e acesso a `Event` pelo domínio de `events` para a transição de status.
- **API:** `api/openapi.yaml` ganha três operações, o schema de página de execuções e o detalhe.
- **Front-end:** duas páginas, rotas e hooks em `src/api`, mais `schema.d.ts` regenerado.
- **Postman:** coleção atualizada.
