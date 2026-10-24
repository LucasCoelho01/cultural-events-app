# Cultural Events

Sistema de eventos culturais (shows, teatro, exposições). Monorepo contract-first:
`api/openapi.yaml` (contrato), `backend/` (Spring Boot), `database/migrations/` (schema SQL),
`frontend/` (fase 2) e `postman/` (teste manual).

## Pré-requisitos

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (para o Postgres local)
- JDK 25 (o `backend/` usa Maven Wrapper, então não é preciso instalar Maven)

## Subir o banco de dados (Windows/PowerShell)

```powershell
docker compose up -d
```

Isso inicia um Postgres 16 em `localhost:5432`, com usuário, senha e banco padrão
`cultural_events` (sobrescrevíveis pelas variáveis de ambiente `DB_USER`, `DB_PASSWORD`,
`DB_NAME` antes de rodar o comando acima). Na primeira inicialização (volume novo), os
scripts de `database/migrations/` são aplicados automaticamente.

Para parar o banco:

```powershell
docker compose down
```

Para apagar os dados e recomeçar do zero (reaplica todas as migrations):

```powershell
docker compose down -v
docker compose up -d
```

## Subir a API (Windows/PowerShell)

Com o banco já rodando:

```powershell
cd backend
./mvnw spring-boot:run
```

A API usa, por padrão, as mesmas credenciais do `docker-compose.yml`
(`jdbc:postgresql://localhost:5432/cultural_events`), então nenhuma variável de
ambiente precisa ser definida para rodar localmente. Para apontar para outro banco,
defina `DB_URL`, `DB_USER` e `DB_PASSWORD` antes de subir a API, por exemplo:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/outro_banco"
$env:DB_USER = "outro_usuario"
$env:DB_PASSWORD = "outra_senha"
cd backend
./mvnw spring-boot:run
```

Depois de subir, confira o health check:

```powershell
curl http://localhost:8080/actuator/health
```

## Rodar os testes

```powershell
cd backend
./mvnw test
```

Os testes de integração usam [Testcontainers](https://testcontainers.com/) e sobem seu
próprio container Postgres automaticamente — não dependem do `docker compose up -d`
acima, mas precisam do Docker Desktop em execução.

## Localidades (estados e cidades)

A API expõe, somente leitura, `GET /api/v1/states`, `GET /api/v1/cities`,
`GET /api/v1/cities/{uf}/{slug}` e `GET /api/v1/cities/default`. Estados e cidades vivem nas
tabelas `state` e `city`, criadas e populadas por `database/migrations/V002__create_locations.sql`
(27 UFs e uma cidade inicial ativa).

- **Cadastrar uma nova cidade:** não há endpoint de escrita. Crie um novo script numerado em
  `database/migrations/` (por exemplo `V003__add_<cidade>.sql`) com um `INSERT INTO city (...)`;
  cidades entram com `active = false` a menos que `active` seja informado. Nunca edite um
  script já mergeado.
- **Cidade padrão:** vem da propriedade `app.default-city` (formato `uf/slug`, por exemplo
  `rj/rio-de-janeiro`), definida em `backend/src/main/resources/application.yml` e
  sobrescrevível pela variável de ambiente `APP_DEFAULT_CITY`. Se apontar para uma cidade
  inexistente, inativa ou em formato inválido, `GET /api/v1/cities/default` responde 500.
- **Aplicar a V002 num banco local já existente:** o `initdb.d` só roda em volume novo, então
  recrie o volume (`docker compose down -v` e `docker compose up -d`) ou execute o script
  manualmente.

## Gerando código a partir do contrato OpenAPI

`api/openapi.yaml` é a fonte da verdade do contrato HTTP. Qualquer alteração nele (novo
path, novo schema) deve ser seguida de `./mvnw generate-sources` (ou qualquer build) em
`backend/`, que regenera as interfaces/DTOs em `target/generated-sources` — nenhum
código gerado é versionado no repositório.
