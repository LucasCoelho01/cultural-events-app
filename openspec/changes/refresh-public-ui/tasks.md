## 1. Tema, fonte e favicon

- [ ] 1.1 Adicionar `@theme` com tokens de cor da marca e `--font-sans` (Poppins + fallback) e regra global `:focus-visible` em `frontend/src/index.css`; verificar com `npm run build` e inspeção das classes `bg-brand-*` aplicadas.
- [ ] 1.2 Adicionar `preconnect` + CSS da Poppins e `<link rel="icon" href="/logo-mark.svg">` em `frontend/index.html`; verificar no `npm run dev` que favicon e fonte carregam.

## 2. Cabeçalho com logo

- [ ] 2.1 Reescrever o cabeçalho de `Layout.tsx` com `logo.svg`, destaque em degradê e link do logo para `/:uf/:cidade` (via `useParams`) ou `/`, mantendo cidade e links Admin/Execuções; verificar em testes de componente: logo em rota de cidade, em `/admin/eventos` e com cidade 404.

## 3. Imagem com fallback

- [ ] 3.1 Criar `components/EventImage.tsx` (16:9, `object-cover`, `loading="lazy"`, `alt` = título, placeholder em degradê com `logo-mark.svg` para `src` ausente ou `onError`); verificar com testes: sem `imageUrl`, `onError` (`fireEvent.error`) troca para placeholder, novo `src` reinicia o estado, imagem válida tem `loading="lazy"` e `alt`.

## 4. Card e grade

- [ ] 4.1 Redesenhar `EventCard.tsx` (imagem no topo, chip de categoria com texto, data e preço em destaque, cantos arredondados, sombra no hover, foco visível) e passar `state={{ from }}` com a URL atual da listagem; verificar em `pages.test.tsx`: card com e sem imagem, `state` de origem presente, textos "Sem sessões..." mantidos.
- [ ] 4.2 Ajustar a grade em `EventList.tsx` para 1/2/3 colunas e estilizar busca, filtros, paginação e estados na nova paleta sem mudar comportamento; verificar que `search.test.tsx`, `filters.test.tsx` e os testes de paginação existentes continuam passando.

## 5. Página do evento

- [ ] 5.1 Reescrever `EventPage.tsx` com "← Voltar para os eventos" no topo (origem do `state` aceita só se iniciar por `/` e não por `//`, senão `/:uf/:cidade`), banner `EventImage` e informações em blocos (sessões, local, preço/classificação, ingresso), mantendo `ErrorMessage` e estados de carregamento; verificar com testes: voltar com origem (`?q=jazz&page=2`), sem origem, origem inválida (`https://outro.site`), banner com e sem imagem, campos opcionais ausentes, 404 e 500.

## 6. Verificação final

- [ ] 6.1 Rodar `npm run lint`, `npm run build` e `npm test` em `frontend/`; confirmar que os testes do admin passam sem mudança e que não há cidade real no código/testes (usar `zz/cidade-teste`).
- [ ] 6.2 Conferir no navegador (desktop e mobile): grade 1/2/3 colunas, foco por Tab visível, contraste AA dos pares de cor (ferramenta de contraste), placeholder com `imageUrl` quebrada, navegação logo e voltar.
