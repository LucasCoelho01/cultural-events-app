# Design

## Context

Estado atual (ver `proposal.md` para a motivação):

- Migrations numeradas em `database/migrations` (V001–V006), aplicadas à mão ou pelo `AbstractPostgresIntegrationTest`, que roda todos os `.sql` em ordem num único Postgres 16 por JVM. Constraints têm nome explícito; `set_updated_at()` (V004) é o trigger reutilizável.
- `event` já tem `origin` (`MANUAL`/`AGENT`) e `version`. A V005 deixou `locked_fields` anunciado para a fase 3.
- `EventsService.replace` faz `PUT`: carrega o `Event`, confere `version`, chama `Event.replaceWith(fields, occurrences)` e grava via `events.update`, que relê a linha depois (refresh). O slug nulo é regenerado por trigger a partir do título.
- A entidade usa `ddl-auto=validate`, então a coluna nova precisa estar mapeada na entidade.
- Triggers de `event` leem `category` e `venue` (search_vector, cidade do local); o agente precisa de `SELECT` neles, o que já está no escopo.

## Goals / Non-Goals

**Goals:**
- Schema de ingestão e role de escrita entregues em scripts novos, sem editar V001–V006.
- `locked_fields` mantido pelo back-end sem mudar o OpenAPI.
- `AGENT_CONTRACT.md` suficiente para implementar o agente Python sem ler o código Java.

**Non-Goals:**
- Implementar o agente, o scraper da Sympla ou a deduplicação (só o contrato).
- Expor `locked_fields`, fontes ou execuções na API ou no front-end.
- Impedir no banco que o agente grave em campo travado (a regra é do agente; ver Riscos).
- Gestão de senha/segredos da role.

## Decisions

**Scripts SQL novos** (nenhuma mudança no OpenAPI):
- `database/migrations/V007__add_ingestion_schema.sql`
- `database/migrations/V008__create_agent_writer_role.sql`

**1. Constraints e nomes (V007).** Nomes explícitos no padrão do projeto: `source_code_key`, `event_source_link_source_external_key`, `ingestion_run_status_valid`, `ingestion_run_counters_nonneg`, `schema_contract_version_format` (`version ~ '^\d+\.\d+$'`), `schema_contract_version_key`. `event_source_link.event_id` com `ON DELETE CASCADE` (apagar um evento limpa o vínculo); `last_run_id` com FK para `ingestion_run` `ON DELETE SET NULL`; `ingestion_run.source_id` obrigatório com FK. `source.city_id` nulo = nacional. `source` e `event_source_link` ganham `created_at`/`updated_at` com trigger `set_updated_at`; `ingestion_run` não tem `updated_at` (é um log com `started_at`/`finished_at`). `locked_fields` é `TEXT[] NOT NULL DEFAULT '{}'`; índices em FKs (`event_source_link(event_id)`, `ingestion_run(source_id, started_at DESC)`), como a V005 faz.
  *Alternativa:* `locked_fields` em tabela separada — descartada: o agente precisa lê-lo junto do evento e o `PUT` o atualiza na mesma linha/versão.

**2. Versão do contrato.** `schema_contract` com `version` única no formato `MAJOR.MINOR`. A regra "versão principal" é do cliente (agente lê a maior versão e compara o MAJOR); o banco só garante o formato. Mudança compatível (coluna opcional) sobe MINOR; incompatível sobe MAJOR, e cada migration que altere o contrato insere a nova linha. *Alternativa:* uma linha única atualizada — descartada: perde o histórico de aplicação.

**3. Role (V008).** `CREATE ROLE` está dentro de `DO $$ ... IF NOT EXISTS ... $$`, porque roles são do cluster e o script precisa rodar de novo num cluster com banco recriado (e no container de teste). Sem `PASSWORD` no script. Privilégios: `GRANT SELECT ON ALL TABLES IN SCHEMA public`; `GRANT INSERT, UPDATE, DELETE` nas seis tabelas listadas; `GRANT USAGE ON ALL SEQUENCES IN SCHEMA public`; `GRANT USAGE ON SCHEMA public`. Sem DDL: no PG16 o `PUBLIC` já não tem `CREATE` no schema `public`, e o script faz `REVOKE CREATE ON SCHEMA public FROM agent_writer` explícito por clareza; um teste prova que `CREATE TABLE` falha. `ALTER DEFAULT PRIVILEGES` **não** é usado: tabela nova exige `GRANT` explícito na migration que a criar (regra registrada no `AGENT_CONTRACT.md`), para que o agente nunca ganhe escrita por acidente.
  *Alternativa:* `GRANT ... ON ALL TABLES` + `REVOKE` nas de referência — descartada: nega por omissão é mais seguro (lista de permitidas).

**4. Testar a role sem senha.** O container usa autenticação por senha para conexões TCP, e a role não tem senha, então o teste não conecta como `agent_writer`: abre conexão com o usuário do container e executa `SET ROLE agent_writer` (e `RESET ROLE` ao fim). `SET ROLE` aplica exatamente os privilégios da role, que é o que se quer provar. Nesse teste o `autoCommit` é controlado para que cada tentativa negada rode na sua própria transação.

**5. "Correção manual vence" (back-end).** `Event` ganha `lockedFields` (mapeado como `text[]`, coluna `locked_fields`, `nullable = false`). Em `EventsService.replace`, para `origin == AGENT`, o conjunto de colunas alteradas é calculado **em memória, antes de gravar**, comparando os valores atuais da entidade com os do comando; os nomes das colunas diferentes entram em `locked_fields` (união sem duplicatas, ordem estável pela ordem das colunas) na mesma chamada de `replaceWith`, de modo que um único `UPDATE` grave os campos, os locks e incremente `version` uma só vez. Colunas consideradas: `city_id`, `category_id`, `venue_id`, `title`, `slug`, `description`, `status`, `age_rating`, `price_min_cents`, `price_max_cents`, `is_free`, `ticket_url`, `image_url`. Slug nulo ou em branco no comando (o banco o regenera do título) não conta como mudança de `slug`; só um slug informado e diferente do atual o trava.
  *Alternativas:* (a) trigger no banco comparando `OLD`/`NEW` — descartada: o banco não distingue edição manual (API) de escrita do agente; (b) comparar após o refresh — descartada: exigiria um segundo `UPDATE` e incrementaria `version` duas vezes.

**6. Sessões.** `occurrence` não é coluna de `event`; mudanças só nas sessões **não** entram em `locked_fields` nesta versão (o contrato fala em colunas). O `AGENT_CONTRACT.md` determina que o agente só reescreve as sessões de um evento quando o `content_hash` mudou e `locked_fields` está vazio; com qualquer lock, o agente não toca nas sessões. Isso evita desfazer uma edição manual de sessões sem criar um nome de lock que não é coluna.

**7. Documento do agente.** `database/AGENT_CONTRACT.md` em português, com: tabelas e colunas que o agente escreve (e as que só lê), o fluxo de uma execução (abrir `ingestion_run`, por evento uma transação com `SELECT ... FOR UPDATE` do vínculo, `INSERT`/`UPDATE ... SET version = version + 1 WHERE id = ? AND version = ?`, atualizar `event_source_link`, fechar o `ingestion_run`), `status = 'PUBLISHED'`, `city_id` só de cidades `active`, não escrever em colunas listadas em `locked_fields`, deduplicação entre fontes por `similarity(title, ...)` do `pg_trgm` (limiar e janela de datas como recomendação), checagem de versão na partida, e a regra de nova tabela exigir `GRANT`.

## Risks / Trade-offs

- [Agente grava em campo travado: o banco não impede] → regra documentada e coberta pelo teste do agente (repo separado); aceito porque trigger não distingue origem da escrita.
- [Renomear/recriar o banco deixa a role órfã de senha] → README documenta o `ALTER ROLE`; V008 é idempotente na criação.
- [Título editado com slug em branco regenera o slug] → só `title` é travado; o slug regenerado não entra em `locked_fields`. Aceito e coberto por teste.
- [Tabela nova sem `GRANT`] → o agente não escreve nela até uma migration conceder; é intencional.
- [`schema_contract` sem proteção contra o agente alterá-lo] → só `SELECT` concedido; coberto por teste.
- [Fixtures: `source`, `event_source_link`, `ingestion_run` entram no banco compartilhado dos testes] → `AbstractPostgresIntegrationTest` passa a limpar `ingestion_run` e `source` de fixture (prefixo `teste-`); `event_source_link` sai por cascade.

## Migration Plan

Aplicar V007 e V008 em ordem (psql, conforme README). Rollback: não há script de reversão (convenção do projeto); em desenvolvimento, `docker compose down -v`. Depois do deploy, definir a senha: `ALTER ROLE agent_writer PASSWORD '...'`. V007/V008 não alteram dados existentes além do default `'{}'`.

## Open Questions

- Limiar de `similarity()` e janela de datas para a deduplicação: o documento traz um valor sugerido; calibrar com dados reais na implementação do agente.
