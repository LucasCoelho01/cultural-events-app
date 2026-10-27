## Why

Hoje eventos só entram no sistema por chamadas diretas à API (Postman) ou pelo agente de ingestão. Quem opera o catálogo precisa de uma tela para criar, corrigir e remover eventos da cidade sem montar JSON à mão. A API de escrita (`POST`, `PUT` com lock otimista, `DELETE`) já existe e a fundação do front-end e as páginas públicas já estão prontas; falta a área administrativa.

## What Changes

- **Front-end:**
  - `/admin/eventos`: tabela paginada dos eventos da cidade padrão, com todos os status, mostrando título, status e próxima sessão, com ações editar e excluir.
  - `/admin/eventos/novo` e `/admin/eventos/:id/editar`: um único formulário (título, slug opcional, categoria, local, descrição, status, classificação etária, gratuito ou preço mínimo/máximo em reais, link de ingresso, imagem e lista dinâmica de sessões).
  - Criação com `POST /events`; edição com `PUT /events/{id}` enviando `version`; 409 de concorrência com aviso e opção de recarregar; 422 mapeado para os campos; exclusão com confirmação na própria página.
  - Validação no cliente espelhando o contrato, sem substituir a do servidor.
  - Link discreto "Admin" no cabeçalho.
- **Sem mudança** em `api/openapi.yaml`, no back-end, no banco ou em dependências (o formulário usa `useActionState` do React 19; nenhuma biblioteca de formulário).
- Sem **BREAKING**.

## Capabilities

### New Capabilities

- `event-admin`: área administrativa do front-end para listar, criar, editar e excluir eventos.

### Modified Capabilities

Nenhuma. `events` (API) e `public-catalog` não mudam de requisito.

## Non-goals

- **Autenticação e autorização:** não há login nem proteção das rotas `/admin` nesta fase; a API de escrita também é aberta hoje. A área deve ser tratada como interna até uma change de segurança própria.
- Administração de locais, categorias e cidades; upload de imagem (só URL); seleção de outra cidade (usa a cidade padrão); edição em lote; busca e filtros na tabela.

## Impact

- Código: `frontend/src` (rotas, `src/api/` com mutações, componentes de lista, formulário e confirmação, funções puras de conversão de preço, fuso, validação e mapeamento de erros, e o `Layout`). Nenhuma alteração em `backend/`, `database/`, `api/` ou `postman/`.
- Contrato de schema com o agente de ingestão: nenhum impacto.
- Dependências novas: nenhuma.
