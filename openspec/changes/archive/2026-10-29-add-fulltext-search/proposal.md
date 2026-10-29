# Proposal

## Why

A lista pública só filtra por categoria e período; quem procura "música" ou um nome de artista não tem como buscar. O agente de ingestão (fase 3) grava direto no Postgres, então a busca precisa funcionar sem passar pela aplicação, e deve estar pronta antes de o catálogo crescer.

## What Changes

- **Banco (V006)**: coluna `event.search_vector` (`tsvector`) mantida por trigger, com pesos título A, nome da categoria e do local B, descrição C; dicionário `portuguese` com `unaccent` ("musica" encontra "Música"); índice GIN; preenchimento dos eventos existentes. Um trigger extra em `venue` atualiza o vetor dos eventos quando o local é renomeado. Renomear categoria não atualiza o vetor (limitação aceita, ver design).
- **Contrato**: `GET /events` ganha o parâmetro `q` (2 a 100 caracteres após trim; fora disso HTTP 400). Com `q`, usa `websearch_to_tsquery` e ordena por relevância (`ts_rank`), depois pela próxima sessão. Sem `q`, nada muda. `api/openapi.yaml` é atualizado antes do código.
- **Back-end**: o caminho com `q` usa query nativa confinada ao adaptador de infraestrutura de `events`; o caminho sem `q` continua em JPQL. Sem mapear `tsvector` na entidade, então `ddl-auto=validate` e ArchUnit seguem como estão.
- **Front-end**: campo de busca acima dos filtros da lista, refletido em `?q=` na URL; aplica ao enviar (Enter ou botão), volta à página 1; "Limpar filtros" também limpa a busca; estado vazio `Nenhum evento para "<termo>"`.
- **Postman**: requisição com `q` (e caso de erro HTTP 400) na coleção.

## Capabilities

### New Capabilities
<!-- nenhuma -->

### Modified Capabilities
- `events`: novos requisitos para o schema de busca (coluna, trigger, índice, backfill), manutenção do vetor (inclusive a limitação de categoria), parâmetro `q` e resultado ordenado por relevância na listagem. A listagem existente não muda sem `q`.
- `public-catalog`: campo de busca e `?q=` na URL; "Lista pública de eventos da cidade", "Parâmetros de filtro na URL", "Limpar filtros" e "Estado vazio com filtro ativo" passam a considerar a busca.

## Impact

- **Contrato de schema com o agente**: adiciona `event.search_vector`, que o agente **não** escreve (o trigger calcula e sobrescreve qualquer valor enviado). Nada muda no que o agente já insere. A role `agent_writer` continua só com DML. Quem renomear uma categoria por script deve refrescar os vetores (ver design).
- **SQL**: novo `database/migrations/V006__add_event_search.sql` (não edita V001-V005; usa `unaccent` da V001).
- **API**: `api/openapi.yaml` (parâmetro `q` em `listEvents`); código gerado atualizado.
- **Back-end**: `EventFilter`, `EventsService.list`, `EventsController.listEvents`, `EventRepositoryAdapter`; testes de integração com Testcontainers.
- **Front-end**: `api/events.ts`, `components/filterParams.ts`, `CatalogFilters`/`EventList`; testes com MSW (`zz/cidade-teste`).
- **Postman**: `postman/cultural-events.postman_collection.json`.
