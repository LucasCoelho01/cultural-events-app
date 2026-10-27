# public-catalog Specification

## Purpose
A capability `public-catalog` define as páginas públicas do catálogo de eventos por cidade e o endpoint público de leitura que as sustenta, expondo apenas eventos publicados.

## Requirements

### Requirement: Endpoint público de detalhe de evento publicado
A API SHALL expor `GET /api/v1/cities/{uf}/{slug}/events/{eventSlug}` (sem autenticação) e responder HTTP 200 com o `Event` (mesma representação de `GET /events/{id}`) somente quando existir, na cidade identificada por `uf`/`slug` (UF sem diferenciar maiúsculas), um evento com `slug` igual a `eventSlug` e `status = PUBLISHED`. Nos demais casos SHALL responder HTTP 404 com `Problem` (`application/problem+json`), sem revelar se o evento existe com outro status.

#### Scenario: Evento publicado na cidade
- **WHEN** o cliente envia `GET /api/v1/cities/{uf}/{slug}/events/{eventSlug}` de um evento `PUBLISHED` dessa cidade
- **THEN** a API responde HTTP 200 com o `Event`, incluindo `category`, `venue` e `occurrences` ordenadas por `startsAt`

#### Scenario: Evento não publicado retorna 404
- **WHEN** o evento existe na cidade com `status` `DRAFT`, `CANCELLED` ou `ARCHIVED`
- **THEN** a API responde HTTP 404 com `Problem` (`status=404`, `title`, `detail`), idêntico ao de um slug inexistente

#### Scenario: Evento de outra cidade retorna 404
- **WHEN** o `eventSlug` pertence a um evento `PUBLISHED` de outra cidade
- **THEN** a API responde HTTP 404 com `Problem` (`status=404`)

#### Scenario: Cidade inexistente retorna 404
- **WHEN** `uf`/`slug` não identificam nenhuma cidade
- **THEN** a API responde HTTP 404 com `Problem` (`status=404`, `detail` indicando a cidade)

#### Scenario: UF malformada retorna 400
- **WHEN** o cliente envia `uf` que não tem exatamente 2 letras (ex.: `sao`)
- **THEN** a API responde HTTP 400 com `Problem` (`status=400`)

### Requirement: Lista pública de eventos da cidade
O front-end SHALL exibir em `/:uf/:cidade` os eventos da cidade obtidos de `GET /api/v1/events?city={uf}/{slug}&status=PUBLISHED&from=<agora ISO 8601>&page=<n>`, paginados com botões "Anterior" e "Próxima" controlados por `PageMetadata` (`page`, `totalPages`), e SHALL mostrar estados de carregando, vazio (nenhum evento) e erro (componente `ErrorMessage`, exibindo o `detail` do `Problem`).

#### Scenario: Lista com eventos
- **WHEN** a API responde HTTP 200 com eventos
- **THEN** a página exibe um card por evento, na ordem recebida

#### Scenario: Navegação entre páginas
- **WHEN** `PageMetadata.totalPages` é maior que 1 e o usuário aciona "Próxima"
- **THEN** a página seguinte é requisitada (`page` incrementado), e "Anterior" fica desabilitado na primeira página e "Próxima" na última

#### Scenario: Nenhum evento
- **WHEN** a API responde HTTP 200 com `items` vazio na primeira página (`page=0`)
- **THEN** a página exibe a mensagem de estado vazio e nenhum botão de paginação ativo

#### Scenario: Carregando
- **WHEN** a requisição ainda está em andamento
- **THEN** a página exibe o indicador de carregamento

#### Scenario: Erro da API
- **WHEN** a API responde HTTP 500 com um `Problem` contendo `detail`
- **THEN** a página exibe `ErrorMessage` com o texto de `detail`, sem lista

#### Scenario: Cidade inexistente
- **WHEN** a API responde HTTP 404 com `Problem` para a cidade da rota
- **THEN** a página exibe `ErrorMessage` com o `detail` e não exibe a lista

### Requirement: Página da lista na query string
A página atual da lista SHALL ser refletida na query string `?page=N`, com `N` começando em 1 (primeira página, também quando o parâmetro é omitido), e SHALL ser enviada à API como `page = N - 1` (a API é baseada em zero).

#### Scenario: Primeira página sem parâmetro
- **WHEN** o usuário acessa `/:uf/:cidade` sem `?page=`
- **THEN** a lista requisita `page=0` à API e a URL permanece sem `?page=`

#### Scenario: Página explícita
- **WHEN** o usuário acessa `/:uf/:cidade?page=3`
- **THEN** a lista requisita `page=2` à API

#### Scenario: Próxima atualiza a URL
- **WHEN** o usuário está na primeira página e aciona "Próxima"
- **THEN** a URL passa a ter `?page=2` e a API recebe `page=1`

#### Scenario: Valor inválido
- **WHEN** a URL tem `?page=0`, `?page=-1` ou `?page=abc`
- **THEN** a lista trata como a primeira página (`page=0` na API), sem erro

#### Scenario: Página além do total
- **WHEN** a URL tem `?page=99` e a API responde HTTP 200 com `items` vazio
- **THEN** a página exibe o estado vazio e o botão "Anterior" permanece ativo para voltar

### Requirement: Card de evento
Cada card SHALL exibir título, categoria (`category.name`), local (`venue.name`, omitido se `venue` for nulo), próxima sessão e preço, e SHALL ser um link para `/:uf/:cidade/eventos/:slug` do evento. A próxima sessão SHALL ser a primeira sessão `SCHEDULED` com `startsAt` posterior ao instante atual; sem ela, o card SHALL exibir "Sem sessões futuras".

#### Scenario: Card completo
- **WHEN** um evento tem categoria, local, sessões futuras e preço
- **THEN** o card mostra título, categoria, local, a data da próxima sessão e o preço, e o link aponta para o detalhe do evento

#### Scenario: Evento sem local
- **WHEN** `venue` é nulo
- **THEN** o card não mostra local e não falha

#### Scenario: Sem sessão futura agendada
- **WHEN** todas as sessões do evento estão no passado ou com status diferente de `SCHEDULED`
- **THEN** o card exibe "Sem sessões futuras" no lugar da data

### Requirement: Página de detalhe do evento
O front-end SHALL exibir em `/:uf/:cidade/eventos/:slug` o evento obtido de `GET /api/v1/cities/{uf}/{slug}/events/{eventSlug}`, mostrando título, descrição, classificação etária, preço e link de ingresso, e SHALL exibir `ErrorMessage` quando a requisição falhar.

#### Scenario: Detalhe completo
- **WHEN** a API responde HTTP 200 com um evento com descrição, classificação etária, preço e `ticketUrl`
- **THEN** a página exibe título, descrição, classificação, preço e um link de ingresso que abre `ticketUrl`

#### Scenario: Campos opcionais ausentes
- **WHEN** `description`, `ageRating`, `ticketUrl` e `venue` são nulos
- **THEN** a página exibe título, sessões e preço, sem seções vazias nem erro

#### Scenario: Evento não encontrado
- **WHEN** a API responde HTTP 404 com `Problem` (`detail`)
- **THEN** a página exibe `ErrorMessage` com o `detail` e nenhum dado do evento

#### Scenario: Erro do servidor
- **WHEN** a API responde HTTP 500 com `Problem` (`detail`)
- **THEN** a página exibe `ErrorMessage` com o `detail`

### Requirement: Sessões futuras no detalhe do evento
A página de detalhe SHALL listar todas as sessões com `startsAt` posterior ao instante atual, formatadas no fuso da cidade e com o status de cada uma.

#### Scenario: Apenas sessões futuras
- **WHEN** o evento tem sessões passadas e futuras
- **THEN** somente as sessões com `startsAt` posterior ao instante atual são listadas, em ordem de `startsAt`

#### Scenario: Sessão cancelada ou esgotada
- **WHEN** uma sessão futura tem status `CANCELLED` ou `SOLD_OUT`
- **THEN** ela continua listada, com o status visível

#### Scenario: Nenhuma sessão futura
- **WHEN** todas as sessões do evento estão no passado
- **THEN** a página exibe "Sem sessões futuras" no lugar da lista

#### Scenario: Fuso da cidade indisponível
- **WHEN** `GET /cities/{uf}/{slug}` falha e o fuso da cidade não está disponível
- **THEN** as datas não são exibidas com fuso incorreto: a página exibe `ErrorMessage` no lugar das sessões

### Requirement: Local e endereço no detalhe do evento
A página de detalhe SHALL exibir o nome do local e, quando disponível, seu endereço (`addressLine` e `neighborhood`), obtido de `GET /api/v1/venues/{id}` com o `venue.id` do evento.

#### Scenario: Local com endereço
- **WHEN** o evento tem `venue` e `GET /venues/{id}` responde HTTP 200
- **THEN** a página exibe o nome do local, `addressLine` e `neighborhood`

#### Scenario: Evento sem local
- **WHEN** `venue` é nulo
- **THEN** a página não faz a chamada `GET /venues/{id}` e não exibe a seção de local

#### Scenario: Falha ao carregar o local
- **WHEN** o evento carrega mas `GET /venues/{id}` responde HTTP 404 ou 500
- **THEN** a página mantém o evento visível e mostra apenas o nome do local (`venue.name`), sem derrubar a página

### Requirement: Formatação de data e preço por funções puras
O front-end SHALL formatar datas e preços em funções puras e testáveis: datas com `Intl.DateTimeFormat('pt-BR')` no fuso IANA da cidade (`City.timezone`), e preço como "Grátis" quando `isFree` for verdadeiro; "R$ x" quando só houver um valor (ou mínimo igual ao máximo); "R$ x – R$ y" quando mínimo e máximo diferirem; valores em reais a partir de centavos, com formatação `pt-BR`. Sem preço informado e sem `isFree`, SHALL retornar texto vazio.

#### Scenario: Evento gratuito
- **WHEN** `isFree` é verdadeiro
- **THEN** a função de preço retorna "Grátis", mesmo que existam valores

#### Scenario: Faixa de preço
- **WHEN** `priceMinCents=2500` e `priceMaxCents=8000`
- **THEN** a função retorna "R$ 25,00 – R$ 80,00"

#### Scenario: Preço único
- **WHEN** só `priceMinCents=2500` é informado, ou mínimo e máximo são iguais
- **THEN** a função retorna "R$ 25,00"

#### Scenario: Sem preço informado
- **WHEN** `isFree` é falso e mínimo e máximo são nulos
- **THEN** a função retorna texto vazio

#### Scenario: Fuso horário inválido
- **WHEN** a função de data recebe um fuso IANA inválido
- **THEN** ela formata no fuso do navegador em vez de lançar exceção

#### Scenario: Data no fuso da cidade
- **WHEN** `startsAt` é `2030-01-01T02:00:00Z` e o fuso da cidade é `America/Recife` (UTC-3)
- **THEN** a data formatada corresponde a 31/12/2029 às 23:00

### Requirement: Páginas sem cidade fixa no código
O código-fonte e os testes de página do front-end SHALL NOT citar uma cidade real; os testes de página usam MSW com a cidade fictícia `zz/cidade-teste`.

#### Scenario: Busca por literais de cidade
- **WHEN** `frontend/src` é inspecionado
- **THEN** não há ocorrência de "São Paulo" nem "sao-paulo" (exceto o arquivo gerado do contrato, ignorado pelo git)

#### Scenario: Teste de página sem handler
- **WHEN** um teste de página faz uma requisição sem handler MSW correspondente
- **THEN** o teste falha (`onUnhandledFrame: 'error'`)
