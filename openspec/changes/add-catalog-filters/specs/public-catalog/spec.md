## MODIFIED Requirements

### Requirement: Lista pública de eventos da cidade
O front-end SHALL exibir em `/:uf/:cidade` os eventos da cidade obtidos de `GET /api/v1/events?city={uf}/{slug}&status=PUBLISHED&from=<início>&page=<n>`, acrescentando `category` e `to` quando houver filtros ativos, paginados com botões "Anterior" e "Próxima" controlados por `PageMetadata` (`page`, `totalPages`), e SHALL mostrar estados de carregando, vazio (nenhum evento) e erro (componente `ErrorMessage`, exibindo o `detail` do `Problem`). Sem filtro de período, `from` SHALL ser o instante atual em ISO 8601; com filtro de período, `from` e `to` SHALL seguir os requisitos de período.

#### Scenario: Lista com eventos
- **WHEN** a API responde HTTP 200 com eventos
- **THEN** a página exibe um card por evento, na ordem recebida

#### Scenario: Navegação entre páginas
- **WHEN** `PageMetadata.totalPages` é maior que 1 e o usuário aciona "Próxima"
- **THEN** a página seguinte é requisitada (`page` incrementado), "Anterior" fica desabilitado na primeira página e "Próxima" na última, e os filtros ativos são mantidos na URL e na requisição

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

## ADDED Requirements

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
- **WHEN** a URL tem `?categoria=inexistente`
- **THEN** o filtro é ignorado, o select mostra "Todas", `category` não é enviado e não há erro

#### Scenario: Falha ao carregar categorias
- **WHEN** `GET /categories` responde HTTP 500
- **THEN** a lista de eventos continua funcional, sem o select de categorias e sem derrubar a página

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

### Requirement: Intervalos no fuso da cidade
Os intervalos de período SHALL ser calculados por funções puras que recebem `now` e `timeZone` (`City.timezone`) e enviados em ISO 8601 com offset do fuso da cidade.

#### Scenario: Virada de dia no fuso da cidade
- **WHEN** o usuário escolhe "Hoje" e, em `America/Recife` (UTC-3), são 22:30 de 10/03/2099, embora em UTC já seja 11/03 01:30
- **THEN** o dia considerado é 10/03 e `to` é `2099-03-10T23:59:00-03:00`

#### Scenario: Fuso da cidade indisponível
- **WHEN** `GET /cities/{uf}/{slug}` falha e o fuso não está disponível
- **THEN** a página exibe `ErrorMessage` e nenhum intervalo é calculado com outro fuso

### Requirement: Parâmetros de filtro na URL
Os filtros SHALL ser refletidos na query string junto com `?page=`: `categoria` (slug), `periodo` (`hoje`, `fim-de-semana` ou `7-dias`) e `de`/`ate` (`AAAA-MM-DD`).

#### Scenario: Filtros e página juntos
- **WHEN** o usuário acessa `?categoria=shows&periodo=hoje&page=2`
- **THEN** a API recebe `category=shows`, `from`/`to` de hoje e `page=1`

#### Scenario: Próxima página mantém filtros
- **WHEN** há `?categoria=shows` e o usuário aciona "Próxima"
- **THEN** a URL passa a `?categoria=shows&page=2` e a API recebe `category=shows` e `page=1`

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
A lista SHALL exibir o botão "Limpar filtros" apenas com filtro ativo; acioná-lo SHALL remover `categoria`, `periodo`, `de`, `ate` e `page` da URL.

#### Scenario: Limpar com filtros e página
- **WHEN** há filtros e `?page=2` e o usuário aciona "Limpar filtros"
- **THEN** a URL fica sem parâmetros e a API recebe `from` igual ao instante atual, sem `category` nem `to`, e `page=0`

#### Scenario: Sem filtros ativos
- **WHEN** a URL não tem filtros válidos
- **THEN** o botão "Limpar filtros" não é exibido

### Requirement: Estado vazio com filtro ativo
Quando a API responder HTTP 200 com `items` vazio e houver filtro ativo, a lista SHALL exibir "Nenhum evento para esses filtros" e uma ação para limpar os filtros, em vez de "Nenhum evento encontrado.".

#### Scenario: Filtro sem resultados
- **WHEN** há `?categoria=teatro` e a API responde HTTP 200 com `items` vazio
- **THEN** a página exibe "Nenhum evento para esses filtros" e a ação "Limpar filtros"

#### Scenario: Limpar a partir do estado vazio
- **WHEN** o usuário aciona a ação de limpar no estado vazio
- **THEN** os filtros e `page` saem da URL e a lista é requisitada sem `category` e `to`

#### Scenario: Página além do total com filtro
- **WHEN** a URL tem `?categoria=shows&page=99` e a API responde HTTP 200 com `items` vazio
- **THEN** a página exibe "Nenhum evento para esses filtros", e "Anterior" e "Limpar filtros" permanecem disponíveis
