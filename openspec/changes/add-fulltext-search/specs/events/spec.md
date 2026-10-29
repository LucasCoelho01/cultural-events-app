## ADDED Requirements

### Requirement: Vetor de busca do evento no schema
O script `V006` SHALL adicionar `event.search_vector` (`tsvector`) com índice GIN e preencher o vetor dos eventos já existentes. O vetor não aceita valor de escritor: toda gravação em `event` o recalcula.

#### Scenario: Evento existente ganha vetor
- **WHEN** a V006 é aplicada sobre um banco com eventos criados pela V005
- **THEN** todos os eventos têm `search_vector` preenchido e o índice GIN existe

#### Scenario: Valor enviado para o vetor é descartado
- **WHEN** um escritor insere um evento com `search_vector` explícito e inconsistente com o título
- **THEN** a linha é gravada com o vetor calculado a partir do título, descrição, categoria e local, sem erro

#### Scenario: Contexto falha sem a V006
- **WHEN** a busca é executada contra um Postgres sem a coluna `search_vector`
- **THEN** a consulta com `q` falha com erro de banco e a API responde HTTP 500 com `Problem` (`status=500`) sem stack trace

### Requirement: Composição e pesos do vetor de busca
O vetor SHALL ser mantido por trigger em `INSERT` e em `UPDATE` de título, descrição, categoria ou local, usando o dicionário `portuguese` sobre texto sem acentos: título peso A, nome da categoria e do local peso B, descrição peso C.

#### Scenario: Busca sem acento encontra texto acentuado
- **WHEN** existe o evento "Festival de Música" e o vetor é consultado por `musica`
- **THEN** o evento casa, e o termo `música` também casa

#### Scenario: Singular e plural casam
- **WHEN** existe um evento com "shows" no título e a consulta é `show`, e vice-versa
- **THEN** o evento casa nos dois casos

#### Scenario: Evento sem descrição nem local
- **WHEN** um evento é gravado com `description` e `venue_id` nulos
- **THEN** o vetor é calculado só com título e categoria, sem erro

#### Scenario: Inserção direta pelo agente
- **WHEN** o agente insere um evento por SQL direto, sem passar pela API
- **THEN** o evento tem `search_vector` preenchido e aparece na busca

### Requirement: Renomear local atualiza a busca; categoria não
Renomear um local SHALL atualizar o `search_vector` dos eventos dele. Renomear uma categoria NÃO atualiza o vetor dos eventos, limitação aceita porque categorias só mudam por script numerado; o script que renomear deve refrescar os vetores.

#### Scenario: Local renomeado
- **WHEN** o nome de um local com eventos muda por `PUT /venues/{id}` ou por SQL direto
- **THEN** a busca pelo novo nome encontra os eventos do local e a busca pelo nome antigo deixa de encontrá-los

#### Scenario: Categoria renomeada por script
- **WHEN** o nome de uma categoria é alterado por `UPDATE` sem refrescar os eventos
- **THEN** o vetor dos eventos continua com o nome antigo até que `title` seja regravado

#### Scenario: Renomear local sem eventos
- **WHEN** o nome de um local sem eventos é alterado
- **THEN** a operação conclui sem erro e nenhum evento é alterado

### Requirement: Parâmetro de busca na listagem
`GET /api/v1/events` SHALL aceitar `q`, texto de 2 a 100 caracteres após remover espaços nas pontas. Sem `q` a listagem não muda. Com `q` fora do limite, ou só com espaços, a API SHALL responder HTTP 400 com `Problem`.

#### Scenario: q ausente
- **WHEN** o cliente envia `GET /api/v1/events` sem `q`
- **THEN** a API responde HTTP 200 com o mesmo resultado e ordem de antes da busca

#### Scenario: q curto
- **WHEN** o cliente envia `q=a`, `q=%20a%20` ou `q=` ou `q=%20%20%20`
- **THEN** a API responde HTTP 400 com `Problem` (`status=400`)

#### Scenario: q longo
- **WHEN** o cliente envia `q` com 101 caracteres
- **THEN** a API responde HTTP 400 com `Problem` (`status=400`)

#### Scenario: q nos limites
- **WHEN** o cliente envia `q` com 2 caracteres, com 100 caracteres, ou `q=%20%20show%20%20`
- **THEN** a API responde HTTP 200 e a busca usa o texto sem os espaços das pontas

### Requirement: Resultado da busca textual
Com `q`, a listagem SHALL devolver só eventos cujo vetor casa com `websearch_to_tsquery`, ordenados por relevância decrescente, depois pela próxima sessão, depois por `id`, combinando `q` com `city`, `category`, `from`, `to` e `status` e paginando como sem `q`.

#### Scenario: Título pesa mais que descrição
- **WHEN** dois eventos casam com `q`, um no título e outro só na descrição
- **THEN** o do título vem primeiro, independentemente da data das sessões

#### Scenario: Empate de relevância
- **WHEN** dois eventos têm a mesma relevância e sessões em datas diferentes
- **THEN** o de sessão mais próxima vem primeiro

#### Scenario: q combinado com filtros
- **WHEN** o cliente envia `q` com `category`, `from`, `to` e `status`
- **THEN** a API responde HTTP 200 somente com eventos que casam com `q` e atendem todos os filtros, e `page.totalElements` reflete essa interseção

#### Scenario: Sem resultado
- **WHEN** nenhum evento casa com `q`, ou `q` só tem palavras ignoradas pelo dicionário (ex.: `de`)
- **THEN** a API responde HTTP 200 com `items` vazio e `totalElements=0`

#### Scenario: Sintaxe de busca especial
- **WHEN** `q` contém aspas abertas, `-` ou `OR` (ex.: `"show -rock`)
- **THEN** a API responde HTTP 200 e nunca HTTP 500 por erro de sintaxe da consulta

#### Scenario: Categoria inexistente com q
- **WHEN** o cliente envia `q=show&category=nao-existe`
- **THEN** a API responde HTTP 404 com `Problem` (`status=404`)

### Requirement: Busca compatível com a validação de schema
O módulo `events` SHALL executar a busca textual sem mapear `search_vector` na entidade, de modo que o contexto suba com `ddl-auto=validate` contra o schema V006 e a consulta nativa fique confinada à infraestrutura do módulo.

#### Scenario: Contexto sobe com o schema V006
- **WHEN** o contexto Spring é iniciado contra um Postgres com as migrations V001 a V006
- **THEN** a validação de schema passa e a aplicação inicia

#### Scenario: Consulta nativa fora da infraestrutura
- **WHEN** a suíte de arquitetura analisa as classes de `com.culturalevents.events`
- **THEN** a regra de camadas continua passando, e falha se `api` ou `domain` passarem a depender de `EntityManager` ou do adaptador
