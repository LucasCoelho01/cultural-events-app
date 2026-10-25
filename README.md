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
  `database/migrations/` (por exemplo `V004__add_<cidade>.sql`, usando o próximo número livre) com um `INSERT INTO city (...)`;
  cidades entram com `active = false` a menos que `active` seja informado. Nunca edite um
  script já mergeado.
- **Cidade padrão:** vem da propriedade `app.default-city` (formato `uf/slug`, por exemplo
  `rj/rio-de-janeiro`), definida em `backend/src/main/resources/application.yml` e
  sobrescrevível pela variável de ambiente `APP_DEFAULT_CITY`. Se apontar para uma cidade
  inexistente, inativa ou em formato inválido, `GET /api/v1/cities/default` responde 500.
- **Aplicar a V002 num banco local já existente:** o `initdb.d` só roda em volume novo, então
  recrie o volume (`docker compose down -v` e `docker compose up -d`) ou execute o script
  manualmente.

## Categorias

A API expõe, somente leitura, `GET /api/v1/categories` (árvore: raízes com `children`) e
`GET /api/v1/categories/{slug}`. As categorias vivem na tabela `category`, criada por
`database/migrations/V003__create_categories.sql` com 6 raízes: Shows, Teatro, Exposições,
Dança, Cinema e Outros.

- **Cadastrar categoria ou subcategoria:** não há endpoint de escrita. Crie um novo script
  numerado em `database/migrations/` com `INSERT INTO category (parent_id, name, slug, sort_order)`;
  para uma subcategoria, informe o `parent_id` da raiz, por exemplo
  `(SELECT id FROM category WHERE slug = 'shows')`. Nunca edite um script já mergeado.
- **Limite de 2 níveis (categoria > subcategoria):** garantido por trigger no banco, contra
  qualquer escritor, inclusive scripts manuais. Uma inserção ou atualização que crie um terceiro
  nível falha com `check_violation` (SQLSTATE `23514`).
- **Agente de ingestão:** só lê `category` (lookup por `slug`) e usa `outros` como fallback
  quando nenhuma categoria corresponde. Não recebe privilégio de escrita na tabela.
- **Aplicar a V003 num banco local já existente:** recrie o volume
  (`docker compose down -v` e `docker compose up -d`) ou execute o script manualmente:

  ```powershell
  Get-Content -Raw database/migrations/V003__create_categories.sql | docker compose exec -T postgres psql -U cultural_events -d cultural_events -v ON_ERROR_STOP=1
  ```

## Locais (venues)

A API expõe o CRUD de locais por cidade: `POST /api/v1/venues`, `GET /api/v1/venues`
(`?city=uf/slug`, padrão `app.default-city`; `?neighborhood=`; `page`/`size`; ordenado por nome),
`GET`, `PUT` (substituição completa) e `DELETE /api/v1/venues/{id}`. O `id` é o `public_id` (UUID);
o `BIGSERIAL` interno nunca é exposto. A cidade (`cityId`, de `GET /api/v1/cities`) precisa existir
e estar ativa. Slug repetido na mesma cidade responde 409.

- **Regras no banco:** a tabela `venue` é criada por `database/migrations/V004__create_venues.sql`.
  Um trigger gera o `slug` a partir do `name` quando vier nulo (ou vazio) e outro atualiza
  `updated_at` a cada UPDATE. Valem para qualquer escritor, inclusive o agente de ingestão, que
  pode omitir `slug`, `public_id` e as datas.
- **Funções reutilizáveis:** `slugify(text)` (minúsculas, sem acentos, hífens) e `set_updated_at()`
  (trigger genérico) podem ser usadas por outras tabelas, como `event`:

  ```sql
  CREATE TRIGGER event_set_updated_at BEFORE UPDATE ON event
      FOR EACH ROW EXECUTE FUNCTION set_updated_at();
  ```
- **Aplicar a V004 num banco local já existente:** recrie o volume
  (`docker compose down -v` e `docker compose up -d`) ou execute o script manualmente:

  ```powershell
  Get-Content -Raw database/migrations/V004__create_venues.sql | docker compose exec -T postgres psql -U cultural_events -d cultural_events -v ON_ERROR_STOP=1
  ```

## Gerando código a partir do contrato OpenAPI

`api/openapi.yaml` é a fonte da verdade do contrato HTTP. Qualquer alteração nele (novo
path, novo schema) deve ser seguida de `./mvnw generate-sources` (ou qualquer build) em
`backend/`, que regenera as interfaces/DTOs em `target/generated-sources` — nenhum
código gerado é versionado no repositório.
