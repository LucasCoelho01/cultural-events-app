# Design

## Context

Motivação e escopo em `proposal.md`; requisitos em `specs/ingestion-monitoring/spec.md`.

- `ingestion_run`, `source` e `event_source_link` existem desde `V007`. O back-end ainda não tem entidade nem módulo para eles; o agente escreve neles pela role `agent_writer`.
- O agente grava `errors` como `{"external_id", "message"}`, e a `message` de um item que falha tem a forma `CODIGO: detalhe` (`graph.py`: `f"{exc.code}: {detail}"`). Erros globais usam `external_id = "-"` e uma mensagem sem código.
- Não existe endpoint de despublicação individual. Despublicar é um `PUT /events/{id}` com `status` e `version`: `EventsService.replace` compara `version` (409 se diferir), valida e chama `Event.replaceWith`, que trava em `locked_fields` as colunas alteradas (aqui, `status`) e incrementa `version` via `@Version`.
- Os ids públicos de evento são UUID; os de execução são `BIGSERIAL`. A paginação das listagens é base 0, e o front converte de `?page=` base 1.
- Ainda não há autenticação (`add-admin-auth`); as rotas `/admin/...` ficam abertas como as demais operações de escrita.

## Goals / Non-Goals

**Goals:**
- Consultar execuções e eventos criados sem alterar o schema nem o contrato do agente.
- Despublicar em lote pelo mesmo caminho de domínio da edição individual.

**Non-Goals:**
- Controle de acesso, reexecução do agente, edição de erros, paginação dos erros e dos eventos criados.

## Decisions

**1. Módulo `ingestion`, só leitura sobre as tabelas do agente.** Pacotes `api/application/domain/infrastructure`, como os demais. As entidades `IngestionRun` e `Source` são `@Immutable` (o back-end nunca as grava). As consultas de "eventos criados" usam SQL nativo ou JPQL sobre uma entidade `@Immutable` de `event_source_link`; o JPA não mapeia essa tabela para escrita, para a exclusão continuar deixando lápide pelo `ON DELETE SET NULL`. *Alternativa descartada:* contar `last_run_id`, que cada execução sobrescreve.

**2. Definição de "criado na execução".** Vínculo `l` com `l.source_id = run.source_id`, `l.event_id IS NOT NULL`, `l.created_at >= run.started_at` e `l.created_at <= COALESCE(run.finished_at, now())`, evento com `origin = 'AGENT'` e `l.id = (SELECT min(id) FROM event_source_link WHERE event_id = l.event_id)`. O último critério vai além do pedido: a deduplicação cria vínculo da fonte nova apontando para um evento de outra fonte (contrato §8), e sem ele o lote despublicaria esse evento. O "mais antigo" é o mesmo critério que o agente usa para decidir quem é dono do evento. Duas execuções da mesma fonte em paralelo se confundiriam, e o agente não as permite. *Limite aceito:* a janela depende de `created_at`, que o agente nunca regrava.

**3. Despublicação reaproveita o domínio de `events`.** Um método `EventsService.unpublish(Collection<UUID>)` (ou `Event.unpublish()` mais um serviço de aplicação) carrega os eventos `PUBLISHED`, muda `PUBLISHED → DRAFT` e grava com o mesmo `events.update(event)`, que força o incremento de `version`. A transição é a mesma do `PUT` e também grava `status` em `locked_fields`. O destino é `DRAFT` (e não `ARCHIVED` ou `CANCELLED`) porque o evento deixa de ser público, mas pode ser revisado e republicado no admin. *Alternativa descartada:* `UPDATE ... SET status` em massa, que pularia `version` e `locked_fields`.

**4. Lote atômico, conflito vira 409.** Uma transação: lê a execução (404 se não existe, 409 se `RUNNING`), seleciona os eventos publicados e grava todos. Se o Hibernate lançar `ObjectOptimisticLockingFailureException` (outro usuário editou no meio), a transação volta inteira e o handler global responde 409. Tudo-ou-nada é mais simples de explicar ao admin ("tente novamente") do que um lote parcial. *Alternativa descartada:* pular o evento em conflito e contar parcial.

**5. Parsing dos erros no back-end.** `errors` (JSONB) é lido como lista de `{external_id, message}`. Se `message` casa com `^([A-Z][A-Z0-9_]+): (.*)$`, `code` e `detail` saem do grupo 1 e 2; senão `code = null` e `detail = message`. Itens malformados (sem `message`) viram `detail = "-"`, para um registro ruim do agente nunca derrubar a página. O contrato continua sendo o do agente; a API só interpreta a convenção dele.

**6. `durationSeconds` calculado na consulta**, `finished_at - started_at` em segundos, nulo para `RUNNING`.

**7. Filtro `source` pelo `code`**, e não pelo id, para a URL do front ser legível. `status` é validado contra o enum antes da consulta (400 em `Problem` pelo handler existente). Um `code` desconhecido devolve página vazia, sem erro.

**8. `GET /admin/ingestion-sources`.** O pedido não o cita, mas o filtro por fonte do front precisa das opções, e derivá-las das execuções esconderia fontes sem execução. É uma listagem simples sem paginação (poucas fontes).

**9. Contrato OpenAPI primeiro.** Quatro operações novas sob a tag `ingestion`: `listIngestionRuns`, `getIngestionRun`, `unpublishIngestionRun`, `listIngestionSources`. Schemas: `IngestionRunStatus` (enum), `IngestionRunSummary`, `IngestionRunPage`, `IngestionRunDetail`, `IngestionRunError`, `IngestionCreatedEvent`, `UnpublishResult`, `SourceRef`. Reaproveita `PageMetadata` e `Problem`. Depois, o `openapi-generator` gera as interfaces e o `schema.d.ts` do front.

**10. Front-end.** Rotas `admin/execucoes` e `admin/execucoes/:id` no `App.tsx`, páginas `AdminRunsPage` e `AdminRunDetailPage` em `src/admin`, hooks em `src/api/ingestion.ts` (TanStack Query, com `invalidateQueries` após o `POST`). O padrão de confirmação na página da exclusão de eventos é reaproveitado. Os filtros ficam na query string, como no catálogo. Cores do badge: `SUCCESS` verde, `PARTIAL` amarelo, `FAILED` vermelho, `RUNNING` azul; o texto do badge sempre aparece, para a cor não ser a única pista. A contagem da confirmação sai da lista `createdEvents` (itens com `status = PUBLISHED`).

## Risks / Trade-offs

- [Execução com milhares de eventos faz o detalhe pesar] → sem paginação por ora, já que o limite de páginas por execução do agente é pequeno; paginar vira mudança futura se a Sympla crescer.
- [`created_at` do vínculo mudar no agente] → a regra é contrato implícito; registrar na documentação da capability e cobrir com um teste de integração que cria vínculos via SQL.
- [Despublicar trava `status` em `locked_fields`] → inofensivo, porque o agente não escreve `status`; a trava só documenta a correção manual.
- [Rotas `/admin` sem autenticação] → mesma situação de `/events`; resolvido no `add-admin-auth`.
- [Lote tudo-ou-nada pode falhar por um conflito isolado] → o admin tenta de novo; o 409 traz mensagem clara.
