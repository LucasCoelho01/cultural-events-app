# Design

## Context

- `platform-foundation` já entrega: openapi-generator (`spring`, `interfaceOnly`, `useTags`) com saída em `com.culturalevents.shared.api` / `shared.api.model`, `GlobalExceptionHandler` (ProblemDetail), `PageMetadataMapper`, `ddl-auto=validate`, `app.default-city: sp/sao-paulo` e ArchUnit para `api -> application -> domain <- infrastructure`.
- `api/openapi.yaml` tem `paths: {}` e os componentes `Problem`, `FieldError`, `PageMetadata`.
- O handler global hoje mapeia `ConstraintViolationException` para **422**; esta change exige **400** para `uf` inválido.
- Migrations são aplicadas por Docker Compose (`initdb.d`) e, nos testes, pelo próprio teste (`PlatformFoundationIntegrationTest` lê `database/migrations/*.sql` em ordem).

Motivação e escopo: ver proposal.md.

## Goals / Non-Goals

**Goals:**
- Schema e API de localidades prontos para serem referenciados por capabilities futuras via `city.id`.
- Cidade padrão 100% configuração + dado; zero literal de cidade em `src/main`.

**Non-Goals:**
- Endpoints de escrita, importação do IBGE, geocodificação, cache.
- Carregar municípios além da cidade da carga inicial (novas cidades entram por scripts V00N futuros).
- Validar `app.default-city` na inicialização (ver Decisão 5).

## Decisions

**1. Arquivos e mudanças de contrato**
- SQL novo: `database/migrations/V002__create_locations.sql` (tabelas `state`, `city`, seed de 27 UFs + cidade IBGE 3550308). Sem índices extras: `UNIQUE (state_uf, slug)` serve o lookup por UF/slug e `UNIQUE (ibge_code)` o lookup por IBGE; a listagem filtra por `state_uf` e `active` sobre tabela pequena (milhares de linhas no máximo).
- OpenAPI (`api/openapi.yaml`), tag `locations`:
  - `GET /states` → `200 State[]`; `GET /cities` (query `uf` com `pattern ^[A-Za-z]{2}$`, `active` boolean default `true`, `page` min 0 default 0, `size` 1..100 default 20) → `200 CityPage`, `400 Problem`; `GET /cities/{uf}/{slug}` → `200 City`, `400/404 Problem`; `GET /cities/default` → `200 City`, `500 Problem`.
  - Schemas novos: `State {uf, name}`, `City {id, ibgeCode, uf, name, slug, timezone, latitude, longitude, active}` (lat/long `nullable`), `CityPage {items: City[], page: PageMetadata}`.
- Sem mudança em `pom.xml`: a execução única do gerador continua valendo; `LocationsApi`, `City`, `State` e `CityPage` saem em `com.culturalevents.shared.api[.model]`, e o controller em `locations.api` implementa `LocationsApi`.

**2. Camadas do pacote `locations`**
- `domain`: entidades JPA `State` e `City` (só anotações `jakarta.persistence`), e as portas `StateRepository` / `CityRepository` (interfaces simples; paginação com `Pageable`/`Page` do Spring Data, aceito como tipo utilitário).
- `application`: `LocationsService` (casos de uso de consulta, `@Transactional(readOnly = true)`), `DefaultCityResolver` (lê `app.default-city`) e exceções de domínio (`DefaultCityMisconfiguredException`).
- `infrastructure`: adaptadores que implementam as portas sobre interfaces Spring Data JPA.
- `api`: `LocationsController implements LocationsApi`, mapeamento entidade → DTO.
- Alternativa descartada: entidades JPA separadas do modelo de domínio (mapeamento duplo) — custo sem ganho para um catálogo somente leitura de 2 tabelas.

**3. Mapeamento JPA compatível com `validate`**
- `state.uf`/`city.state_uf` são `CHAR(2)` (bpchar): usar `@JdbcTypeCode(SqlTypes.CHAR)` + `@Column(length = 2)` para a validação passar; `id` com `GenerationType.IDENTITY`; `latitude`/`longitude` como `BigDecimal` com `precision = 9, scale = 6`. `City.stateUf` é uma coluna `String` simples (`@Column(name = "state_uf", length = 2)` + `@JdbcTypeCode(SqlTypes.CHAR)`), **sem** `@ManyToOne` para `State`: a integridade fica na FK do banco e o DTO usa `stateUf` direto, sem joins nem lazy loading.
- `GET /states` e as consultas devolvem entidades somente leitura (sem setters públicos, `@Immutable`).

**4. Normalização**: `uf` é persistida em maiúsculas (`SP`) e comparada em maiúsculas; `slug` em minúsculas. O valor `sp/sao-paulo` de `app.default-city` é normalizado ao resolver (`uf` → maiúsculas), sem mudar o formato já documentado. Listagem ordenada por `name, id` para paginação estável; `/states` ordenado por `name`.

**5. Cidade padrão resolvida sob demanda (não no startup)**
- `app.default-city` é lida a cada chamada por `DefaultCityResolver` (via `@Value`/properties). Falhas (formato inválido, inexistente, inativa) lançam `DefaultCityMisconfiguredException`, mapeada em `locations.api` por um `@RestControllerAdvice` próprio, de maior precedência, para HTTP 500 `Problem` com `detail` específico (sem dados sensíveis, sem stack). O handler genérico do shared devolveria detalhe genérico, por isso a exceção dedicada.
- Alternativa descartada: falhar na inicialização — a spec e o pedido definem 500 em runtime, e fail-fast impediria subir a aplicação para corrigir a cidade via SQL.

**6. 400 versus 422 para parâmetros inválidos**
- O contrato usa 400 para parâmetros de query/path inválidos (`uf`, `page`, `size`); 422 permanece para corpo com bean validation. Implementação: os parâmetros gerados carregam `@Pattern`/`@Min`/`@Max`; o handler compartilhado passa a tratar `HandlerMethodValidationException` e `ConstraintViolationException` originada de parâmetro de método como 400 `Problem` (com `detail` citando o parâmetro). Isso altera o comportamento atual de `ConstraintViolationException` (hoje 422) — o teste existente do handler deve ser ajustado/estendido para manter 422 apenas no caso de bean validation de corpo. Se a validação de método não for acionada pelo gerador, o controller valida `uf` explicitamente e lança exceção mapeada para 400.

**7. Teste sem literal de cidade**
- Os testes de integração usam o valor lido de `app.default-city` para a cidade da carga e inserem cidades próprias de fixture (nomes fictícios, ex.: `Cidade Teste`, UF real, IBGE fora da faixa real) para ativa/inativa/paginação. Teste de varredura (`NoHardcodedCityTest`) lê os `.java` de `src/main` e falha se achar nome, slug ou IBGE da cidade da carga; o `application.yml` e o SQL de seed são dados e ficam fora da varredura.

**8a. Infraestrutura de teste compartilhada**: uma classe base abstrata `AbstractPostgresIntegrationTest` (em `com.culturalevents`, `src/test`) mantém um único `PostgreSQLContainer` estático (singleton, iniciado uma vez num bloco `static`, sem `@Container`/`@Testcontainers` para não ser parado por classe), exposto com `@ServiceConnection`, e aplica `database/migrations` uma única vez nesse mesmo bloco. Todas as classes de integração (incluindo `PlatformFoundationIntegrationTest`, migrada) a estendem; o Ryuk encerra o container ao fim da JVM. Dados de fixture inseridos por um teste são removidos ao final dele (`@AfterEach` ou `@Sql(executionPhase = AFTER_TEST_METHOD)`), de modo que nenhum teste dependa de ordem nem de dados deixados por outro. Alternativa descartada: um container por classe — lento e repete as migrations. Consequência: classes com `@TestPropertySource`/propriedades diferentes criam contextos Spring distintos, mas reusam o mesmo container.

**8. Testes**: unitário do `LocationsService`/`DefaultCityResolver` (Mockito) e do mapper; integração com Testcontainers + MockMvc (`@SpringBootTest`, `@AutoConfigureMockMvc`, migrations aplicadas como no teste base) cobrindo cada endpoint e cada cenário de erro; testes de configuração da cidade padrão via `@TestPropertySource`/`DynamicPropertySource` por classe; ArchUnit existente passa a ter classes reais para validar.

## Risks / Trade-offs

- [`CHAR(2)` não casa com `String` no `validate`] → mapear com `@JdbcTypeCode(SqlTypes.CHAR)`; o teste "contexto sobe com V002" detecta regressão.
- [Mudar `ConstraintViolationException` de 422 para 400 pode quebrar o contrato implícito do handler] → restringir a 400 aos casos de parâmetro de método e manter 422 para beans; cobrir ambos em `GlobalExceptionHandlerTest`.
- [Interfaces/DTOs de `locations` geradas em `shared.api`] → consistente com a configuração atual e sem impacto no ArchUnit (`shared` não casa com o padrão de camadas); revisitar se futuras capabilities exigirem pacotes de API por capability.
- [Seed com uma só cidade presume SP no dado] → é dado, não regra; a regra consulta `app.default-city` e novas cidades entram por script.
- [Lookup de cidade padrão a cada chamada] → consulta indexada por unique, volume desprezível; cache fica fora do escopo.
- [`app.default-city` usa `uf` minúscula enquanto o banco guarda maiúscula] → normalização na resolução (Decisão 4).

## Migration Plan

1. Criar V002 e aplicar localmente (`docker compose down -v && docker compose up -d`, pois o `initdb.d` só roda em volume novo) ou aplicar o script manualmente num banco existente.
2. Publicar o código; V002 não é editado após o merge (correções entram como V003+).
3. Rollback: reverter o código e remover as tabelas manualmente (`DROP TABLE city, state`) em ambientes descartáveis; não há dependentes ainda.
