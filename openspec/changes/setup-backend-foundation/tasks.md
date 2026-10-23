# Tasks

## 1. Contrato OpenAPI compartilhado

- [ ] 1.1 Criar `api/openapi.yaml` (OpenAPI 3.1, `servers: [{ url: /api/v1 }]`) com `components.schemas.Problem` (type, title, status, detail, instance, errors[] com field/message) e `components.schemas.PageMetadata` (page, size, totalElements, totalPages), sem nenhum `paths`, e verificar que o arquivo valida como OpenAPI 3.1 (ex.: `npx @redocly/cli lint api/openapi.yaml` ou validador equivalente disponível)

## 2. Projeto Maven do backend

- [ ] 2.1 Gerar o esqueleto do projeto em `backend/` (groupId `com.culturalevents`, artifactId `backend`, parent `spring-boot-starter-parent` 4.1.x, Java 25) com Maven Wrapper (`mvnw`, `mvnw.cmd`, `.mvn/`) e verificar que `./mvnw -v` roda e reporta a versão do wrapper sem Maven instalado globalmente
- [ ] 2.2 Adicionar as dependências: `spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, driver PostgreSQL, `springdoc-openapi` (versão compatível com Boot 4), Testcontainers 2 (`testcontainers-postgresql`, `testcontainers-junit-jupiter`, versão gerenciada pelo BOM do Spring Boot) com `spring-boot-testcontainers`, ArchUnit, `jackson-databind-nullable` e `swagger-annotations` (exigidas pelo código gerado do OpenAPI), e verificar que `./mvnw dependency:resolve` baixa todas sem conflito
- [ ] 2.3 Configurar `maven-enforcer-plugin` com a regra `requireJavaVersion` e range `[25,)` na fase `validate`, e verificar que o build falha com uma mensagem clara do enforcer ao rodar com um JDK anterior à 25 (ex.: apontar `JAVA_HOME` temporariamente para um JDK mais antigo, rodar `./mvnw validate`, confirmar a falha, depois restaurar `JAVA_HOME`)
- [ ] 2.4 Configurar `openapi-generator-maven-plugin` lendo `../api/openapi.yaml`, gerador `spring`, `interfaceOnly=true`, `useTags=true`, namespace `jakarta` (`useSpringBoot3=true` ou a opção equivalente de Boot 4 disponível na versão do plugin), saída em `target/generated-sources`, e verificar que `./mvnw generate-sources` gera os DTOs de `Problem`/`PageMetadata` (usando `jakarta.*` e `JsonNullable`) em `target/generated-sources` sem erro
- [ ] 2.5 Verificar que `./mvnw clean verify` passa com o projeto vazio (sem testes de negócio ainda) antes de prosseguir

## 3. Configuração da aplicação, erros e health check

- [ ] 3.1 Criar `application.yml` com datasource via variáveis de ambiente com defaults batendo com o `docker-compose.yml` (ex.: `${DB_URL:jdbc:postgresql://localhost:5432/cultural_events}`, `${DB_USER:cultural_events}`, `${DB_PASSWORD:cultural_events}`), `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.open-in-view=false`, `spring.mvc.problemdetails.enabled=true` e `app.default-city=sp/sao-paulo`, e verificar que a aplicação sobe localmente sem exportar nenhuma variável de ambiente, usando só os defaults (com o banco da seção 4 já rodando)
- [ ] 3.2 Criar em `com.culturalevents.shared.error` as exceções compartilhadas (`NotFoundException` → 404, `ConflictException` → 409) e o `@RestControllerAdvice` (`GlobalExceptionHandler`) mapeando 400 (corpo malformado), 404, 409, 422 (erros por campo de `@Valid`/`ConstraintViolationException`) e 500 (sem stack trace) para `ProblemDetail`
- [ ] 3.3 Escrever testes unitários do `GlobalExceptionHandler` cobrindo os 5 status (400, 404, 409, 422, 500), verificando o corpo `Problem` retornado (incluindo a lista de erros por campo no 422) e a ausência de stack trace no 500, e verificar que `./mvnw test -Dtest=GlobalExceptionHandlerTest` passa
- [ ] 3.4 Criar em `com.culturalevents.shared.pagination` um helper que monta `PageMetadata` a partir de um `org.springframework.data.domain.Page<T>`, com teste unitário cobrindo página intermediária e última página, e verificar que o teste passa
- [ ] 3.5 Verificar que `/actuator/health` está exposto (`management.endpoints.web.exposure.include=health` se necessário) subindo a aplicação localmente e confirmando HTTP 200 com `status: UP` via `curl http://localhost:8080/actuator/health` (requer banco local da seção 4 disponível)

## 4. Banco de dados local

- [ ] 4.1 Criar `docker-compose.yml` na raiz com serviço `postgres:16`, variáveis de ambiente (usuário, senha, banco) com defaults locais, volume nomeado, e `./database/migrations` montado em `/docker-entrypoint-initdb.d`, e verificar que `docker compose config` valida o arquivo sem erro
- [ ] 4.2 Criar `database/migrations/V001__enable_extensions.sql` com `CREATE EXTENSION IF NOT EXISTS pg_trgm;` e `CREATE EXTENSION IF NOT EXISTS unaccent;`, e verificar, com `docker compose up -d` em um volume limpo seguido de `docker compose exec` + `psql -c "\dx"`, que ambas as extensions aparecem instaladas
- [ ] 4.3 Escrever um teste (unitário, ex. verificando nomes de arquivo via regex) que garante todo script em `database/migrations` segue o padrão `V###__descricao.sql` com prefixo numérico de 3 dígitos, e verificar que o teste passa para `V001__enable_extensions.sql` e falharia para um nome fora do padrão

## 5. Pacotes por capability e regra arquitetural

- [ ] 5.1 Documentar a convenção de pacotes por capability (`com.culturalevents.<capability>.{api,application,domain,infrastructure}`) no README ou javadoc do pacote raiz, e garantir que `com.culturalevents.shared` (com `error` e `pagination` da seção 3) já exista — sem criar nenhum pacote `com.culturalevents.<capability>.*` concreto nesta change
- [ ] 5.2 Escrever o teste ArchUnit (`ArchitectureTest`) expresso sobre o padrão de pacote `com.culturalevents.(*).{api,application,domain,infrastructure}` com `allowEmptyShould(true)`, garantindo a regra `api -> application -> domain <- infrastructure` (domain não depende de api/application/infrastructure; shared é exceção), e verificar que `./mvnw test -Dtest=ArchitectureTest` passa mesmo sem nenhuma classe de capability de negócio existir ainda
- [ ] 5.3 Adicionar classes de fixture em `src/test` (nunca em `src/main`) simulando uma violação da regra (ex.: uma classe de fixture em `.domain` dependendo de uma classe de fixture em `.infrastructure`), escrever um teste que roda a `ArchRule` contra esse fixture e comprove que ela falha, e verificar que esse teste de fixture passa (ou seja, confirma que a regra de fato rejeita a violação simulada)

## 6. Teste de integração base com Testcontainers

- [ ] 6.1 Escrever `PlatformFoundationIntegrationTest` com `@Testcontainers` + `PostgreSQLContainer` (postgres:16) anotado com `@ServiceConnection` (de `spring-boot-testcontainers`), aplicando os scripts de `database/migrations` contra o container (via execução sequencial dos arquivos SQL) e subindo o `ApplicationContext` completo (`@SpringBootTest`) com `ddl-auto=validate`, e verificar que `./mvnw test -Dtest=PlatformFoundationIntegrationTest` passa
- [ ] 6.2 Escrever um teste separado que aplica, contra um container Postgres isolado, um script SQL inválido colocado numa pasta de fixture de teste (ex.: `src/test/resources/invalid-migrations`, nunca em `database/migrations`), e verificar que a aplicação desse script falha de forma clara (exceção/erro JDBC propagado pelo teste), comprovando que o mecanismo de aplicação de scripts rejeita SQL inválido sem depender de corromper `V001__enable_extensions.sql`

## 7. Documentação

- [ ] 7.1 Atualizar `README.md` da raiz com instruções Windows/PowerShell: subir o banco (`docker compose up -d`), subir a API (`cd backend; ./mvnw spring-boot:run`), variáveis de ambiente necessárias e como rodar os testes (`./mvnw test`), e verificar seguindo o README do zero (ambiente limpo) até `/actuator/health` responder 200

## 8. Verificação final

- [ ] 8.1 Rodar `./mvnw clean verify` (inclui unitários + ArchUnit + integração com Testcontainers) e confirmar build verde de ponta a ponta
- [ ] 8.2 Rodar `openspec validate setup-backend-foundation --strict` e corrigir quaisquer avisos antes de solicitar revisão
