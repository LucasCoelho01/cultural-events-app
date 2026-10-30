# Contrato do banco com o agente de ingestão

**Versão do contrato: `1.0`** (`schema_contract.version`). Vale para o agente de ingestão (Python, repo separado), que
grava direto no Postgres com a role `agent_writer` e publica eventos sem revisão humana. O schema é o contrato: o que
não está aqui não é garantido. Os SQLs deste documento são exercitados por `AgentWriterRoleIntegrationTest`.

## 1. Conexão e permissões

- Role `agent_writer` (`V008__create_agent_writer_role.sql`); a senha é definida à mão (`ALTER ROLE agent_writer PASSWORD '...'`, ver README).
- **Lê** todas as tabelas. **Escreve** (`INSERT`, `UPDATE`, `DELETE`) só em: `event`, `occurrence`, `venue`, `source`, `event_source_link`, `ingestion_run`.
- **Nunca escreve** em `category`, `city`, `state` e `schema_contract` (negado pelo banco, SQLSTATE `42501`) e não faz DDL.
- Tabela nova só é escrita pelo agente se a migration que a cria conceder o acesso (`GRANT`). Não há `ALTER DEFAULT PRIVILEGES`.
- `category` e `city` são só lookup: categoria por `slug` (fallback `outros`), cidade por `state_uf` + `slug`.

## 2. Checagem de versão na partida

Antes de qualquer escrita:

```sql
SELECT version FROM schema_contract
ORDER BY string_to_array(version, '.')::int[] DESC LIMIT 1;
```

Compare a **versão principal** (MAJOR) com a que o agente suporta. Se for **diferente**, o agente **não grava nada** e encerra
com erro. MINOR maior é compatível (só adiciona colunas/tabelas opcionais); o agente ignora o que não conhece.
Mudança incompatível sobe o MAJOR.

## 3. Registro da execução (`ingestion_run`)

Uma linha por execução e por fonte:

```sql
INSERT INTO ingestion_run (source_id) VALUES (:source_id) RETURNING id;   -- status RUNNING
```

Ao terminar, uma única vez:

```sql
UPDATE ingestion_run
   SET status = :status,          -- SUCCESS (nenhuma falha), PARTIAL (algumas) ou FAILED (execução abortada)
       finished_at = now(),
       created = :created, updated = :updated, unchanged = :unchanged, failed = :failed,
       errors = :errors::jsonb     -- lista de {"external_id": "...", "message": "..."}
 WHERE id = :run_id;
```

Um item que falha entra em `errors` e em `failed`; a execução segue com o próximo item.

## 4. Idempotência: `(source_id, external_id)` e `content_hash`

`event_source_link` liga o item da fonte ao evento, com `UNIQUE (source_id, external_id)`. Para cada item:

1. Calcule `content_hash` (hash estável dos campos que o agente grava, incluindo as sessões).
2. Procure o vínculo, travando a linha:

```sql
SELECT id, event_id, content_hash FROM event_source_link
 WHERE source_id = :source_id AND external_id = :external_id
 FOR UPDATE;
```

| Situação | Ação |
|---|---|
| Sem vínculo | **Cria** o evento e o vínculo (seção 5). Conta em `created`. |
| Vínculo com `event_id` nulo | **Lápide** (seção 6): não cria nada. Conta em `unchanged`. |
| `content_hash` igual | Só `UPDATE event_source_link SET last_seen_at = now(), last_run_id = :run_id`. Conta em `unchanged`. |
| `content_hash` diferente | **Atualiza** o evento (seção 7). Conta em `updated`. |

Nunca insira um vínculo sem tratar o conflito: `UNIQUE (source_id, external_id)` rejeita o duplicado com `23505`
(`event_source_link_source_external_key`).

## 5. Uma transação por evento; criação

Cada item é uma transação própria (`BEGIN` … `COMMIT`); se qualquer passo falhar, `ROLLBACK` do item inteiro e siga
para o próximo. Nunca uma transação para a execução toda. Criação:

```sql
-- local (opcional), da mesma cidade do evento; o slug sai do nome quando omitido
INSERT INTO venue (city_id, name, slug) VALUES (:city_id, :venue_name, NULL)
ON CONFLICT (city_id, slug) DO UPDATE SET name = venue.name RETURNING id;

INSERT INTO event (city_id, category_id, venue_id, title, slug, description, status, origin,
                   age_rating, price_min_cents, price_max_cents, is_free, ticket_url, image_url)
VALUES (:city_id, :category_id, :venue_id, :title, NULL, :description, 'PUBLISHED', 'AGENT',
        :age_rating, :price_min, :price_max, :is_free, :ticket_url, :image_url)
RETURNING id;

INSERT INTO occurrence (event_id, starts_at, ends_at) VALUES (:event_id, :starts_at, :ends_at);  -- uma por sessão

INSERT INTO event_source_link (event_id, source_id, external_id, external_url, content_hash, last_run_id)
VALUES (:event_id, :source_id, :external_id, :external_url, :content_hash, :run_id);
```

Regras:

- `origin = 'AGENT'` e `status = 'PUBLISHED'` (publicação direta, sem revisão). O banco dispensa `slug`, `public_id`,
  `created_at`, `updated_at` e `search_vector`.
- **Só cidades ativas:** `city_id` de uma cidade com `city.active = true`. Item de cidade inativa ou desconhecida é ignorado
  (não é erro; conta em `unchanged`). A cidade vem do item ou de `source.city_id` (nulo = fonte nacional); nunca presuma uma cidade.
- Evento publicado precisa de **ao menos uma sessão futura**; sem ela, não publique (ignore o item).
- Respeite as invariantes do banco: preço máximo ≥ mínimo, evento gratuito sem preço positivo, `ends_at ≥ starts_at`,
  local da mesma cidade do evento. Violação (`23514`) é falha do item.
- Colisão de slug na cidade (`event_city_slug_key`, `23505`): deduplicação por fonte já foi feita pelo vínculo; trate como falha do item ou gere um slug explícito diferente.

## 6. Lápides: evento excluído à mão não volta

Excluir um evento (pela API ou diretamente) **não apaga o vínculo**: a FK é `ON DELETE SET NULL`, então
`event_source_link.event_id` fica **nulo** e o par `(source_id, external_id)` permanece. Esse vínculo é uma **lápide**:
o agente **não** recria o evento nem o vínculo para aquele par, em nenhuma execução futura. (O `UNIQUE` também impede
um segundo vínculo para o mesmo par.) Se o conteúdo na fonte mudar, a lápide continua valendo.

## 7. Atualização, `version` e `locked_fields`

Atualize só com controle de versão, lendo antes a versão e os locks, na mesma transação do `FOR UPDATE` do vínculo:

```sql
SELECT version, locked_fields FROM event WHERE id = :event_id FOR UPDATE;

UPDATE event
   SET title = :title, description = :description, /* …apenas colunas NÃO travadas… */
       version = version + 1
 WHERE id = :event_id AND version = :version;
```

- **Sempre** `version = version + 1` **e** `WHERE version = :version`. Se afetar 0 linhas, o evento mudou no meio
  (edição manual): releia e reavalie o item (no máximo algumas tentativas), sem sobrescrever às cegas. Sem o incremento, o
  lock otimista da API não percebe a alteração.
- **`locked_fields`** (`TEXT[]`, `'{}'` por padrão) lista o que uma correção manual alterou. É a regra **"correção manual vence"**:
  - **Colunas:** o agente **não escreve** nas colunas listadas (`title`, `description`, `price_min_cents`, `status`, …), cada uma
    individualmente. As demais colunas continuam atualizáveis.
  - **Sessões:** o nome especial **`occurrences`** (não é coluna) indica que as sessões foram alteradas à mão. O agente
    só deixa de reescrever as sessões quando `occurrences` está listado; lock em **outra** coluna não bloqueia as sessões
    (e `occurrences` não bloqueia as colunas).
  - O agente **nunca altera** `locked_fields`; quem o preenche é o back-end, no `PUT /events/{id}` de evento `AGENT`.
- Reescrita das sessões (quando `occurrences` não está travado): remova as ausentes e insira/atualize as demais por
  `(event_id, starts_at)`:

```sql
INSERT INTO occurrence (event_id, starts_at, ends_at, status) VALUES (:event_id, :starts_at, :ends_at, :status)
ON CONFLICT (event_id, starts_at) DO UPDATE SET ends_at = EXCLUDED.ends_at, status = EXCLUDED.status;
DELETE FROM occurrence WHERE event_id = :event_id AND starts_at <> ALL(:starts_at_list);
```

- Fim da transação do item: `UPDATE event_source_link SET content_hash = :hash, external_url = :url, last_seen_at = now(),
  last_run_id = :run_id WHERE source_id = :source_id AND external_id = :external_id`.

## 8. Deduplicação entre fontes

Antes de **criar** (vínculo inexistente), procure o mesmo evento vindo de outra fonte, na mesma cidade e com sessão próxima,
usando `similarity()` do `pg_trgm` (extensão ativa desde a V001):

```sql
SELECT e.id
  FROM event e
 WHERE e.city_id = :city_id
   AND similarity(e.title, :title) >= 0.6                      -- limiar sugerido; calibrar com dados reais
   AND EXISTS (SELECT 1 FROM occurrence o
                WHERE o.event_id = e.id
                  AND o.starts_at BETWEEN :starts_at - interval '1 day' AND :starts_at + interval '1 day')
 ORDER BY similarity(e.title, :title) DESC
 LIMIT 1;
```

Se achar um evento, **não crie outro**: crie apenas o `event_source_link` da nova fonte apontando para ele (idempotência
da nova fonte) e **não** sobrescreva o evento existente (a primeira fonte é a dona). O limiar e a janela são recomendações.

## 9. Resumo das garantias

| Garantia | Onde é imposta |
|---|---|
| Sem DDL e sem escrita em `category`, `city`, `state`, `schema_contract` | Privilégios da role (V008) |
| Um item da fonte ↔ no máximo um vínculo | `UNIQUE (source_id, external_id)` |
| Evento excluído à mão não é recriado | Lápide (`event_id` nulo, `ON DELETE SET NULL`) + regra do agente |
| Correção manual não é desfeita | `locked_fields` (back-end preenche; **agente respeita**) |
| Escrita concorrente não se perde | `version = version + 1` + `WHERE version` (agente e API) |
| Slug, `updated_at`, `search_vector`, invariantes de preço/local/sessão | Triggers e constraints do banco |
| Agente para se o contrato mudou de forma incompatível | Checagem de `schema_contract` na partida (agente) |
