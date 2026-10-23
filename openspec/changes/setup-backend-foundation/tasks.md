# Tasks

## 1. Contrato OpenAPI compartilhado

- [ ] 1.1 Criar `api/openapi.yaml` (OpenAPI 3.1, `servers: [{ url: /api/v1 }]`) com `components.schemas.Problem` (type, title, status, detail, instance, errors[] com field/message) e `components.schemas.PageMetadata` (page, size, totalElements, totalPages), sem nenhum `paths`, e verificar que o arquivo valida como OpenAPI 3.1 (ex.: `npx @redocly/cli lint api/openapi.yaml` ou validador equivalente disponível)

## 2. Projeto Maven do backend

- [ ] 2.1 Gerar o esqueleto do projeto em `backend/` (groupId `com.culturalevents`, artifactId `backend`, parent `spring-boot-starter-parent` 4.1.x, Java 25) com Maven Wrapper (`mvnw`, `mvnw.cmd`, `.mvn/`) e verificar que `./mvnw -v` roda e reporta a versão do wrapper sem Maven instalado globalmente
- [ ] 2.2 Adicionar as dependências: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, driver PostgreSQL, `springdoc-openapi` (versão compatível com Boot 4), Testcontainers (`postgresql`, `junit-jupiter`) e ArchUnit, e verificar que `./mvnw dependency:resolve` baixa todas sem conflito
- [ ] 2.3 Configurar `openapi-generator-maven-plugin` lendo `../api/openapi.yaml`, gerador `spring`, `interfaceOnly=true`, `useTags=true`, saída em `target/generated-sources`, e verificar que `./mvnw generate-sources` gera os DTOs de `Problem`/`PageMetadata` em `target/generated-sources` sem erro
- [ ] 2.4 Verificar que `./mvnw clean verify` passa com o projeto vazio (sem testes de negócio ainda) antes de prosseguir

## 3. Configuração da aplicação, erros e health check

- [ ] 3.1 Criar `application.yml` com datasource via variáveis de ambiente, `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.open-in-view=false`, `spring.mvc.problemdetails.enabled=true` e `app.default-city=sp`, e verificar que a aplicação falha ao subir sem as variáveis de datasource obrigatórias (erro claro de configuração, não stack trace genérico)
- [ ] 3.2 Criar em `com.culturalevents.shared.error` as exceções compartilhadas (`NotFoundException` → 404, `ConflictException` → 409) e o `@RestControllerAdvice` (`GlobalExceptionHandler`) mapeando 400 (corpo malformado), 404, 409, 422 (erros por campo de `@Valid`/`ConstraintViolationException`) e 500 (sem stack trace) para `ProblemDetail`
- [ ] 3.3 Escrever testes unitários do `GlobalExceptionHandler` cobrindo os 5 status (400, 404, 409, 422, 500), verificando o corpo `Problem` retornado (incluindo a lista de erros por campo no 422) e a ausência de stack trace no 500, e verificar que `./mvnw test -Dtest=GlobalExceptionHandlerTest` passa
- [ ] 3.4 Criar em `com.culturalevents.shared.pagination` um helper que monta `PageMetadata` a partir de um `org.springframework.data.domain.Page<T>`, com teste unitário cobrindo página intermediária e última página, e verificar que o teste passa
- [ ] 3.5 Verificar que `/actuator/health` está exposto (`management.endpoints.web.exposure.include=health` se necessário) subindo a aplicação localmente e confirmando HTTP 200 com `status: UP` via `curl http://localhost:8080/actuator/health` (requer banco local da seção 4 disponível)

## 4. Banco de dados local

- [ ] 4.1 Criar `docker-compose.yml` na raiz com serviço `postgres:16`, variáveis de ambiente (usuário, senha, banco) com defaults locais, volume nomeado, e `./database/migrations` montado em `/docker-entrypoint-initdb.d`, e verificar que `docker compose config` valida o arquivo sem erro
- [ ] 4.2 Criar `database/migrations/V001__enable_extensions.sql` com `CREATE EXTENSION IF NOT EXISTS pg_trgm;` e `CREATE EXTENSION IF NOT EXISTS unaccent;`, e verificar, com `docker compose up -d` em um volume limpo seguido de `docker compose exec` + `psql -c "\dx"`, que ambas as extensions aparecem instaladas
- [ ] 4.3 Escrever um teste (unitário, ex. verificando nomes de arquivo via regex) que garante todo script em `database/migrations` segue o padrão `V###__descricao.sql` com prefixo numérico de 3 dígitos, e verificar que o teste passa para `V001__enable_extensions.sql` e falharia para um nome fora do padrão

## 5. Pacotes por capability e regra arquitetural

- [ ] 5.1 Criar a estrutura de pacotes `com.culturalevents.platformfoundation.{api,application,domain,infrastructure}` (com um tipo mínimo em cada, suficiente para o ArchUnit ter o que verificar) e `com.culturalevents.shared` (contendo `error` e `pagination` da seção 3), documentando a convenção em comentário/README do pacote raiz se útil
- [ ] 5.2 Escrever o teste ArchUnit (`ArchitectureTest`) garantindo a regra `api -> application -> domain <- infrastructure` (domain não depende de api/application/infrastructure; shared é exceção), incluindo um caso que comprove que o teste de fato falha ao detectar uma violação (ex.: regra testada contra um fixture de violação, ou teste do próprio `ArchRule` com classes de exemplo), e verificar que `./mvnw test -Dtest=ArchitectureTest` passa

## 6. Teste de integração base com Testcontainers

- [ ] 6.1 Escrever `PlatformFoundationIntegrationTest` com `@Testcontainers` + `PostgreSQLContainer` (postgres:16), aplicando os scripts de `database/migrations` contra o container (via execução sequencial dos arquivos SQL) e subindo o `ApplicationContext` completo (`@SpringBootTest`) com `ddl-auto=validate` apontando para o container, e verificar que `./mvnw test -Dtest=PlatformFoundationIntegrationTest` passa
- [ ] 6.2 Verificar que o teste falha de forma clara se um script de `database/migrations` for removido/corrompido (teste manual: renomear temporariamente o script, rodar o teste, confirmar falha de inicialização do contexto por `ddl-auto=validate`, depois restaurar o script)

## 7. Documentação

- [ ] 7.1 Atualizar `README.md` da raiz com instruções Windows/PowerShell: subir o banco (`docker compose up -d`), subir a API (`cd backend; ./mvnw spring-boot:run`), variáveis de ambiente necessárias e como rodar os testes (`./mvnw test`), e verificar seguindo o README do zero (ambiente limpo) até `/actuator/health` responder 200

## 8. Verificação final

- [ ] 8.1 Rodar `./mvnw clean verify` (inclui unitários + ArchUnit + integração com Testcontainers) e confirmar build verde de ponta a ponta
- [ ] 8.2 Rodar `openspec validate setup-backend-foundation --strict` e corrigir quaisquer avisos antes de solicitar revisão
