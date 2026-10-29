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
O front-end SHALL exibir em `/:uf/:cidade` os eventos da cidade obtidos de `GET /api/v1/events?city={uf}/{slug}&status=PUBLISHED&from=<início>&page=<n>`, acrescentando `category`, `to` e `q` quando houver filtros ativos, paginados com botões "Anterior" e "Próxima" controlados por `PageMetadata` (`page`, `totalPages`).

#### Scenario: Lista com eventos
- **WHEN** a API responde HTTP 200 com eventos
- **THEN** a página exibe um card por evento, na ordem recebida

#### Scenario: Navegação entre páginas
- **WHEN** `PageMetadata.totalPages` é maior que 1 e o usuário aciona "Próxima"
- **THEN** a página seguinte é requisitada (`page` incrementado), "Anterior" fica desabilitado na primeira página e "Próxima" na última, e os filtros ativos, inclusive a busca, são mantidos na URL e na requisição

### Requirement: Estados da lista pública
A lista SHALL mostrar estados de carregando, vazio (nenhum evento) e erro, este último com o componente `ErrorMessage` exibindo o `detail` do `Problem`.

#### Scenario: Nenhum evento
- **WHEN** a API responde HTTP 200 com `items` vazio na primeira página (`page=0`) e não há filtro ativo
- **THEN** a página exibe "Nenhum evento encontrado." e nenhum botão de paginação ativo

#### Scenario: Carregando
- **WHEN** a requisição ainda está em andamento
- **THEN** a página exibe o indicador de carregamento

#### Scenario: Erro da API
- **WHEN** a API responde HTTP 500 com um `Problem` contendo `detail`
- **THEN** a página exibe `ErrorMessage` com o texto de `detail`, sem lista

#### Scenario: Cidade inexistente
- **WHEN** a API responde HTTP 404 com `Problem` para a cidade da rota
- **THEN** a página exibe `ErrorMessage` com o `detail` e não exibe a lista

#### Scenario: Erro de filtro rejeitado pela API
- **WHEN** a API responde HTTP 400 com `Problem` (`status=400`, `detail`) a uma requisição com filtros
- **THEN** a página exibe `ErrorMessage` com o `detail`, sem lista

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
Cada card SHALL exibir título, categoria (`category.name`), local (`venue.name`, omitido se `venue` for nulo), próxima sessão e preço, e SHALL ser um link para `/:uf/:cidade/eventos/:slug` do evento.

#### Scenario: Card completo
- **WHEN** um evento tem categoria, local, sessões futuras e preço
- **THEN** o card mostra título, categoria, local, a data da próxima sessão e o preço, e o link aponta para o detalhe do evento

#### Scenario: Evento sem local
- **WHEN** `venue` é nulo
- **THEN** o card não mostra local e não falha

### Requirement: Próxima sessão do card
Sem período ativo, a próxima sessão do card SHALL ser a primeira sessão `SCHEDULED` com `startsAt` posterior ao instante atual; com período ativo, a primeira sessão `SCHEDULED` dentro do intervalo `from`/`to` enviado à API.

#### Scenario: Período ativo mostra a sessão do intervalo
- **WHEN** há `?periodo=fim-de-semana` e o evento tem uma sessão na quarta-feira e outra no sábado, ambas `SCHEDULED`
- **THEN** o card mostra a data da sessão de sábado, não a de quarta

#### Scenario: Sessão no intervalo não agendada
- **WHEN** há período ativo e a sessão dentro do intervalo está `CANCELLED` ou `SOLD_OUT`
- **THEN** o card ignora essa sessão e mostra a próxima `SCHEDULED` dentro do intervalo, ou "Sem sessões no período" se não houver

### Requirement: Card sem sessão elegível
Sem sessão elegível, o card SHALL exibir "Sem sessões futuras" (sem período ativo) ou "Sem sessões no período" (com período ativo).

#### Scenario: Sem sessão futura agendada
- **WHEN** todas as sessões do evento estão no passado ou com status diferente de `SCHEDULED`
- **THEN** o card exibe "Sem sessões futuras" no lugar da data

#### Scenario: Sessão fora do intervalo
- **WHEN** há período ativo e as únicas sessões `SCHEDULED` do evento estão fora de `from`/`to`
- **THEN** o card exibe "Sem sessões no período" e não a próxima sessão geral

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

### Requirement: Filtro de categoria na lista
A lista SHALL exibir um select com a opção "Todas" e as categorias de `GET /api/v1/categories` em ordem de árvore, com subcategorias indentadas. Escolher uma categoria SHALL enviar `category=<slug>` a `GET /events` (a API inclui as subcategorias) e refletir `?categoria=<slug>` na URL; escolher "Todas" SHALL omitir ambos. Um `categoria` que não corresponde a nenhuma categoria carregada SHALL ser ignorado.

#### Scenario: Escolher categoria
- **WHEN** o usuário escolhe "Shows" no select
- **THEN** a API recebe `category=shows`, a URL passa a ter `?categoria=shows` e a página 1 é exibida

#### Scenario: Categoria pai inclui filhas
- **WHEN** o usuário escolhe a categoria pai "Shows", que tem a filha "Rock"
- **THEN** a requisição contém apenas `category=shows` (a inclusão das filhas é feita pela API) e "Rock" aparece indentada no select

#### Scenario: Voltar para Todas
- **WHEN** há `?categoria=shows` e o usuário escolhe "Todas"
- **THEN** `categoria` sai da URL e `category` não é enviado à API

#### Scenario: Categoria desconhecida na URL
- **WHEN** a URL tem `?categoria=inexistente` e `GET /categories` responde HTTP 200 sem esse slug
- **THEN** `category` não é enviado (evita o HTTP 404 da API), o select mostra "Todas", a lista carrega e não há erro

#### Scenario: Espera pelas categorias
- **WHEN** a URL tem `?categoria=shows` e `GET /categories` ainda está em andamento
- **THEN** `GET /events` não é chamado até as categorias carregarem, e depois é chamado uma única vez com `category=shows`

#### Scenario: Falha ao carregar categorias
- **WHEN** `GET /categories` responde HTTP 500 e a URL tem `?categoria=shows`
- **THEN** `category` não é enviado, a lista de eventos carrega sem filtro de categoria, sem o select e sem derrubar a página

### Requirement: Atalhos de período na lista
A lista SHALL oferecer os atalhos "Hoje", "Este fim de semana" e "Próximos 7 dias"; o atalho ativo SHALL enviar `from` e `to` a `GET /events`. Sem atalho nem intervalo personalizado, `from` é o instante atual e `to` não é enviado.

#### Scenario: Escolher um atalho
- **WHEN** o usuário escolhe "Hoje"
- **THEN** a API recebe `from` e `to`, a URL passa a ter `?periodo=hoje` e o atalho fica marcado como ativo

#### Scenario: Sem período
- **WHEN** a URL não tem `periodo`, `de` nem `ate`
- **THEN** a API recebe `from` igual ao instante atual e não recebe `to`

#### Scenario: Atalho desconhecido
- **WHEN** a URL tem `?periodo=amanha`
- **THEN** nenhum atalho fica ativo e a requisição se comporta como sem período, sem erro

### Requirement: Janelas dos atalhos de período
"Hoje" SHALL cobrir de 00:00 a 23:59 do dia atual; "Este fim de semana", de sábado 00:00 a domingo 23:59 do fim de semana corrente; "Próximos 7 dias", de hoje 00:00 a 23:59 do sexto dia seguinte (7 dias contando hoje).

#### Scenario: Este fim de semana numa quarta
- **WHEN** o usuário escolhe "Este fim de semana" numa quarta-feira
- **THEN** o intervalo vai do sábado seguinte 00:00 ao domingo seguinte 23:59

#### Scenario: Este fim de semana num sábado
- **WHEN** o usuário escolhe "Este fim de semana" num sábado
- **THEN** o intervalo é o do próprio sábado 00:00 ao domingo seguinte 23:59

#### Scenario: Este fim de semana num domingo
- **WHEN** o usuário escolhe "Este fim de semana" num domingo
- **THEN** o intervalo é o do sábado anterior 00:00 ao domingo de hoje 23:59

#### Scenario: Próximos 7 dias
- **WHEN** o usuário escolhe "Próximos 7 dias" numa terça-feira
- **THEN** o intervalo vai de terça 00:00 até a segunda-feira seguinte 23:59

### Requirement: Início mínimo do período
Em qualquer período, `from` SHALL ser no mínimo o instante atual, para que sessões já passadas não apareçam.

#### Scenario: Hoje à tarde
- **WHEN** o usuário escolhe "Hoje" às 15:00 no fuso da cidade
- **THEN** `from` corresponde às 15:00 e não a 00:00

#### Scenario: Período inteiramente no futuro
- **WHEN** o usuário escolhe "Este fim de semana" numa quarta-feira
- **THEN** `from` é o sábado 00:00, não o instante atual

### Requirement: Intervalo personalizado de datas
A lista SHALL oferecer campos de data inicial (`de`) e final (`ate`); o intervalo SHALL ir de `de` 00:00 a `ate` 23:59, aceitando também apenas um dos dois.

#### Scenario: Intervalo completo
- **WHEN** o usuário informa de `2099-03-01` até `2099-03-03`
- **THEN** a API recebe `from=2099-03-01T00:00:00-03:00` e `to=2099-03-03T23:59:00-03:00`, e a URL tem `?de=2099-03-01&ate=2099-03-03`

#### Scenario: Apenas data inicial
- **WHEN** o usuário informa só `de=2099-03-01`
- **THEN** a API recebe `from` dessa data às 00:00 e não recebe `to`

#### Scenario: Apenas data final
- **WHEN** o usuário informa só `ate=2099-03-03`
- **THEN** a API recebe `from` igual ao instante atual e `to` dessa data às 23:59

#### Scenario: Início depois do fim
- **WHEN** o usuário informa `de` posterior a `ate`
- **THEN** o intervalo é ignorado (`from` é o instante atual, `to` não é enviado) e o formulário exibe "A data inicial deve ser anterior à final"

#### Scenario: Data final anterior a hoje
- **WHEN** `ate` é anterior ao dia atual no fuso da cidade (ex.: `?ate=2099-03-10` quando hoje é 11/03/2099), com ou sem `de`
- **THEN** o intervalo é ignorado como o invertido: `to` não é enviado, `from` é o instante atual e o formulário exibe "A data inicial deve ser anterior à final"

### Requirement: Intervalos no fuso da cidade
Os intervalos de período SHALL ser calculados por funções puras que recebem `now` e `timeZone` (`City.timezone`) e enviados em ISO 8601 com offset do fuso da cidade.

#### Scenario: Virada de dia no fuso da cidade
- **WHEN** o usuário escolhe "Hoje" e, em `America/Recife` (UTC-3), são 22:30 de 10/03/2099, embora em UTC já seja 11/03 01:30
- **THEN** o dia considerado é 10/03 e `to` é `2099-03-10T23:59:00-03:00`

#### Scenario: Fuso da cidade indisponível
- **WHEN** `GET /cities/{uf}/{slug}` falha e o fuso não está disponível
- **THEN** a página exibe `ErrorMessage` e nenhum intervalo é calculado com outro fuso

### Requirement: Parâmetros de filtro na URL
Os filtros SHALL ser refletidos na query string junto com `?page=`: `q` (texto da busca), `categoria` (slug), `periodo` (`hoje`, `fim-de-semana` ou `7-dias`) e `de`/`ate` (`AAAA-MM-DD`).

#### Scenario: Filtros e página juntos
- **WHEN** o usuário acessa `?categoria=shows&periodo=hoje&page=2`
- **THEN** a API recebe `category=shows`, `from`/`to` de hoje e `page=1`

#### Scenario: Próxima página mantém filtros
- **WHEN** há `?categoria=shows&q=rock` e o usuário aciona "Próxima"
- **THEN** a URL passa a `?categoria=shows&q=rock&page=2` e a API recebe `category=shows`, `q=rock` e `page=1`

### Requirement: Valores inválidos nos filtros da URL
Valores inválidos de `categoria`, `periodo`, `de` ou `ate` SHALL ser ignorados sem erro; se `periodo` e `de`/`ate` vierem juntos, `periodo` prevalece.

#### Scenario: Valores malformados
- **WHEN** a URL tem `?periodo=amanha`, `?de=2099-02-31` ou `?ate=abc`
- **THEN** esses filtros são ignorados, a requisição não os contém e não há erro

#### Scenario: Período e intervalo juntos
- **WHEN** a URL tem `?periodo=hoje&de=2099-03-01&ate=2099-03-03`
- **THEN** vale `periodo=hoje` e `de`/`ate` são ignorados

### Requirement: Volta à primeira página ao mudar filtro
Mudar qualquer filtro SHALL remover `page` da URL, exibindo a primeira página.

#### Scenario: Mudar categoria na página 3
- **WHEN** o usuário está em `?page=3` e escolhe a categoria "Teatro"
- **THEN** a URL passa a `?categoria=teatro` (sem `page`) e a API recebe `page=0`

#### Scenario: Mudar período na página 2
- **WHEN** o usuário está em `?categoria=shows&page=2` e escolhe "Hoje"
- **THEN** a URL passa a `?categoria=shows&periodo=hoje` e a API recebe `page=0`

### Requirement: Limpar filtros
A lista SHALL exibir o botão "Limpar filtros" apenas com filtro ou busca ativos; acioná-lo SHALL remover `q`, `categoria`, `periodo`, `de`, `ate` e `page` da URL e esvaziar o campo de busca.

#### Scenario: Limpar com filtros e página
- **WHEN** há filtros, `?q=rock` e `?page=2` e o usuário aciona "Limpar filtros"
- **THEN** a URL fica sem parâmetros, o campo de busca fica vazio e a API recebe `from` igual ao instante atual, sem `q`, `category` nem `to`, e `page=0`

#### Scenario: Sem filtros ativos
- **WHEN** a URL não tem filtros nem busca válidos
- **THEN** o botão "Limpar filtros" não é exibido

#### Scenario: Só a busca ativa
- **WHEN** a URL tem apenas `?q=rock`
- **THEN** o botão "Limpar filtros" é exibido e, ao acioná-lo, `q` sai da URL

### Requirement: Estado vazio com filtro ativo
Quando a API responder HTTP 200 com `items` vazio e houver filtro ativo, a lista SHALL exibir "Nenhum evento para esses filtros" e uma ação para limpar os filtros. Com busca ativa, SHALL exibir `Nenhum evento para "<termo>"`, em vez de "Nenhum evento encontrado.".

#### Scenario: Filtro sem resultados
- **WHEN** há `?categoria=teatro` e a API responde HTTP 200 com `items` vazio
- **THEN** a página exibe "Nenhum evento para esses filtros" e a ação "Limpar filtros"

#### Scenario: Busca sem resultados
- **WHEN** há `?q=xyzabc` (com ou sem outros filtros) e a API responde HTTP 200 com `items` vazio
- **THEN** a página exibe `Nenhum evento para "xyzabc"` e a ação "Limpar filtros"

#### Scenario: Limpar a partir do estado vazio
- **WHEN** o usuário aciona a ação de limpar no estado vazio
- **THEN** os filtros, a busca e `page` saem da URL e a lista é requisitada sem `q`, `category` e `to`

#### Scenario: Página além do total com filtro
- **WHEN** a URL tem `?categoria=shows&page=99` e a API responde HTTP 200 com `items` vazio
- **THEN** a página exibe "Nenhum evento para esses filtros", e "Anterior" e "Limpar filtros" permanecem disponíveis

### Requirement: Campo de busca na lista
A lista SHALL exibir, acima dos filtros, um campo de busca rotulado "Buscar eventos" com botão "Buscar". A busca é aplicada ao enviar (Enter ou botão) e nunca a cada tecla; aplicar envia `q` (sem espaços nas pontas) a `GET /events`, grava `?q=` na URL e volta à página 1.

#### Scenario: Aplicar com Enter
- **WHEN** o usuário digita "  musica " e pressiona Enter
- **THEN** a URL passa a `?q=musica` (sem `page`) e a API recebe `q=musica` e `page=0`

#### Scenario: Digitar não busca
- **WHEN** o usuário digita no campo sem enviar
- **THEN** nenhuma nova requisição a `GET /events` é feita e a URL não muda

#### Scenario: Busca na página 3
- **WHEN** o usuário está em `?categoria=shows&page=3` e busca "rock"
- **THEN** a URL passa a `?categoria=shows&q=rock` e a API recebe `q=rock`, `category=shows` e `page=0`

#### Scenario: Termo curto demais
- **WHEN** o usuário envia 1 caractere (após remover espaços)
- **THEN** o campo exibe a mensagem "Digite ao menos 2 caracteres", `q` não é aplicado e nenhuma requisição nova é feita

#### Scenario: Campo vazio remove a busca
- **WHEN** há `?q=rock` e o usuário apaga o texto e envia
- **THEN** `q` sai da URL, a API deixa de receber `q` e a página 1 é exibida

### Requirement: Valor de busca na URL
O front-end SHALL ler a busca de `?q=` e preencher o campo com ela. Um `q` com menos de 2 ou mais de 100 caracteres após trim SHALL ser ignorado sem erro, e a API só recebe `q` válido.

#### Scenario: Link com busca
- **WHEN** o usuário acessa `?q=musica&categoria=shows&page=2`
- **THEN** o campo mostra "musica" e a API recebe `q=musica`, `category=shows` e `page=1`

#### Scenario: q inválido na URL
- **WHEN** a URL tem `?q=a`, `?q=%20%20` ou `q` com 101 caracteres
- **THEN** `q` não é enviado, o campo fica vazio e a lista carrega sem erro

#### Scenario: Erro de busca rejeitado pela API
- **WHEN** a API responde HTTP 400 com `Problem` (`status=400`, `detail`) a uma requisição com `q`
- **THEN** a página exibe `ErrorMessage` com o `detail`, sem lista
