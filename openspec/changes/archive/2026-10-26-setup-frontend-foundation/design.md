## Context

O back-end (Spring Boot) expõe `/api/v1` com `Problem` (RFC 7807) e `GET /cities/default` e `GET /cities/{uf}/{slug}`, descritos em `api/openapi.yaml`. O monorepo usa Windows/PowerShell e o contrato é a fonte de verdade. Não existe `frontend/`. Motivação em `proposal.md`.

## Goals / Non-Goals

**Goals:** fundação executável (dev/build/test/lint), cliente tipado pelo contrato, roteamento por cidade, layout e erro padrão, testes com API mockada.
**Non-Goals:** telas de negócio (eventos, locais), autenticação, i18n, deploy/Docker do front-end, CI.

## Decisions

- **Vite + React + TS strict, npm, Node 24.** Declarar `engines.node` e `.node-version` com 24.
- **Tipos via `openapi-typescript`** em `src/api/schema.d.ts` (gitignored), script `generate:api`: `openapi-typescript ../api/openapi.yaml -o src/api/schema.d.ts`. `predev`, `prebuild` e `pretest` chamam `generate:api` (hooks `pre*` do npm). O `servers: /api/v1` do contrato não entra nos tipos, então `openapi-fetch` usa `baseUrl: "/api/v1"` e os paths são `/cities/default`.
  *Alternativa descartada:* versionar o arquivo gerado — divergiria do contrato.
- **`src/api/`:** `client.ts` (instância `createClient<paths>`), `problem.ts` (tipo `Problem` extraído de `components["schemas"]["Problem"]` e type guard `isProblem`), `cities.ts` (`useDefaultCity`, `useCity(uf, slug)`) sobre `useQuery`. Um arquivo por recurso; sem camada de abstração além de query keys.
  Erros: o wrapper lança o corpo `Problem` quando `error` existe, ou `Error` em falha de rede; `ErrorMessage` aceita `unknown`.
- **Proxy:** `server.proxy["/api"] = "http://localhost:8080"` em `vite.config.ts`. Sem CORS no back-end.
- **Roteamento (React Router):** layout raiz com `<Outlet/>`; rotas `/` (componente `RedirectToDefaultCity`: usa `useDefaultCity` e `<Navigate replace>` para `/{uf minúsculo}/{slug}`), `/:uf/:cidade` (placeholder de conteúdo que valida a cidade com `useCity`) e `*` (404). O parâmetro de rota se chama `cidade`, conforme pedido, e é passado como `slug` à API.
- **Cabeçalho:** exibe a cidade da rota atual quando houver `:uf/:cidade`, senão a cidade padrão. Erro mostra `ErrorMessage` em vez de quebrar o layout.
- **Sem "São Paulo" no código:** nenhum literal de cidade em `src/`; testes usam cidade fictícia (ex.: "Cidade Teste", `zz/cidade-teste`) via MSW.
- **Tailwind CSS v4** com plugin `@tailwindcss/vite`; sem arquivo de config separado.
- **Testes:** Vitest (`environment: jsdom`, setup iniciando o servidor MSW com `onUnhandledRequest: "error"`), RTL, `QueryClient` novo por teste com `retry: false`. Cobertura: layout (cabeçalho), redirecionamento (`/` → cidade padrão, e falha), `ErrorMessage` (Problem vs. genérico), 404.
- **Qualidade:** ESLint flat config (typescript-eslint, react-hooks) e Prettier; scripts `lint` e `format`.
- **Problem:** respostas de erro usam `application/problem+json`; `openapi-fetch` interpreta JSON por sufixo `+json`.

**Scripts SQL novos:** nenhum. **Mudanças no OpenAPI:** nenhuma (apenas leitura). **`.gitignore`:** acrescentar `frontend/src/api/schema.d.ts`.

## Risks / Trade-offs

- [Geração depende de `../api/openapi.yaml`] → o script falha explicitamente se ausente; o front-end roda dentro do monorepo.
- [Hooks `pre*` não rodam se o Vitest for chamado direto/`npx`] → README orienta usar `npm test`.
- [Versões recentes (Node 24, Tailwind 4, React Router 7) mudam rápido] → versões fixadas no `package-lock.json`.
- [Tipos gerados ausentes quebram o editor antes do primeiro `generate:api`] → README orienta rodar `npm run generate:api`.

## Open Questions

Nenhuma bloqueante. Premissa registrada: nome do app "Cultural Events", definido em uma constante única.
