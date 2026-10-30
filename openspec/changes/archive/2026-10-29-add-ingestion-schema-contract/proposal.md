# Proposal

## Why

A fase 3 traz um agente de ingestão (Python, repo separado) que grava direto no Postgres e publica eventos sem revisão humana. Hoje o schema não tem como identificar de qual fonte veio um evento, evitar duplicatas entre execuções, registrar execuções, impedir que o agente desfaça correções manuais, nem limitar o que o agente pode escrever. O banco precisa virar o contrato explícito e versionado com o agente, antes de ele existir.

## What Changes

- **V007** (`add_ingestion_schema`): tabelas `source`, `event_source_link`, `ingestion_run` e `schema_contract` (com a versão `'1.0'`); coluna `event.locked_fields TEXT[] NOT NULL DEFAULT '{}'`; seed da fonte `sympla` (nacional). Constraints nomeadas e triggers `updated_at` como nas tabelas existentes.
- **V008** (`create_agent_writer_role`): role `agent_writer` (`LOGIN`, sem senha no script) com `SELECT` em tudo, `INSERT/UPDATE/DELETE` só em `event`, `occurrence`, `venue`, `source`, `event_source_link` e `ingestion_run`, `USAGE` nas sequences e nenhum DDL.
- **"Correção manual vence"**: `PUT /api/v1/events/{id}` em evento com `origin = AGENT` acrescenta em `locked_fields` os nomes das colunas de `event` que mudaram. Sem mudança no contrato OpenAPI.
- **`database/AGENT_CONTRACT.md`**: o que o agente escreve e as regras de escrita (idempotência, transação por evento, publicação, locks, deduplicação, checagem de versão do contrato, registro de execução).
- **README**: seção sobre a role `agent_writer` com o `ALTER ROLE ... PASSWORD` manual.
- **Testes** com Testcontainers cobrindo schema, permissões da role, `locked_fields` no PUT e a subida do JPA com `ddl-auto=validate`.

## Capabilities

### New Capabilities
- `ingestion-schema-contract`: schema de ingestão (fontes, vínculos, execuções, versão do contrato, `locked_fields`), role de escrita do agente, regra "correção manual vence" e documento de contrato do agente.

### Modified Capabilities
<!-- Nenhuma: o comportamento do PUT em `events` para origin AGENT é coberto como requisito da nova capability; nenhum requisito existente muda para eventos MANUAL. -->

## Impact

- **Contrato de schema com o agente (principal impacto):** novas tabelas e coluna descritas acima; `schema_contract.version = '1.0'` passa a ser a referência para o agente parar se a versão principal mudar; `agent_writer` define o limite de escrita (não escreve em `category`, `city`, `state`, `schema_contract` nem faz DDL).
- **Banco:** `database/migrations/V007__add_ingestion_schema.sql`, `V008__create_agent_writer_role.sql`; `database/AGENT_CONTRACT.md` (novo).
- **Back-end:** entidade `Event` ganha `lockedFields`; `EventsService.replace` passa a atualizar `locked_fields` para eventos `AGENT`. Hibernate segue em `ddl-auto=validate`.
- **API:** `api/openapi.yaml` inalterado; `locked_fields` não é exposto.
- **Docs:** `README.md`.
- **Testes:** novos testes de schema/permissões/`locked_fields`; `AbstractPostgresIntegrationTest` precisa limpar as novas tabelas de fixture e o teste de colunas de `event` em `EventsSchemaIntegrationTest` passa a incluir `locked_fields`.
