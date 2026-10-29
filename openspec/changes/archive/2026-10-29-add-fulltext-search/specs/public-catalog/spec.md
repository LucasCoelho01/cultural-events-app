## ADDED Requirements

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

## MODIFIED Requirements

### Requirement: Lista pública de eventos da cidade
O front-end SHALL exibir em `/:uf/:cidade` os eventos da cidade obtidos de `GET /api/v1/events?city={uf}/{slug}&status=PUBLISHED&from=<início>&page=<n>`, acrescentando `category`, `to` e `q` quando houver filtros ativos, paginados com botões "Anterior" e "Próxima" controlados por `PageMetadata` (`page`, `totalPages`).

#### Scenario: Lista com eventos
- **WHEN** a API responde HTTP 200 com eventos
- **THEN** a página exibe um card por evento, na ordem recebida

#### Scenario: Navegação entre páginas
- **WHEN** `PageMetadata.totalPages` é maior que 1 e o usuário aciona "Próxima"
- **THEN** a página seguinte é requisitada (`page` incrementado), "Anterior" fica desabilitado na primeira página e "Próxima" na última, e os filtros ativos, inclusive a busca, são mantidos na URL e na requisição

### Requirement: Parâmetros de filtro na URL
Os filtros SHALL ser refletidos na query string junto com `?page=`: `q` (texto da busca), `categoria` (slug), `periodo` (`hoje`, `fim-de-semana` ou `7-dias`) e `de`/`ate` (`AAAA-MM-DD`).

#### Scenario: Filtros e página juntos
- **WHEN** o usuário acessa `?categoria=shows&periodo=hoje&page=2`
- **THEN** a API recebe `category=shows`, `from`/`to` de hoje e `page=1`

#### Scenario: Próxima página mantém filtros
- **WHEN** há `?categoria=shows&q=rock` e o usuário aciona "Próxima"
- **THEN** a URL passa a `?categoria=shows&q=rock&page=2` e a API recebe `category=shows`, `q=rock` e `page=1`

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
