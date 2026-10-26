## Why

O back-end já expõe contrato, erros RFC 7807 e a cidade padrão, mas não existe projeto de front-end. A fase 2 precisa de uma fundação (build, cliente tipado pelo contrato, roteamento por cidade, tratamento de erro, testes) antes de qualquer tela de negócio, para que as telas seguintes só adicionem funcionalidade.

## What Changes

- Novo projeto `frontend/`: Vite + React + TypeScript (strict), npm, Node 24, React Router, TanStack Query e Tailwind CSS.
- Cliente contract-first: tipos gerados de `api/openapi.yaml` com `openapi-typescript` (script `generate:api`, executado antes de `dev`, `build` e `test`; arquivo gerado fora do git) e chamadas via `openapi-fetch`. Wrappers finos de TanStack Query por recurso em `src/api/`.
- Proxy de desenvolvimento do Vite: `/api` → `http://localhost:8080`, sem CORS no back-end.
- Layout base: cabeçalho com nome do app e cidade atual (`GET /api/v1/cities/default`), área de conteúdo, página 404 e componente padrão de erro que exibe o `detail` do `Problem`.
- Rotas por cidade: `/:uf/:cidade` (ex.: `/sp/sao-paulo`) e `/` redirecionando para a cidade padrão. Nenhum código cita "São Paulo".
- Qualidade: ESLint, Prettier, Vitest + React Testing Library + MSW (testes de layout, redirecionamento e componente de erro).
- README com `npm install`, `npm run dev` e `npm test` para Windows/PowerShell.
- Nenhuma mudança em back-end, banco ou `api/openapi.yaml`. Sem **BREAKING**.

## Capabilities

### New Capabilities

Nenhuma. O front-end não tem capability própria.

### Modified Capabilities

- `platform-foundation`: requisitos ADDED para a fundação do front-end (build/qualidade, cliente contract-first, proxy, layout e erro, rotas por cidade).

## Impact

- Código: novo diretório `frontend/`; `.gitignore` (arquivo gerado da API) e `README.md` da raiz.
- Dependências novas (somente front-end): react, react-router, @tanstack/react-query, tailwindcss, openapi-typescript, openapi-fetch, vitest, @testing-library/react, msw, eslint, prettier.
- Contrato de schema com o agente de ingestão: nenhum impacto (sem scripts SQL nem mudanças no banco).
- Contrato HTTP: apenas consumo de `api/openapi.yaml` existente; sem alterações.
