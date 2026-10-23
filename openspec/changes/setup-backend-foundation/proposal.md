# Proposal

## Why

O projeto ainda não tem nenhum código de back-end: não existe módulo Maven, não existe contrato OpenAPI, não existe banco de dados local nem convenção de migrations, e não existe estrutura de pacotes por capability. Antes de implementar qualquer regra de negócio (eventos, locais, categorias etc.), é preciso ter a fundação técnica pronta: projeto Spring Boot buildável, banco local via Docker, esqueleto de contrato OpenAPI, tratamento padronizado de erros, health check e a convenção arquitetural (pacotes por capability + ArchUnit) que as próximas capabilities vão seguir. Sem essa fundação, cada capability futura reimplementaria (de forma inconsistente) paginação, erros RFC 7807 e setup de testes de integração.

## What Changes

- Cria o projeto Maven em `backend/` com Maven Wrapper, Java 25 e Spring Boot 4.1.x (groupId `com.culturalevents`, artifactId `backend`).
- Adiciona dependências: `spring-boot-starter-webmvc`, Spring Data JPA, Validation, Actuator, driver PostgreSQL, springdoc-openapi (compatível com Boot 4), Testcontainers 2 (`testcontainers-postgresql`, `testcontainers-junit-jupiter`, com versão gerenciada pelo Spring Boot) mais `spring-boot-testcontainers` para `@ServiceConnection`, ArchUnit, e `maven-enforcer-plugin` exigindo Java 25+.
- Cria o esqueleto contract-first em `api/openapi.yaml` (OpenAPI 3.1, servidor `/api/v1`) contendo apenas os componentes compartilhados `Problem` (RFC 7807, com lista de erros por campo para 422) e `PageMetadata` (page, size, totalElements, totalPages) — sem nenhum path/endpoint de negócio ainda.
- Configura o `openapi-generator-maven-plugin` no build do `backend/` lendo `../api/openapi.yaml`, gerador `spring`, `interfaceOnly=true`, `useTags=true`, namespace `jakarta` (compatível com Boot 4), saída em `target/generated-sources`.
- Cria `docker-compose.yml` na raiz com `postgres:16`, usuário/senha/banco via variáveis de ambiente (com valores padrão para uso local), volume nomeado para persistência, e `./database/migrations` montado em `/docker-entrypoint-initdb.d`.
- Cria `database/migrations/V001__enable_extensions.sql` habilitando as extensions `pg_trgm` e `unaccent`, e documenta a convenção de scripts numerados com 3 dígitos, nunca editados após o merge.
- Cria `application.yml` com datasource configurado por variáveis de ambiente com defaults batendo com o `docker-compose.yml` local, `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.open-in-view=false` e `app.default-city=sp/sao-paulo` (formato `uf/cidade`), deixando explícito que é apenas o padrão atual e não uma premissa de cidade única.
- Habilita `ProblemDetail` e adiciona um `@RestControllerAdvice` global tratando 400, 404, 409 e 422 (com lista de erros por campo) e 500 (sem expor stack trace).
- Expõe `/actuator/health`.
- Define a convenção de pacotes por capability (`com.culturalevents.<capability>.{api,application,domain,infrastructure}`) mais um pacote `shared/` para erros e paginação, e adiciona um teste ArchUnit (expresso sobre o padrão de pacote, com `allowEmptyShould(true)`) garantindo a regra de dependência `api -> application -> domain <- infrastructure` — sem criar nenhum pacote de capability de negócio concreto nesta change; a prova de que a regra detecta violação usa fixtures em `src/test`.
- Adiciona um teste de integração base com Testcontainers que sobe um container Postgres, aplica os scripts de `database/migrations` e inicializa o contexto Spring com `ddl-auto=validate` (comprovando que schema e entidades — ainda nenhuma — são compatíveis).
- Atualiza o `README.md` da raiz com instruções (Windows/PowerShell) para subir o banco (`docker compose up -d`) e a API (`./mvnw spring-boot:run`).

Nenhuma regra de negócio, entidade de domínio ou endpoint funcional é adicionado nesta change — é puramente fundação técnica.

## Capabilities

### New Capabilities
- `platform-foundation`: fundação técnica do back-end compartilhada por todas as capabilities futuras — estrutura do projeto Spring Boot, geração de código a partir do OpenAPI, convenção de migrations SQL, tratamento global de erros (RFC 7807), paginação (PageMetadata), health check, convenção de pacotes por capability e a regra arquitetural verificada via ArchUnit.

### Modified Capabilities
(nenhuma — este é o primeiro código do back-end, não há capabilities existentes)

## Impact

- **Novo código**: módulo Maven `backend/` (projeto Spring Boot novo).
- **Novo contrato**: `api/openapi.yaml` (apenas componentes compartilhados `Problem` e `PageMetadata`; nenhum path de negócio).
- **Novo schema**: `database/migrations/V001__enable_extensions.sql` (extensions `pg_trgm`, `unaccent` — nenhuma tabela ainda).
- **Infraestrutura local**: `docker-compose.yml` na raiz do monorepo.
- **Dependências**: Spring Boot 4.1.x, Spring Data JPA, Validation, Actuator, driver PostgreSQL, springdoc-openapi, Testcontainers 2 + `spring-boot-testcontainers`, ArchUnit, `openapi-generator-maven-plugin` (com `jackson-databind-nullable` e `swagger-annotations` para o código gerado), `maven-enforcer-plugin`.
- **Contrato com o agente de ingestão (fase 3)**: nenhum impacto direto ainda — a extension `unaccent`/`pg_trgm` habilitada antecipa necessidades de busca textual, mas nenhuma tabela de domínio é criada, então o contrato de schema com o `agent_writer` não existe nesta change.
- **Documentação**: `README.md` atualizado com passos de setup local.
