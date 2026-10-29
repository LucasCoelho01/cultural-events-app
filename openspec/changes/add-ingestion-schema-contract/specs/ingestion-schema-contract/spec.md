# Spec Delta

## Purpose

A capability `ingestion-schema-contract` define o schema de banco como contrato versionado com o agente de ingestão externo: fontes, vínculos evento-fonte, execuções, versão do contrato, campos travados por correção manual e a role de escrita com privilégios mínimos.

## ADDED Requirements

### Requirement: Tabela de fontes
O script `V007__add_ingestion_schema.sql` SHALL criar `source` com `code` único e obrigatório, `name`, `base_url`, `city_id` opcional (nulo = fonte nacional), `active` (padrão `true`), `created_at` e `updated_at` (atualizado por trigger), e SHALL cadastrar a fonte `sympla` (`https://www.sympla.com.br`, `city_id` nulo).

#### Scenario: Fonte sympla cadastrada
- **WHEN** os scripts são aplicados a um banco limpo
- **THEN** existe uma linha em `source` com `code = 'sympla'`, `base_url = 'https://www.sympla.com.br'`, `city_id` nulo e `active = true`

#### Scenario: Code duplicado
- **WHEN** um `INSERT` em `source` repete um `code` existente
- **THEN** o banco rejeita com SQLSTATE `23505` na constraint `source_code_key`

#### Scenario: Cidade inexistente
- **WHEN** um `INSERT` em `source` informa `city_id` que não existe em `city`
- **THEN** o banco rejeita com SQLSTATE `23503`

### Requirement: Vínculo evento-fonte
O script SHALL criar `event_source_link` com `event_id` (`ON DELETE CASCADE`), `source_id`, `external_id`, `external_url`, `content_hash`, `last_run_id` (opcional), `last_seen_at` e `UNIQUE (source_id, external_id)`, para que o agente reconheça um evento já importado.

#### Scenario: Mesmo evento externo vinculado duas vezes
- **WHEN** um `INSERT` repete o par `(source_id, external_id)` para outro evento
- **THEN** o banco rejeita com SQLSTATE `23505` na constraint `event_source_link_source_external_key`

#### Scenario: Evento removido
- **WHEN** um evento com vínculo é removido de `event`
- **THEN** seus vínculos em `event_source_link` são removidos junto

#### Scenario: Vínculo sem evento
- **WHEN** um `INSERT` em `event_source_link` informa `event_id` inexistente
- **THEN** o banco rejeita com SQLSTATE `23503`

### Requirement: Registro de execuções
O script SHALL criar `ingestion_run` com `source_id`, `started_at` (padrão agora), `finished_at` opcional, `status` em `RUNNING`, `SUCCESS`, `PARTIAL` ou `FAILED` (padrão `RUNNING`), contadores `created`, `updated`, `unchanged` e `failed` (inteiros não negativos, padrão 0) e `errors` JSONB (padrão `[]`).

#### Scenario: Execução com padrões
- **WHEN** um `INSERT` em `ingestion_run` informa só `source_id`
- **THEN** a linha tem `status = 'RUNNING'`, os quatro contadores em 0 e `errors = []`

#### Scenario: Status inválido
- **WHEN** um `INSERT` ou `UPDATE` define `status = 'DONE'`
- **THEN** o banco rejeita com SQLSTATE `23514` na constraint `ingestion_run_status_valid`

#### Scenario: Contador negativo
- **WHEN** um `UPDATE` define `failed = -1`
- **THEN** o banco rejeita com SQLSTATE `23514` na constraint `ingestion_run_counters_nonneg`

### Requirement: Versão do contrato
O script SHALL criar `schema_contract` com `version` e `applied_at` e inserir a versão `'1.0'`. A versão MUST ter o formato `MAJOR.MINOR`; o agente SHALL ler a maior versão registrada na partida.

#### Scenario: Versão inicial
- **WHEN** os scripts são aplicados a um banco limpo
- **THEN** `schema_contract` contém exatamente a linha `version = '1.0'` com `applied_at` preenchido

#### Scenario: Formato inválido
- **WHEN** um `INSERT` em `schema_contract` usa `version = 'abc'`
- **THEN** o banco rejeita com SQLSTATE `23514` na constraint `schema_contract_version_format`

#### Scenario: Versão repetida
- **WHEN** um `INSERT` repete `version = '1.0'`
- **THEN** o banco rejeita com SQLSTATE `23505`

### Requirement: Campos travados no evento
O script SHALL adicionar `event.locked_fields TEXT[] NOT NULL DEFAULT '{}'`, listando as colunas de `event` que uma correção manual alterou e que o agente não pode sobrescrever. Eventos existentes e novos começam com `'{}'`.

#### Scenario: Evento novo sem locks
- **WHEN** um evento é inserido sem informar `locked_fields`
- **THEN** `locked_fields` é `'{}'`

#### Scenario: Valor nulo
- **WHEN** um `INSERT` ou `UPDATE` define `locked_fields = NULL`
- **THEN** o banco rejeita com SQLSTATE `23502`

### Requirement: Role agent_writer
O script `V008__create_agent_writer_role.sql` SHALL criar a role `agent_writer` com `LOGIN` e sem senha no script, com `SELECT` em todas as tabelas, `INSERT`, `UPDATE` e `DELETE` somente em `event`, `occurrence`, `venue`, `source`, `event_source_link` e `ingestion_run`, `USAGE` nas sequences e nenhum DDL.

#### Scenario: Fluxo completo de upsert permitido
- **WHEN** `agent_writer` insere um `venue`, um `event` com `origin = 'AGENT'`, suas `occurrence`, o `event_source_link` e atualiza o `ingestion_run`
- **THEN** todas as operações têm sucesso

#### Scenario: Escrita em tabela de referência negada
- **WHEN** `agent_writer` executa `INSERT`, `UPDATE` ou `DELETE` em `category`, `city`, `state` ou `schema_contract`
- **THEN** o banco rejeita com SQLSTATE `42501` (permissão negada)

#### Scenario: DDL negado
- **WHEN** `agent_writer` executa `CREATE TABLE`, `ALTER TABLE event` ou `DROP TABLE event`
- **THEN** o banco rejeita com SQLSTATE `42501`

#### Scenario: Script repetido
- **WHEN** o script V008 é aplicado num cluster onde a role já existe
- **THEN** o script não falha e as permissões permanecem as descritas

### Requirement: Correção manual vence
Ao substituir um evento com `origin = 'AGENT'` por `PUT /api/v1/events/{id}`, o back-end SHALL acrescentar em `locked_fields`, sem duplicar, os nomes das colunas de `event` cujo valor mudou. Eventos `MANUAL` MUST manter `locked_fields` vazio.

#### Scenario: Edição de título e preço
- **WHEN** o cliente envia `PUT` em evento `AGENT` alterando `title` e `priceMinCents` e a API responde HTTP 200
- **THEN** `locked_fields` contém `title` e `price_min_cents` e nenhuma outra coluna

#### Scenario: PUT sem mudanças
- **WHEN** o cliente envia `PUT` em evento `AGENT` com os mesmos valores que ele já tem
- **THEN** `locked_fields` permanece como estava

#### Scenario: Campo já travado
- **WHEN** o cliente altera de novo `title`, já presente em `locked_fields`
- **THEN** `title` aparece uma única vez em `locked_fields`

#### Scenario: Evento manual
- **WHEN** o cliente altera um evento com `origin = 'MANUAL'`
- **THEN** `locked_fields` continua `'{}'`

#### Scenario: PUT rejeitado
- **WHEN** o `PUT` em evento `AGENT` falha com HTTP 409 (`version` desatualizada) ou 422 (campo inválido)
- **THEN** `locked_fields` não é alterado

### Requirement: Contrato OpenAPI inalterado
A API SHALL manter `api/openapi.yaml` sem mudanças: `locked_fields` MUST NOT aparecer em nenhuma resposta nem ser aceito em nenhum corpo.

#### Scenario: Resposta sem locked_fields
- **WHEN** o cliente consulta `GET /api/v1/events/{id}` de um evento com `locked_fields` preenchido
- **THEN** a resposta HTTP 200 não contém `lockedFields` nem `locked_fields`

#### Scenario: Campo enviado no corpo
- **WHEN** o cliente envia `lockedFields` no corpo de `PUT` ou `POST`
- **THEN** o valor é ignorado e `locked_fields` só muda pela regra de correção manual

### Requirement: Documento de contrato do agente
O arquivo `database/AGENT_CONTRACT.md` SHALL descrever o que o agente escreve e as regras: idempotência por `(source_id, external_id)` e `content_hash`, uma transação por evento, `status = 'PUBLISHED'`, só cidades ativas, `UPDATE` com `version = version + 1` e `WHERE version`, respeito a `locked_fields`, deduplicação entre fontes com `similarity()` do `pg_trgm`, checagem de `schema_contract.version` na partida e registro em `ingestion_run`.

#### Scenario: Documento cobre todas as regras
- **WHEN** o arquivo é revisado
- **THEN** cada regra listada tem uma seção própria e o documento indica a versão do contrato (`1.0`) a que se refere

#### Scenario: Parada por versão principal diferente
- **WHEN** o agente lê em `schema_contract` uma versão principal diferente da que suporta (por exemplo `2.0` para um agente `1.x`)
- **THEN** o documento determina que o agente não grava nada e encerra com erro

### Requirement: Documentação da senha da role
O `README.md` SHALL documentar que a senha de `agent_writer` é definida manualmente com `ALTER ROLE agent_writer PASSWORD '...'`, fora dos scripts versionados.

#### Scenario: Senha ausente nos scripts
- **WHEN** os arquivos de `database/migrations` são inspecionados
- **THEN** nenhum contém `PASSWORD`, e a role sem senha não consegue autenticar por senha até o `ALTER ROLE`
