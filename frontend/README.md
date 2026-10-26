# Frontend

React + TypeScript (Vite), TanStack Query, React Router e Tailwind CSS. Sem telas de negócio ainda: apenas a fundação (layout, rotas por cidade, cliente tipado pelo contrato).

## Pré-requisitos

- Node 24 (`.node-version`) e npm
- Back-end rodando em `http://localhost:8080` (veja o README da raiz); o Vite encaminha `/api` para ele, então não há CORS

## Comandos (Windows/PowerShell)

```powershell
cd frontend
npm install
npm run dev      # http://localhost:5173
npm test         # Vitest + React Testing Library + MSW
```

Outros scripts: `npm run build`, `npm run lint`, `npm run format`.

## Cliente contract-first

Os tipos vêm de `../api/openapi.yaml` via `openapi-typescript` e ficam em `src/api/schema.d.ts` (gerado, fora do git). O script `generate:api` roda automaticamente antes de `dev`, `build` e `test`. Para gerar manualmente (ex.: para o editor reconhecer os tipos logo após clonar):

```powershell
npm run generate:api
```

Use sempre `npm test` (e não `npx vitest`), para que a geração rode antes.

## Rotas

- `/` redireciona para a cidade padrão (`GET /api/v1/cities/default`)
- `/:uf/:cidade`, por exemplo `/sp/sao-paulo`
- qualquer outro caminho mostra a página 404
