## Purpose
A capability `locations` mantém o catálogo somente leitura de estados e cidades do Brasil e expõe sua consulta por API, incluindo a resolução da cidade padrão por configuração. É a base do comportamento multicidade: nenhuma outra capability pode presumir uma única cidade.

## ADDED Requirements

### Requirement: Schema de estados e cidades
O banco SHALL ter as tabelas `state` (`uf` CHAR(2) PK, `name` VARCHAR(60) NOT NULL) e `city` (`id` BIGSERIAL PK, `ibge_code` INT NOT NULL UNIQUE, `state_uf` CHAR(2) NOT NULL referenciando `state(uf)`, `name` VARCHAR(120) NOT NULL, `slug` VARCHAR(120) NOT NULL, `timezone` VARCHAR(40) NOT NULL, `latitude` NUMERIC(9,6), `longitude` NUMERIC(9,6), `active` BOOLEAN NOT NULL DEFAULT false, `UNIQUE (state_uf, slug)`), criadas pelo script `V002__create_locations.sql`.

#### Scenario: Migration aplicada em banco limpo
- **WHEN** os scripts de `database/migrations` são aplicados em ordem a um Postgres vazio
- **THEN** as tabelas `state` e `city` existem com as colunas, chaves e restrições especificadas

#### Scenario: Cidade com UF inexistente é rejeitada
- **WHEN** é inserida uma linha em `city` com `state_uf` que não existe em `state`
- **THEN** o banco rejeita a inserção por violação da chave estrangeira

#### Scenario: Código IBGE duplicado é rejeitado
- **WHEN** é inserida uma cidade com `ibge_code` já existente
- **THEN** o banco rejeita a inserção por violação de unicidade

#### Scenario: Slug duplicado na mesma UF é rejeitado
- **WHEN** é inserida uma cidade com o mesmo par (`state_uf`, `slug`) de uma cidade existente
- **THEN** o banco rejeita a inserção por violação de unicidade, enquanto o mesmo slug em outra UF é aceito

### Requirement: Carga inicial de localidades
O script `V002__create_locations.sql` SHALL inserir as 27 unidades federativas do Brasil e a cidade de código IBGE 3550308 (slug `sao-paulo`, fuso `America/Sao_Paulo`) com `active=true`; qualquer outra cidade inserida sem informar `active` SHALL ficar inativa.

#### Scenario: Carga inicial presente
- **WHEN** as migrations são aplicadas em banco limpo
- **THEN** `state` contém exatamente 27 linhas e `city` contém a cidade 3550308 com `active=true`

#### Scenario: Cidade nova sem flag de ativação fica inativa
- **WHEN** uma cidade é inserida sem informar `active`
- **THEN** ela é gravada com `active=false` e não aparece na listagem padrão da API

### Requirement: Listagem de estados
A API SHALL expor `GET /api/v1/states` (tag `locations`) retornando HTTP 200 com a lista de todas as UFs (`uf`, `name`), ordenada por `name`.

#### Scenario: Lista as 27 UFs
- **WHEN** o cliente envia `GET /api/v1/states`
- **THEN** a API responde HTTP 200 com 27 itens, cada um com `uf` (2 letras) e `name`, ordenados por `name`

#### Scenario: Método de escrita é rejeitado
- **WHEN** o cliente envia `POST /api/v1/states`
- **THEN** a API responde HTTP 405 com corpo `Problem` (`status=405`) e nenhuma linha é criada

### Requirement: Listagem paginada de cidades
A API SHALL expor `GET /api/v1/cities` aceitando os filtros opcionais `uf` (exatamente 2 letras, sem diferenciar maiúsculas) e `active` (booleano, padrão `true`), além dos parâmetros de paginação `page` (padrão 0) e `size` (padrão 20, máximo 100), retornando HTTP 200 com `items` (cidades ordenadas por `name`) e `page` (`PageMetadata`).

#### Scenario: Padrão lista apenas cidades ativas
- **WHEN** o cliente envia `GET /api/v1/cities` sem parâmetros
- **THEN** a API responde HTTP 200 com apenas cidades `active=true` e `page` com `page=0`, `size=20`, `totalElements` e `totalPages`

#### Scenario: Filtro por UF
- **WHEN** o cliente envia `GET /api/v1/cities?uf=sp`
- **THEN** a API responde HTTP 200 somente com cidades cuja `uf` é `SP`

#### Scenario: Filtro por cidades inativas
- **WHEN** o cliente envia `GET /api/v1/cities?active=false`
- **THEN** a API responde HTTP 200 somente com cidades `active=false`

#### Scenario: Paginação respeita page e size
- **WHEN** existem mais cidades do que `size` e o cliente envia `GET /api/v1/cities?active=false&page=1&size=1`
- **THEN** a API responde HTTP 200 com 1 item da segunda página e `page.page=1`, `page.size=1`

#### Scenario: UF com tamanho inválido retorna 400
- **WHEN** o cliente envia `GET /api/v1/cities?uf=S` ou `?uf=SPP`
- **THEN** a API responde HTTP 400 com corpo `Problem` (`status=400`, `title`, `detail` indicando o parâmetro `uf`) e nenhuma consulta é devolvida

#### Scenario: Paginação inválida retorna 400
- **WHEN** o cliente envia `GET /api/v1/cities?size=0` ou `?size=101` ou `?page=-1`
- **THEN** a API responde HTTP 400 com corpo `Problem` (`status=400`)

#### Scenario: UF bem formada porém inexistente retorna lista vazia
- **WHEN** o cliente envia `GET /api/v1/cities?uf=ZZ`
- **THEN** a API responde HTTP 200 com `items` vazio e `page.totalElements=0`

### Requirement: Consulta de cidade por UF e slug
A API SHALL expor `GET /api/v1/cities/{uf}/{slug}` retornando HTTP 200 com a cidade correspondente, independentemente de estar ativa (o campo `active` informa o estado), e HTTP 404 quando não existir cidade com esse par; `uf` SHALL ser comparada sem diferenciar maiúsculas.

#### Scenario: Cidade encontrada
- **WHEN** o cliente envia `GET /api/v1/cities/{uf}/{slug}` para uma cidade existente
- **THEN** a API responde HTTP 200 com o DTO da cidade

#### Scenario: UF em minúsculas é aceita
- **WHEN** o cliente envia `GET /api/v1/cities/sp/{slug}` para uma cidade cuja UF é `SP`
- **THEN** a API responde HTTP 200 com a mesma cidade retornada para `SP`

#### Scenario: Cidade inativa é retornada
- **WHEN** o cliente consulta uma cidade existente com `active=false`
- **THEN** a API responde HTTP 200 com `active=false`

#### Scenario: Cidade inexistente retorna 404
- **WHEN** o cliente envia `GET /api/v1/cities/{uf}/slug-que-nao-existe`
- **THEN** a API responde HTTP 404 com corpo `Problem` (`status=404`, `title`, `detail`)

#### Scenario: UF mal formada retorna 400
- **WHEN** o cliente envia `GET /api/v1/cities/SPP/{slug}`
- **THEN** a API responde HTTP 400 com corpo `Problem` (`status=400`)

### Requirement: Cidade padrão por configuração
A API SHALL expor `GET /api/v1/cities/default` retornando HTTP 200 com a cidade identificada pela propriedade `app.default-city` (formato `uf/slug`). Se a propriedade estiver malformada, apontar para uma cidade inexistente ou para uma cidade com `active=false`, a API SHALL responder HTTP 500 com corpo `Problem` indicando erro de configuração, sem stack trace.

#### Scenario: Cidade padrão resolvida
- **WHEN** `app.default-city` aponta para uma cidade existente e ativa e o cliente envia `GET /api/v1/cities/default`
- **THEN** a API responde HTTP 200 com o DTO dessa cidade

#### Scenario: Troca de configuração troca a cidade padrão
- **WHEN** `app.default-city` é alterada para outra cidade ativa e o cliente envia `GET /api/v1/cities/default`
- **THEN** a API responde HTTP 200 com a cidade configurada, sem nenhuma alteração de código

#### Scenario: Configuração aponta para cidade inexistente
- **WHEN** `app.default-city` aponta para um par `uf/slug` que não existe e o cliente envia `GET /api/v1/cities/default`
- **THEN** a API responde HTTP 500 com corpo `Problem` (`status=500`, `title`, `detail` informando que a cidade padrão configurada não existe)

#### Scenario: Configuração aponta para cidade inativa
- **WHEN** `app.default-city` aponta para uma cidade existente com `active=false`
- **THEN** a API responde HTTP 500 com corpo `Problem` (`status=500`, `detail` informando que a cidade padrão configurada está inativa)

#### Scenario: Configuração malformada
- **WHEN** `app.default-city` não segue o formato `uf/slug` (ex.: `sao-paulo`)
- **THEN** a API responde HTTP 500 com corpo `Problem` (`status=500`, `detail` informando o formato esperado)

### Requirement: Representação de cidade e de estado
O DTO de cidade SHALL expor `id`, `ibgeCode`, `uf`, `name`, `slug`, `timezone`, `latitude`, `longitude` e `active`, com `latitude` e `longitude` anuláveis; o DTO de estado SHALL expor `uf` e `name`.

#### Scenario: Cidade com coordenadas
- **WHEN** a API retorna uma cidade com latitude e longitude cadastradas
- **THEN** o DTO contém todos os nove campos, com `latitude` e `longitude` numéricas

#### Scenario: Cidade sem coordenadas
- **WHEN** a API retorna uma cidade cadastrada sem latitude e longitude
- **THEN** o DTO contém os campos `latitude` e `longitude` com valor nulo, sem erro de serialização

### Requirement: Localidades somente leitura na API
A API NÃO SHALL oferecer operação de escrita sobre estados e cidades; o cadastro de novas localidades SHALL ser feito exclusivamente por script SQL.

#### Scenario: Escrita em cidades é rejeitada
- **WHEN** o cliente envia `POST /api/v1/cities`, `PUT /api/v1/cities/{uf}/{slug}` ou `DELETE /api/v1/cities/{uf}/{slug}`
- **THEN** a API responde HTTP 405 com corpo `Problem` (`status=405`) e nenhum dado é alterado

### Requirement: Nenhuma referência fixa a uma cidade no código
O código-fonte de produção do backend (`src/main`) NÃO SHALL conter literais que identifiquem uma cidade específica (nome, slug ou código IBGE); a cidade padrão SHALL vir exclusivamente de `app.default-city` e de dados do banco.

#### Scenario: Código sem referência a cidade específica
- **WHEN** a suíte de testes varre os arquivos Java de `backend/src/main` procurando o nome, o slug e o código IBGE da cidade da carga inicial
- **THEN** nenhuma ocorrência é encontrada

#### Scenario: Varredura falha ao encontrar referência
- **WHEN** um arquivo Java de produção contém um literal com o nome ou slug de uma cidade específica
- **THEN** o teste de varredura falha, indicando o arquivo e o literal

### Requirement: Mapeamento JPA compatível com o schema
O módulo `locations` SHALL mapear `state` e `city` por entidades JPA no pacote `com.culturalevents.locations.{api,application,domain,infrastructure}`, compatíveis com o script V002 sob `ddl-auto=validate`.

#### Scenario: Contexto sobe com o schema V002
- **WHEN** o contexto Spring é iniciado contra um Postgres com as migrations aplicadas
- **THEN** a validação de schema do Hibernate passa e a aplicação inicia

#### Scenario: Contexto falha sem a migration
- **WHEN** o contexto Spring é iniciado contra um Postgres sem a tabela `city` ou `state`
- **THEN** a inicialização falha na validação do schema, reportando a tabela ou coluna ausente
