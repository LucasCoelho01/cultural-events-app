## Context

Front-end React 19 + React Router 8 + Tailwind 4 (`@import 'tailwindcss'` em `index.css`, sem arquivo de config). `Layout` envolve rotas públicas e `/admin/*`; hoje o cabeçalho é só texto. `EventCard` é um `Link` simples; `EventPage` não tem imagem. `imageUrl` já vem em `Event` (nullable). Ver proposal.md.

## Goals / Non-Goals

**Goals:**
- Aplicar a identidade visual sem tocar API, banco ou OpenAPI.
- Voltar à listagem preservando a query string, sem estado global.

**Non-Goals:**
- Redesign do admin, modo escuro, redimensionamento/proxy de imagens, novas dependências npm.

## Decisions

- **Tokens via `@theme` em `index.css`**: `--color-brand-purple/pink/orange` e `--font-sans` (Poppins + fallback de sistema). No Tailwind 4 o tema vive no CSS. Texto sobre fundo claro usa tons escuros do roxo (≥4.5:1); rosa e laranja ficam para fundos/decoração ou com texto escuro por cima.
- **Poppins**: `preconnect` + CSS do Google Fonts em `index.html` (`display=swap`). `@fontsource/poppins` foi descartado para não adicionar dependência.
- **Favicon e logo**: `<link rel="icon" type="image/svg+xml" href="/logo-mark.svg">` em `index.html`; cabeçalho com `<img src="/logo.svg" alt="Cultural Events">` (o SVG carrega metadados C2PA; como `img` não entra no DOM).
- **Link do logo**: `Layout` usa `useParams` (como `CityName`): com `uf` e `cidade`, `/${uf}/${cidade}`; senão `/`. Rotas `/admin/*` não têm esses params, então vão para `/`.
- **Origem do "voltar"**: `EventCard` passa `state={{ from: pathname + search }}` (de `useLocation`). `EventPage` lê `useLocation().state`, aceita `from` só se for string iniciada por `/` e não por `//`, senão usa `/${uf}/${cidade}`. Descartados: `navigate(-1)` (quebra no acesso direto) e `sessionStorage` (estado oculto). O `state` sobrevive a reload.
- **`EventImage`** (novo, em `components/`): recebe `src`, `title`, `className`; contêiner `aspect-video` com `img` (`loading="lazy"`, `alt=title`, `object-cover`). Estado de falha via `onError`, reiniciado quando `src` muda; `src` nulo/vazio ou falha renderiza o placeholder (degradê + `logo-mark.svg` decorativo). Reutilizado no card e no banner, com o mesmo espaço (sem layout shift).
- **Chip de categoria**: cor determinística a partir de `category.slug` (hash → uma de três combinações da paleta com texto AA); o nome sempre aparece como texto.
- **Foco visível**: regra global `:focus-visible` com outline na cor da marca em `index.css`.
- **Admin**: nenhum arquivo do admin muda; os links "Admin" e "Execuções do agente" seguem no cabeçalho, estilizados para o novo fundo.

Sem scripts SQL novos e sem mudanças no `api/openapi.yaml`.

## Risks / Trade-offs

- [Contraste do degradê com texto] → Texto sobre fundo sólido/claro; degradê só como faixa ou destaque; conferir os pares de cor na implementação.
- [Google Fonts bloqueado/offline] → Fallback de sistema com `display=swap`; testes não dependem da fonte.
- [Testes que consultam cabeçalho ou links por texto] → Atualizar `App.test.tsx` e `pages.test.tsx` ao novo markup (nome acessível do logo).
- [`imageUrl` em hosts externos lentos] → `loading="lazy"` e placeholder em erro; sem proxy nesta fase.
