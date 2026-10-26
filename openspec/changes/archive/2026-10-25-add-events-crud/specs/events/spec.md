# Spec Delta

## Purpose

A capability `events` mantém o cadastro de eventos culturais (shows, teatro, exposições) com suas sessões e o expõe por API CRUD, com listagem filtrada por cidade, categoria, período e status. Slug, `updated_at` e invariantes de preço e local ficam no banco, para valer também para o agente de ingestão.

## ADDED Requirements

### Requirement: Schema de eventos e sessões
O script `V005__create_events.sql` SHALL criar `event` (com `public_id` UUID único gerado, cidade e categoria obrigatórias, local opcional, `status` padrão `DRAFT`, `origin` padrão `MANUAL`, `version` padrão 0 e `UNIQUE (city_id, slug)`) e `occurrence` (com `event_id` `ON DELETE CASCADE`, `status` padrão `SCHEDULED` e `UNIQUE (event_id, starts_at)`).

#### Scenario: Migration aplicada em banco limpo
- **WHEN** os scripts de `database/migrations` são aplicados em ordem a um Postgres vazio
- **THEN** as tabelas `event` e `occurrence` existem com as colunas, padrões, chaves e unicidades especificados

#### Scenario: Referências inexistentes são rejeitadas
- **WHEN** é inserido um `event` com `city_id`, `category_id` ou `venue_id` inexistente
- **THEN** o banco rejeita a inserção por violação de chave estrangeira (SQLSTATE `23503`)

#### Scenario: Slug duplicado na cidade e sessão duplicada são rejeitados
- **WHEN** é inserido um evento com (`city_id`, `slug`) já existente, ou uma sessão com (`event_id`, `starts_at`) já existente
- **THEN** o banco rejeita cada inserção por violação de unicidade (`23505`), enquanto o mesmo slug em outra cidade é aceito

#### Scenario: Remoção do evento apaga as sessões
- **WHEN** um `event` com sessões é removido
- **THEN** as linhas de `occurrence` dele são removidas e as de outros eventos permanecem

### Requirement: Regras de integridade de eventos no banco
O banco SHALL garantir, por constraints nomeadas e para qualquer escritor, os domínios de `status` e `origin` do evento e de `status` da sessão, preços não negativos com máximo não menor que o mínimo, evento gratuito sem preço positivo e `ends_at >= starts_at`. Triggers SHALL gerar `slug` de `title` quando nulo ou vazio, atualizar `updated_at` a cada UPDATE e exigir `venue.city_id = event.city_id` quando houver `venue_id`.

#### Scenario: Valores fora do domínio são rejeitados
- **WHEN** é inserido um evento com `status` `FOO`, `origin` `BOT`, `price_min_cents` -1, ou `price_max_cents` menor que `price_min_cents`
- **THEN** o banco rejeita cada inserção por violação de CHECK (`23514`) e informa o nome da constraint violada

#### Scenario: Evento gratuito com preço positivo é rejeitado
- **WHEN** é inserido ou atualizado um evento com `is_free` verdadeiro e `price_min_cents` ou `price_max_cents` maior que zero
- **THEN** o banco rejeita a escrita por violação de CHECK (`23514`), e aceita `is_free` verdadeiro com preços nulos ou zero

#### Scenario: Sessão com fim antes do início é rejeitada
- **WHEN** é inserida uma sessão com `ends_at` menor que `starts_at`
- **THEN** o banco rejeita a inserção por violação de CHECK (`23514`), e aceita `ends_at` nulo ou igual a `starts_at`

#### Scenario: Slug gerado do título
- **WHEN** é inserido um evento com `title` `Festival de Jazz de São Paulo` e `slug` nulo
- **THEN** o evento é gravado com `slug` `festival-de-jazz-de-sao-paulo`; um `title` sem caracteres alfanuméricos com `slug` nulo é rejeitado com `23514`

#### Scenario: updated_at muda no UPDATE
- **WHEN** um evento existente é atualizado em outra transação
- **THEN** `updated_at` fica maior que o valor anterior e `created_at` permanece inalterado

#### Scenario: Local de outra cidade é rejeitado
- **WHEN** é inserido ou atualizado um evento cujo `venue_id` aponta para um local de `city_id` diferente do `city_id` do evento
- **THEN** o banco rejeita a escrita com violação de CHECK (`23514`), e aceita um evento sem `venue_id` ou com local da mesma cidade

### Requirement: Referências do evento devem ser válidas
Ao criar ou substituir um evento, a API SHALL responder HTTP 422 com `errors[]` se `cityId` não existir ou estiver inativa (campo `cityId`), se `categoryId` não existir (campo `categoryId`) ou se `venueId` não existir ou pertencer a outra cidade que não a do evento (campo `venueId`), acumulando os erros de todos os campos. A cidade só é revalidada na substituição se `cityId` mudou.

#### Scenario: Cidade inexistente ou inativa retorna 422
- **WHEN** o cliente envia `POST /api/v1/events` com `cityId` inexistente ou de cidade com `active=false`
- **THEN** a API responde HTTP 422 com `Problem` (`status=422`) e `errors[]` com `field=cityId`, e nenhum evento é criado

#### Scenario: Categoria inexistente retorna 422
- **WHEN** o cliente envia `POST /api/v1/events` com `categoryId` inexistente
- **THEN** a API responde HTTP 422 com `errors[]` contendo `field=categoryId`

#### Scenario: Local inexistente ou de outra cidade retorna 422
- **WHEN** o cliente envia `POST /api/v1/events` com `venueId` inexistente, ou de um local cuja cidade difere de `cityId`
- **THEN** a API responde HTTP 422 com `errors[]` contendo `field=venueId`

#### Scenario: Erros de referência são acumulados
- **WHEN** o cliente envia um evento com `categoryId` e `venueId` inválidos ao mesmo tempo
- **THEN** a API responde HTTP 422 com um item em `errors[]` para cada um dos dois campos

### Requirement: Cadastro de evento com sessões
A API SHALL expor `POST /api/v1/events` (tag `events`) com `cityId`, `categoryId` e `title` obrigatórios, campos opcionais do evento (incluindo `venueId`, `slug`, `status` padrão `DRAFT`, preços e `occurrences[]`) e responder HTTP 201 com o evento e o cabeçalho `Location`. `slug` ausente é gerado de `title`. Corpo inválido, preço incoerente e sessões inválidas SHALL resultar em HTTP 422 com `errors[]` por campo; slug existente na cidade em HTTP 409.

#### Scenario: Criação mínima
- **WHEN** o cliente envia `POST /api/v1/events` com `cityId`, `categoryId` e `title` `Show de Teste` válidos e sem sessões
- **THEN** a API responde HTTP 201 com `Location`, `status` `DRAFT`, `slug` `show-de-teste`, `version` 0, `occurrences` vazio e `origin` `MANUAL`

#### Scenario: Criação completa com sessões
- **WHEN** o cliente envia um evento com `venueId` da mesma cidade, preços e duas sessões
- **THEN** a API responde HTTP 201 com `venue`, `category` e `occurrences` ordenadas por `startsAt`

#### Scenario: Campos inválidos retornam 422
- **WHEN** o cliente envia `title` em branco, `slug` `Slug Inválido`, `priceMinCents` -1, ou `ticketUrl` `ftp://x`
- **THEN** a API responde HTTP 422 com `Problem` (`status=422`) e `errors[]` com um item por campo inválido, e nenhum evento é criado

#### Scenario: Preço incoerente retorna 422
- **WHEN** o cliente envia `priceMaxCents` menor que `priceMinCents`, ou `isFree` verdadeiro com `priceMinCents` 1000
- **THEN** a API responde HTTP 422 com `errors[]` apontando `priceMaxCents` ou `isFree` e nenhum evento é criado

#### Scenario: Sessões inválidas retornam 422
- **WHEN** o cliente envia uma sessão com `endsAt` anterior a `startsAt`, sem `startsAt`, ou duas sessões com o mesmo `startsAt`
- **THEN** a API responde HTTP 422 com `errors[]` cujo `field` começa com `occurrences` e nenhum evento é criado

#### Scenario: Slug duplicado na cidade retorna 409
- **WHEN** o cliente envia `POST /api/v1/events` com `slug`, informado ou gerado de `title`, já existente na mesma cidade
- **THEN** a API responde HTTP 409 com `Problem` (`status=409`) e nenhum evento é criado

#### Scenario: Mesmo slug em outra cidade é aceito
- **WHEN** o cliente cria um evento com slug já usado em outra cidade
- **THEN** a API responde HTTP 201

#### Scenario: Corpo malformado ou valor de enum desconhecido retorna 400
- **WHEN** o cliente envia `POST /api/v1/events` com JSON ilegível, ou com `status` (do evento ou da sessão) fora dos valores permitidos, como `FOO`
- **THEN** a API responde HTTP 400 com `Problem` (`status=400`)

### Requirement: Publicação exige sessão futura
Ao criar ou substituir um evento com `status` `PUBLISHED`, a API SHALL exigir ao menos uma sessão com `startsAt` posterior ao instante atual; caso contrário SHALL responder HTTP 422 com `errors[]` em `occurrences`. Eventos em outros `status` SHALL ser aceitos sem sessões ou só com sessões passadas.

#### Scenario: Publicação sem sessões retorna 422
- **WHEN** o cliente envia `POST /api/v1/events` com `status` `PUBLISHED` e `occurrences` vazio
- **THEN** a API responde HTTP 422 com `errors[]` contendo `field=occurrences` e nenhum evento é criado

#### Scenario: Publicação só com sessões passadas retorna 422
- **WHEN** o cliente envia `status` `PUBLISHED` com todas as sessões em datas passadas
- **THEN** a API responde HTTP 422 com `errors[]` contendo `field=occurrences`

#### Scenario: Publicação com sessão futura é aceita
- **WHEN** o cliente envia `status` `PUBLISHED` com ao menos uma sessão futura
- **THEN** a API responde HTTP 201 com `status` `PUBLISHED`

#### Scenario: Evento arquivado só com sessões passadas é aceito
- **WHEN** o cliente envia `status` `ARCHIVED` com apenas sessões passadas
- **THEN** a API responde HTTP 201

### Requirement: Listagem paginada de eventos
A API SHALL expor `GET /api/v1/events` com os filtros `city` (`uf/slug`, padrão `app.default-city`), `category` (slug, inclui subcategorias), `from`/`to` (sessões com `startsAt` entre eles, inclusive), `status` (ausente = todos), `page` (padrão 0) e `size` (padrão 20, máximo 100), e responder HTTP 200 com `items` e `page` (`PageMetadata`), ordenados pela próxima sessão a partir do maior entre agora e `from`, depois os sem sessão futura, depois por `id`.

#### Scenario: Padrão lista a cidade padrão
- **WHEN** o cliente envia `GET /api/v1/events` sem parâmetros
- **THEN** a API responde HTTP 200 somente com eventos da cidade de `app.default-city`, de qualquer `status`, e `page` com `page=0`, `size=20`

#### Scenario: Ordenação pela próxima sessão
- **WHEN** existem eventos com próximas sessões em datas diferentes, um sem sessão e um só com sessões passadas
- **THEN** os itens saem em ordem crescente da próxima sessão, seguidos pelos sem sessão futura

#### Scenario: Filtro por categoria inclui subcategorias
- **WHEN** o cliente envia `GET /api/v1/events?category=shows` e há eventos na categoria `shows` e em uma subcategoria dela
- **THEN** a API responde HTTP 200 com os eventos das duas, e `category=<subcategoria>` traz só os dessa subcategoria

#### Scenario: Filtro por intervalo de sessões
- **WHEN** o cliente envia `from` e `to` e um evento tem sessões dentro e outras fora do intervalo
- **THEN** a API responde HTTP 200 incluindo o evento uma vez, e excluindo eventos sem nenhuma sessão no intervalo

#### Scenario: Filtro por status e por cidade
- **WHEN** o cliente envia `status=PUBLISHED` ou `city=uf/slug` de outra cidade
- **THEN** a API responde HTTP 200 somente com eventos nesse `status` ou nessa cidade

#### Scenario: Paginação respeita page e size
- **WHEN** existem mais eventos que `size` e o cliente envia `?page=1&size=1`
- **THEN** a API responde HTTP 200 com 1 item da segunda página e `page.totalElements` correto

#### Scenario: Parâmetros inválidos retornam 400
- **WHEN** o cliente envia `city=sao-paulo`, `status=FOO`, `from=ontem`, `size=0`, `size=101` ou `page=-1`
- **THEN** a API responde HTTP 400 com `Problem` (`status=400`)

#### Scenario: Cidade ou categoria inexistente retorna 404
- **WHEN** o cliente envia `city=sp/nao-existe` ou `category=nao-existe`
- **THEN** a API responde HTTP 404 com `Problem` (`status=404`)

#### Scenario: Cidade padrão mal configurada retorna 500
- **WHEN** `app.default-city` aponta para cidade inexistente ou inativa e o cliente envia `GET /api/v1/events` sem `city`
- **THEN** a API responde HTTP 500 com `Problem` (`status=500`) sem stack trace

### Requirement: Consulta e representação de evento
A API SHALL expor `GET /api/v1/events/{id}` (`id` UUID) respondendo HTTP 200 ou HTTP 404. O DTO SHALL trazer `id` (o `public_id`), `cityId`, `title`, `slug`, `description`, `status`, `ageRating`, `priceMinCents`, `priceMaxCents`, `isFree`, `ticketUrl`, `imageUrl`, `origin`, `version`, `createdAt`, `updatedAt`, `category` (`id`, `slug`, `name`), `venue` (`id` UUID, `name`, `slug`) ou nulo e `occurrences` ordenadas por `startsAt`; a API NÃO SHALL expor o `BIGSERIAL` de `event` nem de `venue`.

#### Scenario: Evento com local e sessões
- **WHEN** o cliente consulta um evento com local e duas sessões
- **THEN** a API responde HTTP 200 com `category`, `venue` e `occurrences` ordenadas por `startsAt`, e `id` no formato UUID

#### Scenario: Evento sem local
- **WHEN** o cliente consulta um evento sem `venueId`
- **THEN** a API responde HTTP 200 com `venue` nulo e campos opcionais nulos, sem erro de serialização

#### Scenario: Evento inexistente retorna 404
- **WHEN** o cliente envia `GET /api/v1/events/{id}` com UUID inexistente
- **THEN** a API responde HTTP 404 com `Problem` (`status=404`, `title`, `detail`)

#### Scenario: Id que não é UUID retorna 400
- **WHEN** o cliente envia `GET /api/v1/events/123`
- **THEN** a API responde HTTP 400 com `Problem` (`status=400`)

#### Scenario: Ids internos não aparecem
- **WHEN** um evento com local é criado e consultado
- **THEN** nenhum `BIGSERIAL` de `event` ou `venue` aparece no corpo ou no `Location`

### Requirement: Substituição completa com lock otimista
A API SHALL expor `PUT /api/v1/events/{id}` com o corpo do cadastro, mas com `version` e `status` obrigatórios, substituindo todos os campos e sessões (opcional ausente vira nulo, sessões ausentes são removidas), e responder HTTP 200 com `version` incrementada, 404 se não existir, 409 se `version` estiver desatualizada ou o slug for de outro evento, e 422 se inválido. `id`, `origin` e `createdAt` permanecem; `updatedAt` muda a cada substituição.

#### Scenario: Substituição bem-sucedida
- **WHEN** o cliente envia `PUT /api/v1/events/{id}` com `version` igual à atual e corpo válido com outro conjunto de sessões
- **THEN** a API responde HTTP 200 com os novos valores e sessões, mesmo `id` e `createdAt`, `version` incrementada e `updatedAt` maior

#### Scenario: Substituição idêntica ainda incrementa a versão
- **WHEN** o cliente envia `PUT` com os mesmos valores que o evento já tem
- **THEN** a API responde HTTP 200 com `version` incrementada e `updatedAt` maior

#### Scenario: Version desatualizada retorna 409
- **WHEN** o cliente envia `PUT` com `version` menor que a atual, por exemplo reutilizando a de uma leitura anterior
- **THEN** a API responde HTTP 409 com `Problem` (`status=409`) e o evento permanece inalterado

#### Scenario: Dois PUT com a mesma version
- **WHEN** dois clientes enviam `PUT` com a mesma `version` e o primeiro é aceito
- **THEN** o segundo recebe HTTP 409 e nenhuma alteração dele é gravada

#### Scenario: Sessão mantida por startsAt é atualizada, não duplicada
- **WHEN** o `PUT` repete o `startsAt` de uma sessão existente com `status` `SOLD_OUT`
- **THEN** a API responde HTTP 200 com uma única sessão nesse horário, com `status` `SOLD_OUT`

#### Scenario: Evento inexistente retorna 404
- **WHEN** o cliente envia `PUT /api/v1/events/{id}` com UUID inexistente
- **THEN** a API responde HTTP 404 com `Problem` (`status=404`) e nenhum evento é criado

#### Scenario: Slug de outro evento retorna 409
- **WHEN** o cliente envia `PUT` com `slug` já usado por outro evento da mesma cidade
- **THEN** a API responde HTTP 409 e o evento permanece inalterado

#### Scenario: Corpo inválido, version ausente ou status ausente retorna 422
- **WHEN** o cliente envia `PUT` sem `version`, sem `status`, com `title` em branco, ou referência inválida em `categoryId` ou `venueId`
- **THEN** a API responde HTTP 422 com `errors[]` por campo (`version`, `status`, `title`, `categoryId` ou `venueId`) e o evento permanece inalterado

#### Scenario: Status ausente no PUT não despublica o evento
- **WHEN** o cliente envia `PUT` de um evento `PUBLISHED` sem o campo `status`
- **THEN** a API responde HTTP 422 com `errors[]` contendo `field=status` e o evento permanece `PUBLISHED` com a mesma `version`

### Requirement: Remoção de evento com sessões
A API SHALL expor `DELETE /api/v1/events/{id}` respondendo HTTP 204 sem corpo e removendo o evento e todas as suas sessões, ou HTTP 404 quando não existir.

#### Scenario: Remoção em cascata
- **WHEN** o cliente envia `DELETE /api/v1/events/{id}` para um evento com sessões
- **THEN** a API responde HTTP 204, um `GET` posterior responde HTTP 404 e nenhuma sessão do evento permanece

#### Scenario: Evento inexistente retorna 404
- **WHEN** o cliente envia `DELETE /api/v1/events/{id}` com UUID inexistente, ou repete um `DELETE` já concluído
- **THEN** a API responde HTTP 404 com `Problem` (`status=404`)

### Requirement: Mapeamento JPA e isolamento entre módulos
O módulo `events` SHALL mapear `event` e `occurrence` por entidades JPA em `com.culturalevents.events.{api,application,domain,infrastructure}`, compatíveis com o script V005 sob `ddl-auto=validate`, com `@Version` em `version`, guardar cidade, categoria e local como ids (`Long`) e acessar `locations`, `categories` e `venues` só por casos de uso da camada `application` deles.

#### Scenario: Contexto sobe com o schema V005
- **WHEN** o contexto Spring é iniciado contra um Postgres com as migrations aplicadas
- **THEN** a validação de schema do Hibernate passa e a aplicação inicia

#### Scenario: Contexto falha sem a migration
- **WHEN** o contexto Spring é iniciado contra um Postgres sem as tabelas `event` ou `occurrence`
- **THEN** a inicialização falha na validação do schema, reportando a tabela ou coluna ausente

#### Scenario: Events não depende das classes internas dos outros módulos
- **WHEN** a suíte de arquitetura analisa as classes de `com.culturalevents.events`
- **THEN** nenhuma depende de `domain`, `api` ou `infrastructure` de `locations`, `categories` ou `venues`, e a regra falha se alguma passar a depender
