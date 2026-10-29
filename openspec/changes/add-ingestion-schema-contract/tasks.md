# Tasks

## 1. V007 — schema de ingestão

- [ ] 1.1 Criar `database/migrations/V007__add_ingestion_schema.sql` com `source`, `event_source_link`, `ingestion_run`, `schema_contract` (versão `'1.0'`), `event.locked_fields`, seed `sympla`, constraints nomeadas, índices de FK e triggers `set_updated_at`; verificar aplicando V001–V007 em Postgres limpo sem erro
- [ ] 1.2 Atualizar `AbstractPostgresIntegrationTest.removeFixtures()` para apagar `ingestion_run` e `source` de fixture (prefixo `teste-`) e adicionar helpers de fixture de fonte/execução; verificar que a suíte existente continua verde
- [ ] 1.3 Atualizar `EventsSchemaIntegrationTest.tabelasEColunasCriadas` para incluir `locked_fields`; verificar o teste passando
- [ ] 1.4 Criar `IngestionSchemaIntegrationTest` (Testcontainers) cobrindo: seed `sympla`, `source_code_key`, FK de `city_id`, `UNIQUE (source_id, external_id)`, cascade ao apagar evento, status e contadores inválidos de `ingestion_run`, formato e unicidade de `schema_contract.version`, `locked_fields` padrão `'{}'` e `NOT NULL`, `updated_at` por trigger; verificar o teste passando

## 2. V008 — role agent_writer

- [ ] 2.1 Criar `database/migrations/V008__create_agent_writer_role.sql` (criação idempotente via `DO`, sem senha, `GRANT`s mínimos, sem DDL, sem `ALTER DEFAULT PRIVILEGES`); verificar aplicando o script duas vezes sem erro
- [ ] 2.2 Criar `AgentWriterRoleIntegrationTest` (Testcontainers, conexão do container com `SET ROLE agent_writer`): negar `INSERT/UPDATE/DELETE` em `category`, `city`, `state` e `schema_contract` e DDL (`CREATE/ALTER/DROP TABLE`) com SQLSTATE `42501`; permitir o fluxo completo (venue, event `AGENT` `PUBLISHED`, sessões, `event_source_link`, `ingestion_run`, `UPDATE ... version = version + 1 WHERE version`); verificar o teste passando
- [ ] 2.3 Adicionar ao teste um caso que prova que nenhum script em `database/migrations` contém `PASSWORD` e que a role não possui senha (`pg_authid.rolpassword` nulo); verificar o teste passando
- [ ] 2.4 Documentar no `README.md` a role `agent_writer`, o `ALTER ROLE agent_writer PASSWORD '...'` manual e como aplicar V007/V008 num banco local existente (comandos PowerShell no padrão das seções anteriores); verificar que os comandos documentados rodam como escritos

## 3. Correção manual vence (back-end)

- [ ] 3.1 Mapear `locked_fields` (`text[]`, `NOT NULL`) na entidade `Event` com acesso somente interno ao domínio; verificar que a aplicação sobe com `ddl-auto=validate`
- [ ] 3.2 Implementar em `Event.replaceWith`/`EventsService.replace` o cálculo das colunas alteradas para `origin = AGENT` e a união sem duplicatas em `locked_fields`, ignorando slug em branco, sem alterar `api/openapi.yaml`; verificar com testes unitários em `EventsServiceTest` (mudança de título e preço, PUT sem mudança, campo já travado, evento `MANUAL`, slug em branco)
- [ ] 3.3 Criar teste de integração `EventsLockedFieldsApiIntegrationTest` (Testcontainers) com `PUT` real: evento `AGENT` preenche `locked_fields`; sem mudanças não altera; 409 por `version` desatualizada e 422 não alteram; evento `MANUAL` continua `'{}'`; `GET` não expõe `lockedFields`; `version` incrementa uma única vez; verificar o teste passando
- [ ] 3.4 Criar teste que confirma `JPA` com `ddl-auto=validate` subindo com V001–V008 (contexto Spring carrega) e que `api/openapi.yaml` não foi modificado (`git diff --exit-code api/openapi.yaml`); verificar ambos

## 4. Documento de contrato do agente

- [ ] 4.1 Escrever `database/AGENT_CONTRACT.md` (versão do contrato `1.0`) cobrindo: tabelas escritas vs. só lidas; idempotência por `(source_id, external_id)` e `content_hash`; uma transação por evento; `status = 'PUBLISHED'`; só cidades ativas; `UPDATE` com `version = version + 1` e `WHERE version`; respeito a `locked_fields` (incluindo sessões); deduplicação com `similarity()` do `pg_trgm`; checagem de `schema_contract` na partida com parada se o MAJOR mudar; registro em `ingestion_run`; regra de `GRANT` para tabela nova; verificar que cada regra do requisito "Documento de contrato do agente" tem seção própria
- [ ] 4.2 Validar o SQL de exemplo do documento (upsert do evento, vínculo e `similarity()`) executando-o contra o container de teste dentro de `AgentWriterRoleIntegrationTest` ou de um teste dedicado; verificar o teste passando

## 5. Verificação final

- [ ] 5.1 Rodar a suíte completa do back-end (`.\mvnw.cmd test` em `backend/`) e `openspec validate add-ingestion-schema-contract --strict`; verificar tudo verde
- [ ] 5.2 Atualizar a coleção em `postman/` apenas se algum contrato HTTP mudou (esperado: nenhuma mudança); verificar `git diff -- api postman` vazio
