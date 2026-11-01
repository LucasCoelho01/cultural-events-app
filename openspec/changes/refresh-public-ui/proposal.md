## Why

O front-end público é funcional, mas visualmente cru: cabeçalho só com texto, cards sem imagem, página de evento sem destaque visual e sem caminho de volta para a listagem (o usuário perde filtros, busca e página). A marca (logo e paleta) já existe e precisa chegar à interface.

## What Changes

- Logo no cabeçalho (`logo.svg`) como link para a página inicial da cidade atual (`/:uf/:cidade`) ou, fora de rota de cidade, para `/`; `logo-mark.svg` como favicon.
- Botão "← Voltar para os eventos" no topo da página do evento: volta à listagem de origem preservando filtros, busca e página (URL de origem no `state` do `Link`); sem origem, vai para a página inicial da cidade.
- Tokens de cor da marca (roxo `#7C3AED`, rosa `#EC4899`, laranja `#F59E0B`) no tema do Tailwind 4, fonte Poppins (Google Fonts) com fallback de sistema.
- Cabeçalho com destaque em degradê; cards com imagem 16:9, chip de categoria, data e preço em destaque, sombra no hover; grade de 1, 2 ou 3 colunas.
- Página do evento com banner de imagem e informações em blocos.
- Placeholder em degradê com o ícone da marca quando `imageUrl` falta ou falha (`onError`); imagens com `loading="lazy"` e `alt` com o título.
- Acessibilidade: contraste AA, foco visível, informação nunca só por cor.
- Admin sem redesign; apenas herda o novo cabeçalho.
- Testes de componente atualizados.

Nenhuma mudança na API, no banco ou no `api/openapi.yaml` (`imageUrl` já existe em `Event`).

## Capabilities

### New Capabilities
- `public-branding`: identidade visual do front-end (logo, favicon, tema, fonte, placeholder de imagem, acessibilidade visual).

### Modified Capabilities
- `public-catalog`: card de evento (imagem, chip, destaque de data/preço, grade) e página de detalhe (banner, blocos, botão de voltar com origem).

## Impact

- Código: `frontend/index.html`, `frontend/src/index.css`, `Layout.tsx`, `EventCard.tsx`, `EventList.tsx`, `EventPage.tsx`, novo componente de imagem com fallback, testes em `frontend/src/components/`.
- Dependência externa nova: Google Fonts (CSS da Poppins) em tempo de execução; sem pacote npm novo.
- Contrato de schema com o agente: nenhum impacto.
- Admin: o cabeçalho compartilhado muda de aparência; `event-admin` não muda de requisitos (o link "Admin" segue no cabeçalho).
