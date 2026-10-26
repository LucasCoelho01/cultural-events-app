# venues Specification

## Purpose
A capability `venues` mantém o cadastro de locais (casas de show, teatros, museus) de cada cidade e o expõe por API CRUD. Slug e `updated_at` são garantidos pelo banco, para valer também para escritores diretos como o agente de ingestão.

## Requirements

### Requirement: Schema de locais
O script `V004__create_venues.sql` SHALL criar a tabela `venue` com `id` BIGSERIAL, `public_id` UUID único gerado pelo banco, `city_id` obrigatório referenciando `city(id)`, `name` e `slug` obrigatórios (até 200 caracteres), endereço, bairro, CEP, `latitude`, `longitude`, `website_url`, `created_at`/`updated_at` com padrão `now()` e `UNIQUE (city_id, slug)`, e CHECKs de latitude (-90..90), longitude (-180..180) e CEP (`00000-000`), nulos aceitos.

#### Scenario: Migration aplicada em banco limpo
- **WHEN** os scripts de `database/migrations` são aplicados em ordem a um Postgres vazio
- **THEN** a tabela `venue` existe com as colunas, chaves e unicidades especificadas

#### Scenario: Cidade inexistente é rejeitada
- **WHEN** é inserida uma linha em `venue` com `city_id` que não existe em `city`
- **THEN** o banco rejeita a inserção por violação da chave estrangeira (SQLSTATE `23503`)

#### Scenario: Slug duplicado na mesma cidade é rejeitado
- **WHEN** é inserido um local com o par (`city_id`, `slug`) de um local existente
- **THEN** o banco rejeita a inserção por violação de unicidade (`23505`), enquanto o mesmo slug em outra cidade é aceito

#### Scenario: Coordenadas e CEP fora do formato são rejeitados
- **WHEN** é inserido um local com `latitude` 90.5, `longitude` -180.5 ou `postal_code` `01310100`
- **THEN** o banco rejeita cada inserção por violação de CHECK (`23514`)

#### Scenario: Limites e nulos são aceitos
- **WHEN** são inseridos locais com `latitude` -90 e 90, `longitude` -180 e 180, `postal_code` `01310-100`, ou os três nulos
- **THEN** o banco aceita as inserções

### Requirement: Regras de slug e updated_at no banco
O script V004 SHALL criar a função `slugify(text)`, que remove acentos (`unaccent`), converte para minúsculas, troca sequências de caracteres não alfanuméricos por um único hífen e remove hífens nas pontas, e a função genérica `set_updated_at()`, reutilizáveis por outras tabelas. `venue` SHALL ter um trigger que preenche `slug` com `slugify(name)` quando vier nulo ou vazio e um trigger que atualiza `updated_at` a cada UPDATE.

#### Scenario: Slug gerado a partir do nome
- **WHEN** é inserido um local com `name` `Teatro Municipal de São Paulo` e `slug` nulo
- **THEN** o local é gravado com `slug` `teatro-municipal-de-sao-paulo`

#### Scenario: Slug informado é preservado
- **WHEN** é inserido um local com `slug` `meu-slug`
- **THEN** o local é gravado com `slug` `meu-slug`, sem regeneração

#### Scenario: Nome sem caracteres alfanuméricos não gera slug
- **WHEN** é inserido um local com `name` `!!!` e `slug` nulo
- **THEN** o banco rejeita a inserção por violação de CHECK (`23514`) e nenhuma linha é criada

#### Scenario: updated_at muda no UPDATE
- **WHEN** um local existente é atualizado em outra transação
- **THEN** `updated_at` fica maior que o valor anterior e `created_at` permanece inalterado

### Requirement: Cidade do local deve existir e estar ativa
Ao criar um local, e ao substituir um local com `cityId` diferente do atual, a API SHALL validar `cityId` por meio de um caso de uso de `locations`; se a cidade não existir ou estiver inativa, SHALL responder HTTP 422 com `errors[]` contendo o campo `cityId`. A capability `venues` NÃO SHALL depender das classes de domínio, API ou infraestrutura de `locations`.

#### Scenario: Cidade inexistente retorna 422
- **WHEN** o cliente envia `POST /api/v1/venues` com `cityId` inexistente
- **THEN** a API responde HTTP 422 com corpo `Problem` (`status=422`) e `errors[]` com `field=cityId`, e nenhum local é criado

#### Scenario: Cidade inativa retorna 422
- **WHEN** o cliente envia `POST /api/v1/venues` com `cityId` de uma cidade com `active=false`
- **THEN** a API responde HTTP 422 com `errors[]` contendo `field=cityId`

#### Scenario: Local de cidade já inativa pode ser atualizado sem trocar de cidade
- **WHEN** o cliente envia `PUT /api/v1/venues/{id}` mantendo o `cityId` atual, de uma cidade que passou a `active=false`
- **THEN** a API responde HTTP 200 e a cidade não é revalidada

#### Scenario: Troca para cidade inativa retorna 422
- **WHEN** o cliente envia `PUT /api/v1/venues/{id}` com `cityId` de uma cidade inativa
- **THEN** a API responde HTTP 422 com `errors[]` contendo `field=cityId` e o local permanece inalterado

### Requirement: Cadastro de local
A API SHALL expor `POST /api/v1/venues` (tag `venues`) aceitando `cityId` e `name` obrigatórios e `slug`, `addressLine`, `neighborhood`, `postalCode`, `latitude`, `longitude` e `websiteUrl` opcionais, e responder HTTP 201 com o local criado no corpo e o cabeçalho `Location` apontando para `/api/v1/venues/{id}`. `slug` ausente SHALL ser gerado a partir de `name`. Corpo inválido SHALL resultar em HTTP 422 com `errors[]` por campo; slug já existente na cidade SHALL resultar em HTTP 409.

#### Scenario: Criação com slug gerado
- **WHEN** o cliente envia `POST /api/v1/venues` com `cityId` ativo e `name` `Sala São Paulo` sem `slug`
- **THEN** a API responde HTTP 201 com `Location` do novo local e corpo com `slug` `sala-sao-paulo`, `id` UUID, `createdAt` e `updatedAt`

#### Scenario: Criação com slug informado
- **WHEN** o cliente envia `POST /api/v1/venues` com `slug` `sala-sp` válido
- **THEN** a API responde HTTP 201 com `slug` `sala-sp`

#### Scenario: Campos inválidos retornam 422
- **WHEN** o cliente envia `POST /api/v1/venues` com `name` em branco, `slug` `Slug Inválido`, `postalCode` `01310100`, `latitude` 91 ou `websiteUrl` `ftp://x`
- **THEN** a API responde HTTP 422 com `Problem` (`status=422`) e `errors[]` com um item (`field`, `message`) por campo inválido, e nenhum local é criado

#### Scenario: Campo obrigatório ausente retorna 422
- **WHEN** o cliente envia `POST /api/v1/venues` sem `cityId` ou sem `name`
- **THEN** a API responde HTTP 422 com `errors[]` apontando o campo ausente

#### Scenario: Slug duplicado na cidade retorna 409
- **WHEN** o cliente envia `POST /api/v1/venues` com um `slug` que já existe na mesma cidade, informado ou gerado a partir de `name`
- **THEN** a API responde HTTP 409 com `Problem` (`status=409`) e nenhum local é criado

#### Scenario: Mesmo slug em outra cidade é aceito
- **WHEN** o cliente cria um local com slug já usado em outra cidade
- **THEN** a API responde HTTP 201

#### Scenario: Corpo malformado retorna 400
- **WHEN** o cliente envia `POST /api/v1/venues` com JSON ilegível
- **THEN** a API responde HTTP 400 com `Problem` (`status=400`)

### Requirement: Listagem paginada de locais
A API SHALL expor `GET /api/v1/venues` aceitando `city` (formato `uf/slug`, padrão `app.default-city`), `neighborhood` opcional (igualdade sem diferenciar maiúsculas), `page` (padrão 0) e `size` (padrão 20, máximo 100), e responder HTTP 200 com `items` ordenados por `name` e `page` (`PageMetadata`), apenas com locais da cidade selecionada.

#### Scenario: Padrão lista a cidade padrão
- **WHEN** o cliente envia `GET /api/v1/venues` sem parâmetros
- **THEN** a API responde HTTP 200 somente com locais da cidade de `app.default-city`, ordenados por `name`, e `page` com `page=0`, `size=20`

#### Scenario: Filtro por cidade
- **WHEN** o cliente envia `GET /api/v1/venues?city=sp/outra-cidade`
- **THEN** a API responde HTTP 200 somente com locais dessa cidade

#### Scenario: Filtro por bairro
- **WHEN** o cliente envia `GET /api/v1/venues?neighborhood=pinheiros`
- **THEN** a API responde HTTP 200 somente com locais cujo bairro é `Pinheiros`, sem diferenciar maiúsculas

#### Scenario: Paginação respeita page e size
- **WHEN** existem mais locais que `size` e o cliente envia `?page=1&size=1`
- **THEN** a API responde HTTP 200 com 1 item da segunda página e `page.page=1`, `page.size=1`

#### Scenario: Cidade com formato inválido retorna 400
- **WHEN** o cliente envia `GET /api/v1/venues?city=sao-paulo`
- **THEN** a API responde HTTP 400 com `Problem` (`status=400`, `detail` indicando `city`)

#### Scenario: Cidade inexistente retorna 404
- **WHEN** o cliente envia `GET /api/v1/venues?city=sp/nao-existe`
- **THEN** a API responde HTTP 404 com `Problem` (`status=404`)

#### Scenario: Paginação inválida retorna 400
- **WHEN** o cliente envia `?size=0`, `?size=101` ou `?page=-1`
- **THEN** a API responde HTTP 400 com `Problem` (`status=400`)

#### Scenario: Cidade padrão mal configurada retorna 500
- **WHEN** `app.default-city` aponta para cidade inexistente ou inativa e o cliente envia `GET /api/v1/venues` sem `city`
- **THEN** a API responde HTTP 500 com `Problem` (`status=500`) sem stack trace

### Requirement: Consulta de local por id
A API SHALL expor `GET /api/v1/venues/{id}`, com `id` UUID, respondendo HTTP 200 com o local ou HTTP 404 quando não existir.

#### Scenario: Local encontrado
- **WHEN** o cliente envia `GET /api/v1/venues/{id}` para um local existente
- **THEN** a API responde HTTP 200 com o DTO do local

#### Scenario: Local inexistente retorna 404
- **WHEN** o cliente envia `GET /api/v1/venues/{id}` com um UUID que não existe
- **THEN** a API responde HTTP 404 com `Problem` (`status=404`, `title`, `detail`)

#### Scenario: Id que não é UUID retorna 400
- **WHEN** o cliente envia `GET /api/v1/venues/123`
- **THEN** a API responde HTTP 400 com `Problem` (`status=400`)

### Requirement: Substituição completa de local
A API SHALL expor `PUT /api/v1/venues/{id}` com o mesmo corpo do cadastro, substituindo todos os campos do local (campo opcional ausente passa a nulo e `slug` ausente é regenerado de `name`), e responder HTTP 200 com o local atualizado, HTTP 404 se não existir, HTTP 409 se o slug já pertencer a outro local da cidade e HTTP 422 se o corpo for inválido. O `id` e `createdAt` SHALL permanecer; `updatedAt` SHALL ser atualizado.

#### Scenario: Substituição bem-sucedida
- **WHEN** o cliente envia `PUT /api/v1/venues/{id}` com corpo válido
- **THEN** a API responde HTTP 200 com os novos valores, mesmo `id` e `createdAt`, e `updatedAt` maior que o anterior

#### Scenario: Campo opcional ausente é limpo
- **WHEN** o cliente envia `PUT` sem `neighborhood` para um local que tinha bairro
- **THEN** a API responde HTTP 200 com `neighborhood` nulo

#### Scenario: Manter o próprio slug não conflita
- **WHEN** o cliente envia `PUT` repetindo o `slug` atual do próprio local
- **THEN** a API responde HTTP 200

#### Scenario: Local inexistente retorna 404
- **WHEN** o cliente envia `PUT /api/v1/venues/{id}` com UUID inexistente
- **THEN** a API responde HTTP 404 com `Problem` (`status=404`) e nenhum local é criado

#### Scenario: Slug de outro local retorna 409
- **WHEN** o cliente envia `PUT` com `slug` já usado por outro local da mesma cidade
- **THEN** a API responde HTTP 409 com `Problem` (`status=409`) e o local permanece inalterado

#### Scenario: Corpo inválido retorna 422
- **WHEN** o cliente envia `PUT` com `name` em branco ou `postalCode` inválido
- **THEN** a API responde HTTP 422 com `errors[]` por campo e o local permanece inalterado

### Requirement: Remoção de local
A API SHALL expor `DELETE /api/v1/venues/{id}` respondendo HTTP 204 sem corpo quando o local for removido, HTTP 404 quando não existir e HTTP 409 quando houver evento ligado ao local.

#### Scenario: Remoção bem-sucedida
- **WHEN** o cliente envia `DELETE /api/v1/venues/{id}` para um local existente sem eventos
- **THEN** a API responde HTTP 204 e um `GET` posterior do mesmo `id` responde HTTP 404

#### Scenario: Local inexistente retorna 404
- **WHEN** o cliente envia `DELETE /api/v1/venues/{id}` com UUID inexistente, ou repete um `DELETE` já concluído
- **THEN** a API responde HTTP 404 com `Problem` (`status=404`)

#### Scenario: Local com evento ligado retorna 409
- **WHEN** o cliente envia `DELETE /api/v1/venues/{id}` para um local referenciado por ao menos um evento, em qualquer `status`
- **THEN** a API responde HTTP 409 com `Problem` (`status=409`) e o local permanece cadastrado

#### Scenario: Remoção volta a ser possível sem eventos
- **WHEN** o último evento ligado ao local é removido e o cliente envia `DELETE /api/v1/venues/{id}`
- **THEN** a API responde HTTP 204

### Requirement: Representação de local sem expor o id interno
O DTO de local SHALL expor `id` (o `public_id` UUID), `cityId`, `name`, `slug`, `addressLine`, `neighborhood`, `postalCode`, `latitude`, `longitude`, `websiteUrl`, `createdAt` e `updatedAt` (ISO 8601 com fuso), com os campos opcionais anuláveis; a API NÃO SHALL expor o `BIGSERIAL` de `venue` em nenhum corpo, cabeçalho ou URL.

#### Scenario: Local com todos os campos
- **WHEN** a API retorna um local com todos os campos preenchidos
- **THEN** o DTO contém os doze campos, `id` em formato UUID e datas com fuso

#### Scenario: Local só com campos obrigatórios
- **WHEN** a API retorna um local sem endereço, bairro, CEP, coordenadas e site
- **THEN** o DTO traz esses campos com valor nulo, sem erro de serialização

#### Scenario: Id interno nunca aparece
- **WHEN** um local é criado e consultado pela API
- **THEN** o valor do `BIGSERIAL` não aparece no corpo nem no cabeçalho `Location`

### Requirement: Mapeamento JPA e isolamento entre módulos
O módulo `venues` SHALL mapear `venue` por entidade JPA no pacote `com.culturalevents.venues.{api,application,domain,infrastructure}`, compatível com o script V004 sob `ddl-auto=validate`, guardar a cidade apenas como `cityId` (`Long`) e acessar `locations` só por um caso de uso da camada `application` de `locations`.

#### Scenario: Contexto sobe com o schema V004
- **WHEN** o contexto Spring é iniciado contra um Postgres com as migrations aplicadas
- **THEN** a validação de schema do Hibernate passa e a aplicação inicia

#### Scenario: Contexto falha sem a migration
- **WHEN** o contexto Spring é iniciado contra um Postgres sem a tabela `venue`
- **THEN** a inicialização falha na validação do schema, reportando a tabela ou coluna ausente

#### Scenario: Venues não depende das classes internas de locations
- **WHEN** a suíte de arquitetura analisa as classes de `com.culturalevents.venues`
- **THEN** nenhuma depende de `com.culturalevents.locations.domain`, `.api` ou `.infrastructure`, e a regra falha se alguma passar a depender

### Requirement: Cidade de local com eventos não pode mudar
O banco SHALL rejeitar a alteração de `city_id` de um `venue` referenciado por algum `event`, e a API SHALL responder HTTP 409 em `PUT /api/v1/venues/{id}` que tente trocar a cidade de um local com eventos, mantendo o local inalterado.

#### Scenario: Troca de cidade de local com eventos retorna 409
- **WHEN** o cliente envia `PUT /api/v1/venues/{id}` com `cityId` diferente do atual para um local que tem ao menos um evento
- **THEN** a API responde HTTP 409 com `Problem` (`status=409`) e o local permanece na cidade original

#### Scenario: Troca de cidade de local sem eventos é aceita
- **WHEN** o cliente envia `PUT /api/v1/venues/{id}` com `cityId` de outra cidade ativa para um local sem eventos
- **THEN** a API responde HTTP 200 com o novo `cityId`

#### Scenario: Banco rejeita a troca por UPDATE direto
- **WHEN** um script faz `UPDATE venue SET city_id = <outra cidade>` num local referenciado por um evento
- **THEN** o banco rejeita o UPDATE com violação de restrição (SQLSTATE `23001`) e o local permanece inalterado
