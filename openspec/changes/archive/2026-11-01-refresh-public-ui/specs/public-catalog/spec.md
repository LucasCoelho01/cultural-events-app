## MODIFIED Requirements

### Requirement: Card de evento
Cada card SHALL exibir imagem no topo em proporção 16:9, título, categoria (`category.name`) em chip, local (`venue.name`, omitido se `venue` for nulo), próxima sessão e preço em destaque, e SHALL ser um link para `/:uf/:cidade/eventos/:slug`, levando no `state` da navegação a URL atual da listagem (caminho e query string).

#### Scenario: Card completo
- **WHEN** um evento tem imagem, categoria, local, sessões futuras e preço
- **THEN** o card mostra imagem, título, chip da categoria, local, data da próxima sessão e preço, e o link aponta para o detalhe do evento

#### Scenario: Card sem imagem
- **WHEN** `imageUrl` é nulo ou a imagem falha ao carregar
- **THEN** o card mostra o placeholder de marca no mesmo espaço 16:9, sem erro

#### Scenario: Evento sem local
- **WHEN** `venue` é nulo
- **THEN** o card não mostra local e não falha

### Requirement: Página de detalhe do evento
O front-end SHALL exibir em `/:uf/:cidade/eventos/:slug` o evento obtido de `GET /api/v1/cities/{uf}/{slug}/events/{eventSlug}`, com banner de imagem no topo e título, descrição, classificação etária, preço, sessões, local e link de ingresso organizados em blocos, e SHALL exibir `ErrorMessage` quando a requisição falhar.

#### Scenario: Detalhe completo
- **WHEN** a API responde HTTP 200 com um evento com imagem, descrição, classificação etária, preço e `ticketUrl`
- **THEN** a página exibe banner, título, descrição, classificação, preço e um link de ingresso que abre `ticketUrl`

#### Scenario: Campos opcionais ausentes
- **WHEN** `imageUrl`, `description`, `ageRating`, `ticketUrl` e `venue` são nulos
- **THEN** a página exibe placeholder no lugar do banner, título, sessões e preço, sem blocos vazios nem erro

#### Scenario: Evento não encontrado
- **WHEN** a API responde HTTP 404 com `Problem` (`detail`)
- **THEN** a página exibe `ErrorMessage` com o `detail` e nenhum dado do evento

#### Scenario: Erro do servidor
- **WHEN** a API responde HTTP 500 com `Problem` (`detail`)
- **THEN** a página exibe `ErrorMessage` com o `detail`

## ADDED Requirements

### Requirement: Voltar para a listagem
A página de detalhe SHALL exibir no topo o link "← Voltar para os eventos". Com URL de origem no `state` da navegação, o link SHALL apontar para ela (mantendo filtros, busca e `?page=`); sem origem, para `/:uf/:cidade`.

#### Scenario: Veio da listagem
- **WHEN** o usuário abre o evento a partir de `/zz/cidade-teste?q=jazz&page=2`
- **THEN** "← Voltar para os eventos" aponta para `/zz/cidade-teste?q=jazz&page=2`

#### Scenario: Acesso direto
- **WHEN** o usuário abre o evento digitando a URL, sem `state`
- **THEN** o link aponta para `/zz/cidade-teste`

#### Scenario: Origem inválida
- **WHEN** o `state` traz um valor que não é um caminho interno iniciado por `/` (ex.: `https://outro.site`, `//outro.site` ou vazio)
- **THEN** o link ignora a origem e aponta para `/zz/cidade-teste`

### Requirement: Grade responsiva da lista
A lista pública SHALL organizar os cards em 1 coluna em telas estreitas, 2 em médias e 3 em largas.

#### Scenario: Larguras de tela
- **WHEN** a viewport muda de estreita para larga
- **THEN** a grade passa de 1 para 2 e depois 3 colunas, sem rolagem horizontal

#### Scenario: Lista vazia
- **WHEN** a API responde HTTP 200 com `items` vazio
- **THEN** a grade não é renderizada e a mensagem de vazio é exibida
