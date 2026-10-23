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

## Gerando código a partir do contrato OpenAPI

`api/openapi.yaml` é a fonte da verdade do contrato HTTP. Qualquer alteração nele (novo
path, novo schema) deve ser seguida de `./mvnw generate-sources` (ou qualquer build) em
`backend/`, que regenera as interfaces/DTOs em `target/generated-sources` — nenhum
código gerado é versionado no repositório.
