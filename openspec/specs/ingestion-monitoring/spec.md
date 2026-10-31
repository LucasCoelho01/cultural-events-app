# ingestion-monitoring Specification

## Purpose
A capability `ingestion-monitoring` permite ao admin acompanhar as execuções do agente de ingestão (situação, contadores, erros e eventos criados) e despublicar em lote os eventos que uma execução criou, pela API e pelo front-end.

## Requirements

### Requirement: Listagem de execuções
`GET /api/v1/admin/ingestion-runs` SHALL devolver `items` e `page` (base 0, `size` padrão 20, máximo 100), das execuções mais recentes para as mais antigas (`startedAt` e `id` decrescentes), com `id`, `source` (`code` e `name`), `startedAt`, `finishedAt`, `durationSeconds`, `status`, `created`, `updated`, `unchanged` e `failed`. Aceita os filtros opcionais `source` (code) e `status`.

#### Scenario: Lista sem filtros
- **WHEN** existem execuções de duas fontes e o cliente envia `GET /admin/ingestion-runs`
- **THEN** a API responde HTTP 200 com as execuções da mais recente para a mais antiga, cada uma com os campos acima

#### Scenario: Execução em andamento
- **WHEN** a execução tem `status = RUNNING` e `finishedAt` nulo
- **THEN** o item traz `finishedAt` e `durationSeconds` nulos

#### Scenario: Filtro por fonte e status
- **WHEN** o cliente envia `source=sympla&status=FAILED`
- **THEN** só voltam execuções da fonte `sympla` com status `FAILED`, e `page.totalElements` conta só essas

#### Scenario: Status inválido
- **WHEN** o cliente envia `status=DONE`
- **THEN** a API responde HTTP 400 com `Problem` cujo `detail` cita o parâmetro `status`

#### Scenario: Fonte sem execuções
- **WHEN** o cliente envia `source=inexistente`
- **THEN** a API responde HTTP 200 com `items` vazio e `page.totalElements = 0`

### Requirement: Fontes para o filtro
`GET /api/v1/admin/ingestion-sources` SHALL listar as fontes cadastradas, ordenadas por `name`, com `code` e `name`, para alimentar o filtro da listagem.

#### Scenario: Fontes cadastradas
- **WHEN** existe apenas a fonte `sympla`
- **THEN** a API responde HTTP 200 com uma lista contendo `code = sympla` e `name = Sympla`

#### Scenario: Falha do servidor
- **WHEN** o banco está indisponível
- **THEN** a API responde HTTP 500 com `Problem` (`application/problem+json`)

### Requirement: Detalhe da execução
`GET /api/v1/admin/ingestion-runs/{id}` SHALL devolver os campos da listagem mais `errors` e `createdEvents`. Cada erro traz `code` (nulo se ausente), `externalId` e `detail`. Cada evento criado traz `id` (UUID público), `title` e `status`.

#### Scenario: Execução com erros
- **WHEN** a coluna `errors` contém `{"external_id": "123", "message": "EXTRACTION_FAILED: sem título"}`
- **THEN** o detalhe traz o erro com `code = EXTRACTION_FAILED`, `externalId = 123` e `detail = sem título`

#### Scenario: Mensagem sem código
- **WHEN** a mensagem do erro é `erro inesperado na execução`, sem prefixo de código
- **THEN** o erro traz `code` nulo e `detail = erro inesperado na execução`

#### Scenario: Execução inexistente
- **WHEN** o `id` não existe
- **THEN** a API responde HTTP 404 com `Problem`

#### Scenario: Id inválido
- **WHEN** o `id` não é um número inteiro
- **THEN** a API responde HTTP 400 com `Problem`

### Requirement: Eventos criados na execução
Os `createdEvents` SHALL ser os eventos com `origin = AGENT` ligados, por `event_source_link` não lápide, à fonte da execução, com `created_at` do vínculo entre `started_at` e `finished_at` (ou o instante atual se não terminou) e vínculo que seja o mais antigo do evento. Não usam `last_run_id`.

#### Scenario: Evento criado na execução
- **WHEN** a execução criou o evento A (vínculo criado dentro da janela) e atualizou o evento B (vínculo criado antes)
- **THEN** `createdEvents` contém A e não contém B, mesmo que o `last_run_id` de B seja esta execução

#### Scenario: Vínculo por deduplicação
- **WHEN** a execução apenas vinculou a fonte a um evento criado por outra fonte ou por um admin
- **THEN** esse evento não aparece em `createdEvents`

#### Scenario: Evento excluído
- **WHEN** o admin excluiu um evento da execução e o vínculo virou lápide (`event_id` nulo)
- **THEN** o evento não aparece e nada falha

### Requirement: Despublicação em lote
`POST /api/v1/admin/ingestion-runs/{id}/unpublish` SHALL mudar para `DRAFT` os `createdEvents` da execução com status `PUBLISHED`, aplicando a transição de status e o controle de `version` da edição individual de evento, e devolver `{"unpublished": n}`. Nenhum evento é excluído.

#### Scenario: Despublicar
- **WHEN** a execução criou 3 eventos, dois `PUBLISHED` e um `DRAFT`, e o cliente chama o endpoint
- **THEN** a API responde HTTP 200 com `unpublished = 2`, os dois passam a `DRAFT` com `version` incrementada e o terceiro fica intacto

#### Scenario: Segunda chamada
- **WHEN** o endpoint é chamado de novo para a mesma execução
- **THEN** a API responde HTTP 200 com `unpublished = 0`

#### Scenario: Evento sem exclusão
- **WHEN** o lote termina
- **THEN** todos os eventos e vínculos continuam no banco, sem lápides novas

#### Scenario: Execução inexistente
- **WHEN** o `id` não existe
- **THEN** a API responde HTTP 404 com `Problem` e nada é alterado

### Requirement: Despublicação recusada ou conflitante
A despublicação em lote MUST ser recusada com HTTP 409 se a execução está `RUNNING`, e MUST ser atômica: se algum evento mudou durante o lote (conflito de `version`), a API responde HTTP 409 e nenhum evento é alterado.

#### Scenario: Execução em andamento
- **WHEN** a execução tem `status = RUNNING`
- **THEN** a API responde HTTP 409 com `Problem` e nenhum evento muda de status

#### Scenario: Evento alterado durante o lote
- **WHEN** outro usuário altera um dos eventos entre a leitura e a gravação do lote
- **THEN** a API responde HTTP 409 com `Problem` e todos os eventos permanecem como estavam

### Requirement: Página de execuções no admin
O front-end SHALL exibir em `/admin/execucoes` a página "Execuções do agente": tabela paginada por `?page=` (base 1) com fonte, início, duração, status em badge colorido e os contadores criados, atualizados, inalterados e com falha; filtros por fonte e status; estados de carregando, vazio e erro (`ErrorMessage`).

#### Scenario: Tabela de execuções
- **WHEN** a API responde HTTP 200 com execuções `SUCCESS`, `PARTIAL`, `FAILED` e `RUNNING`
- **THEN** cada linha mostra o status em badge de cor própria, os quatro contadores e a duração (ou "Em andamento" para `RUNNING`)

#### Scenario: Filtro
- **WHEN** o usuário escolhe a fonte "Sympla" e o status "Falhou"
- **THEN** a requisição envia `source=sympla&status=FAILED`, volta para a página 1 e a URL guarda os filtros

#### Scenario: Nenhuma execução
- **WHEN** a API responde HTTP 200 com `items` vazio
- **THEN** a página mostra a mensagem de estado vazio

#### Scenario: Erro da API
- **WHEN** a API responde HTTP 500 com `Problem` contendo `detail`
- **THEN** a página exibe `ErrorMessage` com o `detail`, sem tabela

### Requirement: Página de detalhe da execução
O front-end SHALL exibir em `/admin/execucoes/:id` o resumo da execução, a lista de erros (código, `externalId` e detalhe), a lista de eventos criados (título, status e link para `/admin/eventos/:id/editar`) e o botão "Despublicar eventos desta execução".

#### Scenario: Detalhe completo
- **WHEN** a API responde HTTP 200 com 2 erros e 3 eventos criados
- **THEN** a página mostra o resumo, as duas linhas de erro e os três eventos, cada um com link de edição

#### Scenario: Sem erros e sem eventos
- **WHEN** `errors` e `createdEvents` vêm vazios
- **THEN** a página mostra "Nenhum erro registrado" e "Nenhum evento criado nesta execução"

#### Scenario: Execução não encontrada
- **WHEN** a API responde HTTP 404 com `Problem`
- **THEN** a página exibe `ErrorMessage` com o `detail`

### Requirement: Confirmação da despublicação em lote
Ao acionar "Despublicar eventos desta execução", o front-end SHALL pedir confirmação na própria página (sem `window.confirm`) mostrando quantos eventos `PUBLISHED` serão despublicados, e SHALL enviar o `POST` somente após a confirmação. O botão fica desabilitado se a execução está `RUNNING` ou não há evento publicado.

#### Scenario: Confirmar
- **WHEN** há 2 eventos publicados, o usuário aciona o botão e confirma, e a API responde HTTP 200 com `unpublished = 2`
- **THEN** a confirmação mostrou "2 eventos" antes do envio e, depois, a página recarrega com os eventos em `DRAFT`

#### Scenario: Cancelar
- **WHEN** o usuário aciona o botão e depois "Cancelar"
- **THEN** nenhuma requisição `POST` é enviada

#### Scenario: Conflito
- **WHEN** a confirmação é enviada e a API responde HTTP 409 com `Problem`
- **THEN** a página exibe o `detail` do `Problem` e recarrega os dados

#### Scenario: Execução em andamento
- **WHEN** a execução está `RUNNING`
- **THEN** o botão aparece desabilitado e nenhuma requisição é enviada
