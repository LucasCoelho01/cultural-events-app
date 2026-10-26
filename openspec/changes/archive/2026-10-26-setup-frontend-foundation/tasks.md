## 1. Projeto e tooling

- [x] 1.1 Criar `frontend/` com Vite + React + TypeScript; habilitar `strict`; `engines.node` e `.node-version` com 24
- [x] 1.2 Instalar e configurar React Router, TanStack Query e Tailwind CSS (plugin Vite)
- [x] 1.3 Configurar ESLint (flat config) e Prettier; scripts `lint` e `format`
- [x] 1.4 Acrescentar `frontend/src/api/schema.d.ts` ao `.gitignore`

## 2. Cliente contract-first

- [x] 2.1 Script `generate:api` (`openapi-typescript ../api/openapi.yaml`) e hooks `predev`, `prebuild`, `pretest`; confirmar falha com contrato ausente/inválido
- [x] 2.2 `src/api/client.ts` com `openapi-fetch` (`baseUrl: /api/v1`) tipado por `paths`
- [x] 2.3 `src/api/problem.ts` (tipo `Problem` do schema gerado, type guard) e wrappers TanStack Query em `src/api/cities.ts` (`useDefaultCity`, `useCity`)
- [x] 2.4 Proxy do Vite `/api` → `http://localhost:8080`

## 3. Layout, rotas e erro

- [x] 3.1 Componente `ErrorMessage` (exibe `detail` do Problem; mensagem genérica caso contrário)
- [x] 3.2 Layout com cabeçalho (nome do app + cidade atual), área de conteúdo e `Outlet`
- [x] 3.3 Rotas: `/` com `RedirectToDefaultCity`, `/:uf/:cidade` com validação da cidade, `*` com página 404
- [x] 3.4 Garantir que nenhum literal de cidade existe em `src/` (verificação por busca)

## 4. Testes (Vitest + RTL + MSW)

- [x] 4.1 Setup: jsdom, servidor MSW (`onUnhandledRequest: error`), helper de render com `QueryClient` e `MemoryRouter`, cidade fictícia
- [x] 4.2 Teste do layout: cabeçalho com nome do app e cidade; erro da API no cabeçalho
- [x] 4.3 Teste do redirecionamento: `/` → `/{uf}/{slug}`; falha 500 com Problem exibe `detail` sem redirecionar
- [x] 4.4 Teste do `ErrorMessage`: Problem com `detail`, erro de rede/não-Problem, cidade inexistente (404)
- [x] 4.5 Teste da página 404
- [x] 4.6 Testes de integração com Testcontainers não se aplicam (nenhum back-end/banco alterado); validar manualmente com o back-end rodando

## 5. Documentação e verificação

- [x] 5.1 `frontend/README.md` e seção no `README.md` da raiz: `npm install`, `npm run dev`, `npm test` (PowerShell), pré-requisito Node 24 e back-end em `localhost:8080`
- [x] 5.2 Rodar `npm run lint`, `npm run build` e `npm test` e confirmar sucesso
