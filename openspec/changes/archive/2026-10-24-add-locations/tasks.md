# Tasks

## 1. Infraestrutura de teste compartilhada

- [x] 1.1 Criar `AbstractPostgresIntegrationTest` (`src/test`) com um `PostgreSQLContainer` estático singleton iniciado uma única vez (bloco `static`), `@ServiceConnection` e aplicação de `database/migrations` uma única vez; verificar que duas classes de teste que a estendem rodam na mesma execução com um único container iniciado
- [x] 1.2 Migrar `PlatformFoundationIntegrationTest` para estender a classe base (removendo `@Container`/`@Testcontainers` e a aplicação própria de migrations); verificar `./mvnw test -Dtest=PlatformFoundationIntegrationTest` verde
- [x] 1.3 Documentar na classe base a regra de isolamento: fixtures inseridas por um teste são removidas ao final dele (`@AfterEach` ou `@Sql` pós-teste), e fornecer um helper de limpeza de cidades de fixture; verificar que dois testes que inserem a mesma fixture passam em qualquer ordem

## 2. Schema e carga inicial

- [x] 2.1 Criar `database/migrations/V002__create_locations.sql` com `state`, `city` (PKs, FK, `UNIQUE (ibge_code)`, `UNIQUE (state_uf, slug)`, `active DEFAULT false`), as 27 UFs e a cidade IBGE 3550308 ativa; verificar aplicando V001+V002 num Postgres 16 limpo e conferindo 27 linhas em `state` e 1 em `city` (`MigrationNamingConventionTest` continua verde)
- [x] 2.2 Criar `LocationsSchemaIntegrationTest` (estende a classe base, JDBC) cobrindo: tabelas/colunas criadas, seed de 27 UFs + cidade ativa, inserção com UF inexistente rejeitada, `ibge_code` duplicado rejeitado, (`state_uf`, `slug`) duplicado rejeitado (mesmo slug em outra UF aceito), cidade sem `active` fica `false`; fixtures removidas ao final de cada teste; verificar `./mvnw test -Dtest=LocationsSchemaIntegrationTest` passando

## 3. Contrato OpenAPI (antes do código)

- [x] 3.1 Atualizar `api/openapi.yaml` com a tag `locations`, os paths `GET /states`, `/cities`, `/cities/{uf}/{slug}`, `/cities/default` e os schemas `State`, `City` (lat/long nullable) e `CityPage` (reusando `PageMetadata`/`Problem` via `$ref`); verificar com `./mvnw generate-sources` gerando `LocationsApi`, `City`, `State`, `CityPage` em `target/generated-sources`
- [x] 3.2 Declarar `pattern ^[A-Za-z]{2}$` para `uf`, `active` default `true`, `page` (min 0, default 0) e `size` (1..100, default 20), e respostas 400/404/500 como `Problem`; verificar que a interface gerada contém as anotações de validação correspondentes

## 4. Domínio e infraestrutura

- [x] 4.1 Criar pacote `com.culturalevents.locations.{api,application,domain,infrastructure}` com entidades JPA `State` e `City` (em `City`, `stateUf` como `String` com `@JdbcTypeCode(SqlTypes.CHAR)` e `length = 2`, sem `@ManyToOne` para `State`) e portas `StateRepository`/`CityRepository` em `domain`; verificar que `ArchitectureTest` passa com as classes reais
- [x] 4.2 Implementar os adaptadores Spring Data em `infrastructure` (listar estados por nome; buscar cidades por `uf`/`active` paginado ordenado por `name, id`; buscar por `uf`+`slug`); verificar com `LocationsContextIntegrationTest` (estende a classe base; contexto sobe com `ddl-auto=validate`, incluindo `CHAR(2)` e `NUMERIC(9,6)`) e que a ausência da tabela `city` derruba o contexto (teste de schema divergente em contexto isolado)

## 5. Aplicação e API

- [x] 5.1 Implementar `LocationsService` e `DefaultCityResolver` (formato `uf/slug`, normalização de caixa, erros de formato/inexistente/inativa via `DefaultCityMisconfiguredException`); verificar com testes unitários (Mockito) cobrindo sucesso, formato inválido, inexistente e inativa
- [x] 5.2 Implementar `LocationsController` implementando `LocationsApi`, mapeamento entidade → DTO (`uf` a partir de `stateUf`, lat/long nulos) e `PageMetadataMapper`; verificar com teste unitário do mapper
- [x] 5.3 Ajustar o `GlobalExceptionHandler` para 400 `Problem` em parâmetros de query/path inválidos (mantendo 422 para bean validation de corpo) e criar o handler de `DefaultCityMisconfiguredException` → 500 `Problem` sem stack trace em `locations.api`; verificar estendendo `GlobalExceptionHandlerTest` com os três casos (parâmetro inválido 400, corpo inválido 422, configuração inválida 500)

## 6. Testes de integração dos endpoints

- [x] 6.1 Criar `LocationsApiIntegrationTest` (estende a classe base, MockMvc) para `GET /states` (200 com 27 itens ordenados; `POST` → 405 `Problem`); verificar execução verde
- [x] 6.2 Cobrir `GET /cities`: padrão só ativas, filtro `uf` (inclusive minúscula), `active=false`, paginação (`page`, `size`, `PageMetadata`), `uf` com 1 ou 3 letras → 400, `size=0`/`101`/`page=-1` → 400, `uf=ZZ` → 200 vazio; fixtures removidas ao final de cada teste; verificar execução verde
- [x] 6.3 Cobrir `GET /cities/{uf}/{slug}`: 200, `uf` minúscula, cidade inativa 200, inexistente 404 `Problem`, `uf` mal formada 400; e escrita (`POST /cities`, `PUT`, `DELETE`) → 405 `Problem`; verificar execução verde
- [x] 6.4 Cobrir `GET /cities/default` com `@TestPropertySource` por classe: cidade configurada 200, troca de configuração, inexistente 500, inativa 500, formato inválido 500 (todos com `Problem`, sem stack trace); verificar execução verde
- [x] 6.5 Cobrir o DTO: cidade com e sem coordenadas (lat/long nulos serializados), nove campos presentes; verificar execução verde e que a suíte inteira passa com a ordem dos testes embaralhada

## 7. Regra "sem cidade fixa" e verificação final

- [x] 7.1 Criar `NoHardcodedCityTest` que varre `backend/src/main/java` por nome, slug e IBGE da cidade da carga inicial (valores lidos do próprio teste, fora de `src/main`) e um teste negativo com arquivo-fixture violador que prova que a varredura falha; verificar ambos verdes
- [x] 7.2 Atualizar o `README.md` com a seção de localidades (como cadastrar cidade por script SQL, `app.default-city`, recriar volume do Docker para aplicar V002); verificar que os comandos documentados rodam como escritos
- [x] 7.3 Rodar `./mvnw clean verify` em `backend/` e `docker compose down -v && docker compose up -d` conferindo `state`/`city` populadas; verificar build verde e consulta SQL retornando 27 UFs

## Workflow follow-up

- Validar a change (`openspec validate add-locations --strict`) e arquivá-la após o merge.
