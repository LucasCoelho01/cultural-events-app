# Proposal

## Why

O produto nasce com São Paulo, mas nenhuma regra pode presumir uma única cidade. Antes de qualquer capability de eventos, locais ou busca, o sistema precisa de uma base de localidades (estados e cidades) à qual esses dados serão ancorados. Fazer isso agora evita retrabalho de schema quando as próximas capabilities passarem a referenciar `city`.

## What Changes

- Nova migration `database/migrations/V002__create_locations.sql` criando as tabelas `state` e `city` (com `UNIQUE (state_uf, slug)`, `UNIQUE (ibge_code)` e FK `city.state_uf -> state.uf`) e a carga inicial: as 27 UFs e uma cidade ativa (código IBGE 3550308, slug `sao-paulo`, fuso `America/Sao_Paulo`).
- Novos endpoints somente leitura, declarados primeiro em `api/openapi.yaml` (tag `locations`): `GET /states`, `GET /cities`, `GET /cities/{uf}/{slug}` e `GET /cities/default`.
- Novo DTO `City` (`id`, `ibgeCode`, `uf`, `name`, `slug`, `timezone`, `latitude`, `longitude`, `active`), `State` e a página `CityPage` (reusa `PageMetadata`); erros reusam `Problem`.
- A cidade padrão é resolvida a partir da propriedade `app.default-city` (já existente, formato `uf/slug`); configuração apontando para cidade inexistente ou inativa resulta em 500 com `Problem`.
- Novo pacote `com.culturalevents.locations.{api,application,domain,infrastructure}` com entidades JPA compatíveis com o script (`ddl-auto=validate`).
- Nenhum código de `src/main` cita "São Paulo" ou o slug; a cidade existe apenas como dado (seed SQL) e como valor padrão de configuração.
- Cadastro de novas cidades/estados é feito por script SQL; a API não oferece escrita.
- Testes de integração com Testcontainers para cada endpoint, incluindo cenários de erro.

## Capabilities

### New Capabilities
- `locations`: catálogo somente leitura de estados e cidades (schema, carga inicial, consulta por API e resolução da cidade padrão), base do comportamento multicidade.

### Modified Capabilities
- `platform-foundation`: o requisito "Contrato HTTP compartilhado — Problem e PageMetadata" afirma que nenhum path de negócio é declarado "nesta fase"; passa a permitir paths de negócio (a partir de `locations`), mantendo `Problem` e `PageMetadata` como componentes compartilhados.

## Impact

- **Capabilities afetadas:** `locations` (nova), `platform-foundation` (delta no requisito de contrato HTTP).
- **Banco:** nova migration V002; sem alteração de tabelas existentes. Docker Compose aplica o script automaticamente na primeira inicialização.
- **Contrato com o agente de ingestão (fase 3):** `state` e `city` passam a fazer parte do schema que o agente lê; eventos e locais futuros referenciarão `city.id`. O agente (`agent_writer`, só DML) NÃO deve escrever nessas tabelas — cidades são cadastradas via script SQL revisado. Slugs e `ibge_code` são estáveis e podem ser usados pelo agente como chave natural de lookup.
- **API:** `api/openapi.yaml` ganha 4 paths e 4 schemas novos; sem breaking changes.
- **Código:** novo pacote `locations` no backend; possível ajuste no handler de erros compartilhado para devolver 400 em parâmetro de query inválido e 500 com `Problem` específico para configuração inválida da cidade padrão.
- **Dependências:** nenhuma nova.
