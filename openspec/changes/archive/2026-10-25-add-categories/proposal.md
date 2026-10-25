# Proposal

## Why

Eventos, locais e busca precisam de uma taxonomia estável para classificar o que é um show, uma peça ou uma exposição. Definir isso agora, antes da capability de eventos, evita retrabalho de schema e dá ao agente de ingestão (fase 3) um conjunto fechado de destinos, com "Outros" como fallback quando nenhuma categoria corresponde.

## What Changes

- Nova migration `database/migrations/V003__create_categories.sql` criando a tabela `category` (`id`, `parent_id` auto-referenciado, `name`, `slug` único, `sort_order`, `CHECK (parent_id <> id)`) e a carga inicial das categorias raiz, nesta ordem: Shows, Teatro, Exposições, Dança, Cinema, Outros (slugs sem acento).
- A profundidade máxima de 2 níveis (categoria > subcategoria) é garantida no banco por trigger, contra qualquer escritor (scripts SQL manuais incluídos), e não só pela aplicação.
- Novos endpoints somente leitura, declarados primeiro em `api/openapi.yaml` (tag `categories`): `GET /categories` (árvore: raízes com `children`) e `GET /categories/{slug}` (categoria com seus `children`, ou 404).
- Novo DTO `Category` (`id`, `name`, `slug`, `sortOrder`, `parentSlug`, `children`); erros reusam `Problem`.
- Novo pacote `com.culturalevents.categories.{api,application,domain,infrastructure}` seguindo o padrão de `locations`, com entidade JPA compatível com o script (`ddl-auto=validate`).
- Cadastro de novas categorias e subcategorias é feito por script SQL numerado; a API não oferece escrita.
- Testes de integração estendendo `AbstractPostgresIntegrationTest`, com fixtures removidas após cada teste.

## Capabilities

### New Capabilities
- `categories`: taxonomia somente leitura de categorias e subcategorias (schema com limite de 2 níveis, carga inicial das raízes e consulta por API em árvore ou por slug).

### Modified Capabilities
<!-- Nenhuma: platform-foundation já permite paths de negócio desde add-locations. -->

## Impact

- **Capabilities afetadas:** `categories` (nova). Nenhuma capability existente muda de requisito.
- **Banco:** nova migration V003 (tabela, função e trigger de limite de nível, carga inicial); sem alteração de tabelas existentes.
- **Contrato com o agente de ingestão (fase 3):** `category` pertence ao back-end e é mantida só por scripts SQL numerados. O agente só LÊ `category`: faz lookup por `slug` e usa a categoria `outros` como fallback quando não há correspondência (a carga inicial garante que `outros` existe). A role `agent_writer` NÃO recebe privilégio de escrita (INSERT, UPDATE, DELETE) sobre `category`; ela precisa apenas de SELECT, concedido junto com a fase 3, fora desta change. O trigger de 2 níveis permanece como garantia no banco contra qualquer escritor, incluindo scripts SQL manuais.
- **API:** `api/openapi.yaml` ganha 2 paths e 1 schema novo; sem breaking changes.
- **Código:** novo pacote `categories` no backend; sem mudança no handler de erros compartilhado.
- **Dependências:** nenhuma nova.
