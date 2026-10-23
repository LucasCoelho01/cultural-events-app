# Design

## Context

Monorepo vazio de back-end: ver `proposal.md` - Why. Convenções já fixadas no `openspec/config.yaml` do projeto (não repetidas aqui): contract-first com `api/openapi.yaml`, migrations SQL numeradas sem Flyway, `ddl-auto=validate`, Testcontainers para integração, Postman para teste manual, domínio em português / código em inglês, Windows + PowerShell.

Esta é a primeira change de código do repositório — não há projeto Maven, contrato OpenAPI, docker-compose nem pacotes Java ainda. Todas as decisões abaixo partem de zero.

## Goals / Non-Goals

**Goals:**
- Projeto backend que compila, testa e sobe localmente, sem nenhuma entidade/endpoint de negócio.
- Convenções (pacotes por capability, erros RFC 7807, paginação, migrations) estabelecidas de um jeito que a próxima capability só precise segui-las, não reinventá-las.
- Pipeline contract-first funcionando ponta a ponta (OpenAPI → código gerado) mesmo sem nenhum path de negócio no contrato.

**Non-Goals:**
- Qualquer entidade de domínio, endpoint de negócio ou regra de eventos culturais.
- CI/CD, autenticação/autorização, deploy em produção.
- Role `agent_writer` do agente de ingestão (fase 3) — fora de escopo aqui; apenas as extensions de schema (`pg_trgm`, `unaccent`) são habilitadas por serem base para buscas textuais futuras.
- Flyway ou outra ferramenta de migration automatizada — a convenção do projeto é scripts SQL aplicados via `docker-entrypoint-initdb.d` em dev, sem ferramenta de migration em runtime.

## Decisions

### 1. Estrutura do módulo Maven
Único módulo `backend/` (não multi-módulo). Não há necessidade de separar módulos por capability neste estágio — a separação de responsabilidade vem da estrutura de pacotes Java (`com.culturalevents.<capability>.*`), não de módulos Maven. Multi-módulo adicionaria complexidade de build sem benefício enquanto há uma única capability.

`pom.xml` usa Spring Boot 4.1.x como parent (`spring-boot-starter-parent`), Java 25 via `<java.version>`/`maven.compiler.release`. Maven Wrapper (`./mvnw`, `mvnw.cmd`) gerado via `mvn wrapper:wrapper` para não depender de Maven instalado na máquina do desenvolvedor.

Dependências usam os artifact ids do Spring Boot 4: o starter web é `spring-boot-starter-webmvc` (renomeado a partir do antigo `spring-boot-starter-web` no Boot 4), com os starters de teste correspondentes. Testcontainers usa a versão 2 (`testcontainers-postgresql`, `testcontainers-junit-jupiter`), com a versão gerenciada pelo BOM do Spring Boot — sem `<version>` declarada manualmente — e `spring-boot-testcontainers` fornece `@ServiceConnection` para o teste de integração (decisão 6).

Para garantir a versão mínima de Java em qualquer máquina de build, `maven-enforcer-plugin` roda a regra `requireJavaVersion` com range `[25,)` na fase `validate`, falhando o build antes de qualquer compilação quando o JDK ativo for anterior à 25. Esse plugin é o mecanismo concreto atrás do cenário "Build falha com versão de Java incompatível" do spec — sem ele, uma versão de Java divergente só falharia de forma indireta (erro de compilação ou de bytecode), não com uma mensagem clara de pré-condição.

### 2. Geração de código contract-first
`openapi-generator-maven-plugin` roda na fase `generate-sources`, lendo `../api/openapi.yaml` (fora de `backend/`, na raiz, compartilhado futuramente com o front-end/Postman). Gerador `spring`, `interfaceOnly=true` (gera apenas interfaces de controller + DTOs, implementação fica em classes `@RestController` escritas à mão que implementam a interface), `useTags=true` (agrupamento por tag do OpenAPI vira agrupamento por capability). O gerador é configurado para o namespace `jakarta.*` do Spring Boot 4 (`useSpringBoot3=true`, ou a opção equivalente para Boot 4 se a versão do plugin disponível já expuser uma — confirmar na implementação qual flag está disponível) em vez do `javax.*` legado. O código gerado exige `jackson-databind-nullable` (tipo `JsonNullable` usado pelos DTOs) e `swagger-annotations` (anotações `@Schema` nos modelos) no classpath de `backend/`. Saída em `target/generated-sources/openapi` — não versionada (`.gitignore` já cobre `target/`).

Como `api/openapi.yaml` nesta change só tem `components` (Problem, PageMetadata) e nenhum `path`, o plugin roda mas não gera nenhuma interface de controller ainda — apenas os DTOs/schemas compartilhados. Isso é esperado e valida que o pipeline de geração funciona antes de qualquer path de negócio existir.

**Alternativa considerada**: gerar client/DTOs manualmente (sem codegen). Rejeitada porque o projeto já define contract-first com openapi-generator como convenção (`openspec/config.yaml`), e manter o contrato como única fonte de verdade evita DTOs divergindo do schema documentado.

### 3. Tratamento de erros: ProblemDetail + exceções compartilhadas
Em vez de cada capability futura definir suas próprias exceções e handlers, este change define em `com.culturalevents.shared.error`:
- Um pequeno conjunto de exceções de domínio compartilhadas (ex.: `NotFoundException` → 404, `ConflictException` → 409) que qualquer capability pode lançar.
- Um `@RestControllerAdvice` (`GlobalExceptionHandler`) que mapeia essas exceções, mais `MethodArgumentNotValidException`/`ConstraintViolationException` (422, com lista de erros por campo), `HttpMessageNotReadableException` (400) e `Exception` genérica (500, sem stack trace), para `ProblemDetail` (usando o suporte nativo do Spring 6/Boot 4, habilitado via `spring.mvc.problemdetails.enabled=true`).
- Os campos de erro por campo no 422 usam a mesma estrutura declarada no schema `Problem` do OpenAPI, para o corpo de erro HTTP real coincidir com o contrato documentado.

**Alternativa considerada**: deixar cada capability implementar seu próprio tratamento de erro. Rejeitada — geraria formatos de erro inconsistentes entre capabilities, violando o contrato HTTP único declarado em `api/openapi.yaml`.

### 4. Convenção de pacotes e ArchUnit
Pacotes por capability: `com.culturalevents.<capability>.api` (controllers), `.application` (casos de uso/serviços), `.domain` (entidades/regras), `.infrastructure` (repositórios JPA, adapters). Mais `com.culturalevents.shared` para `error` (exceções + advice) e `pagination` (helper para montar `PageMetadata` a partir de um `Page<T>` do Spring Data).

Regra verificada via ArchUnit (`ArchitectureTest`), expressa sobre o padrão de pacote `com.culturalevents.(*).{api,application,domain,infrastructure}` — qualquer capability presente ou futura que siga essa convenção, não uma capability nomeada: `api` pode depender de `application`; `application` pode depender de `domain`; `infrastructure` pode depender de `domain`; `domain` não depende de nenhum dos outros três. `shared` é exceção à regra (qualquer camada pode depender de `shared`).

Esta change não cria nenhum pacote de capability de negócio concreto (nenhum `com.culturalevents.<capability>.*` com classe real) — só `com.culturalevents.shared` tem classes de verdade (o `GlobalExceptionHandler` e o helper de paginação da decisão 3). Por isso a `ArchRule` é configurada com `allowEmptyShould(true)`, para não falhar quando nenhuma classe casa com o padrão ainda. A prova de que a regra de fato detecta uma violação usa classes de fixture declaradas em `src/test` (um pacote de teste simulando, por exemplo, uma classe de `domain` dependendo de uma classe de `infrastructure`), nunca em `src/main` — assim nenhuma capability fictícia é criada em código de produção só para o teste ter o que verificar.

**Alternativa considerada**: aplicar a regra arquitetural apenas por convenção/code review, sem ArchUnit. Rejeitada — regra automatizada evita regressão silenciosa à medida que novas capabilities forem adicionadas por diferentes contribuidores.

### 5. Banco local: Docker Compose + scripts SQL numerados (sem Flyway)
`docker-compose.yml` na raiz do monorepo (não em `backend/`), porque o banco é infraestrutura compartilhada do monorepo, não um detalhe do módulo Spring. Serviço único `postgres:16`, variáveis de ambiente com default local (`POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`), volume nomeado (`postgres_data`) para persistir entre `docker compose down`/`up`, e bind mount `./database/migrations:/docker-entrypoint-initdb.d:ro` — o Postgres oficial executa automaticamente, em ordem alfabética, qualquer `.sql` nesse diretório **apenas na primeira inicialização do volume** (comportamento nativo da imagem `postgres`, não escrito por nós).

Consequência direta: migrations só reaplicam quando o volume é removido (`docker compose down -v`). Isso é aceitável em dev porque o projeto não usa uma ferramenta de migration incremental (Flyway) — a convenção documentada é: scripts nunca editados após merge, e qualquer ambiente novo roda a sequência completa do zero. O teste de integração com Testcontainers (decisão 6) é o que garante, no CI/local, que a sequência completa de scripts continua válida mesmo sem reaplicar num volume já existente.

`V001__enable_extensions.sql` habilita `pg_trgm` e `unaccent` — ambas extensions nativas do Postgres (`CREATE EXTENSION IF NOT EXISTS ...`), sem dependência externa. São habilitadas agora porque busca textual (nome de evento/local) é uma necessidade conhecida das capabilities futuras, e extensions exigem privilégio de superuser mais cedo é mais simples que adicionar depois.

**Alternativa considerada**: Flyway/Liquibase. Rejeitada explicitamente pela convenção do projeto (`openspec/config.yaml`: "sem Flyway"); `ddl-auto=validate` + scripts numerados versionados é o mecanismo escolhido.

Os defaults de `application.yml` para o datasource (seção 7) usam exatamente os mesmos valores default de usuário/senha/banco declarados aqui no `docker-compose.yml`, para que `docker compose up -d` seguido de `./mvnw spring-boot:run` funcione sem nenhuma variável de ambiente adicional no ambiente local padrão.

### 6. Teste de integração base com Testcontainers
Um teste (`PlatformFoundationIntegrationTest` ou nome equivalente) usa `@Testcontainers` com um `PostgreSQLContainer` anotado com `@ServiceConnection` (de `spring-boot-testcontainers`), que configura automaticamente o datasource do `ApplicationContext` para apontar para o container — sem precisar de um `@DynamicPropertySource` manual. O container é inicializado programaticamente aplicando os scripts de `database/migrations` (via execução sequencial dos arquivos SQL, já que não há Flyway para orquestrar isso) antes de o `ApplicationContext` Spring completo (`@SpringBootTest`) subir com `ddl-auto=validate`. Isso prova duas coisas ao mesmo tempo: (a) os scripts SQL continuam aplicáveis do zero, e (b) não há nenhuma entidade JPA mapeada sem schema correspondente (trivialmente verdadeiro agora, pois não há entidades — o teste serve de andaime para capabilities futuras).

**Alternativa considerada**: usar apenas H2 em memória para testes. Rejeitada — o projeto já decide usar Postgres real via Testcontainers (`openspec/config.yaml`), pois `pg_trgm`/`unaccent` e tipos específicos do Postgres não existem em H2.

### 7. Configuração de datasource e cidade padrão
`application.yml` lê o datasource via variáveis de ambiente com defaults que batem com os defaults do `docker-compose.yml` (decisão 5): `${DB_URL:jdbc:postgresql://localhost:5432/cultural_events}`, `${DB_USER:cultural_events}`, `${DB_PASSWORD:cultural_events}` (nomes exatos de variável e valores default a confirmar na implementação, mantendo-os idênticos aos do `docker-compose.yml`). Isso permite rodar `docker compose up -d` e `./mvnw spring-boot:run` em sequência, sem exportar nenhuma variável, no ambiente local padrão — não há mais um cenário de "falha ao subir sem variáveis", porque sempre há um default válido para desenvolvimento local; apenas um ambiente que sobrescreve as variáveis com um valor de datasource inválido falharia (caso coberto por qualquer erro de conexão JDBC, não é um comportamento específico desta fundação).

`app.default-city=sp/sao-paulo` usa o formato `uf/cidade` (não apenas o nome livre da cidade), prevendo que o mesmo formato sirva de chave em capabilities futuras que expandirem para outras cidades — mas, como o proposal reforça, nenhuma regra pode assumir que `sp/sao-paulo` é a única cidade suportada.

## Novos scripts SQL

- `database/migrations/V001__enable_extensions.sql` — `CREATE EXTENSION IF NOT EXISTS pg_trgm;` e `CREATE EXTENSION IF NOT EXISTS unaccent;`.

## Mudanças no OpenAPI

- Novo arquivo `api/openapi.yaml`, OpenAPI 3.1, `servers: [{ url: /api/v1 }]`.
- `components.schemas.Problem`: `type`, `title`, `status`, `detail`, `instance` (RFC 7807) + `errors` (lista de `{ field, message }`) usada quando `status == 422`.
- `components.schemas.PageMetadata`: `page`, `size`, `totalElements`, `totalPages`.
- Nenhum `paths` de negócio declarado nesta change.

## Risks / Trade-offs

- [Risco] Scripts de `database/migrations` só são reaplicados quando o volume Docker é removido, o que pode mascarar erros de um script novo em ambientes de dev já inicializados → Mitigação: o teste de integração com Testcontainers sempre inicializa um banco do zero, pegando esse tipo de erro antes do merge, independente do estado do volume local do desenvolvedor.
- [Risco] `interfaceOnly=true` gera só a interface — se um desenvolvedor esquecer de implementar um método novo depois de alterar o contrato, o erro só aparece em tempo de compilação (interface não implementada), não é um risco de runtime, mas pode ser confuso na primeira vez → Mitigação: documentar esse fluxo no README (alterar `api/openapi.yaml` → rodar build → implementar o(s) método(s) novo(s) da interface gerada).
- [Risco] Nenhuma entidade JPA e nenhum pacote de capability de negócio existe ainda, então o teste ArchUnit roda com `allowEmptyShould(true)` e o teste de integração com Testcontainers desta change fica "vazio" (verifica uma estrutura sem conteúdo de negócio) → Mitigação: aceitável — o objetivo explícito desta change é a fundação; a próxima capability de negócio passa a alimentar o padrão de pacote que o ArchUnit já verifica e reaproveita o teste de integração como andaime.
- [Trade-off] Escolher um único módulo Maven em vez de multi-módulo por capability simplifica o build agora, mas pode exigir refatoração se o projeto crescer muito → aceito conscientemente; reavaliar apenas se o tempo de build ou o acoplamento entre capabilities se tornar um problema real.

## Migration Plan

Não aplicável como "migração" no sentido de sistema em produção — é a criação inicial do backend. Ordem de criação dentro da change (ver `tasks.md`): contrato OpenAPI mínimo → projeto Maven → configuração/erros/health → docker-compose + migration SQL → pacotes por capability + ArchUnit → teste de integração com Testcontainers → README. Não há rollback necessário: se algo falhar, a change inteira ainda não foi mesclada.
