## Purpose
A capability `event-admin` define a área administrativa do front-end para gerenciar eventos da cidade padrão (listar, criar, editar e excluir), usando apenas a API de eventos existente.

## ADDED Requirements

### Requirement: Tabela administrativa de eventos
O front-end SHALL exibir em `/admin/eventos` uma tabela paginada com os eventos da cidade padrão em todos os status, obtidos de `GET /api/v1/events` sem o filtro `status`, mostrando título, status e próxima sessão (no fuso da cidade), com as ações editar e excluir por linha, estados de carregando, vazio e erro (`ErrorMessage`) e paginação por `?page=` (base 1).

#### Scenario: Lista com todos os status
- **WHEN** a API responde HTTP 200 com eventos `DRAFT`, `PUBLISHED`, `CANCELLED` e `ARCHIVED`
- **THEN** a tabela mostra uma linha por evento com título, status e próxima sessão, e a requisição não envia o parâmetro `status`

#### Scenario: Evento sem sessão futura
- **WHEN** um evento não tem sessão futura
- **THEN** a coluna de próxima sessão mostra "Sem sessões futuras"

#### Scenario: Nenhum evento
- **WHEN** a API responde HTTP 200 com `items` vazio na primeira página
- **THEN** a página mostra a mensagem de estado vazio e um link para criar evento

#### Scenario: Erro da API
- **WHEN** a API responde HTTP 500 com `Problem` contendo `detail`
- **THEN** a página exibe `ErrorMessage` com o `detail`, sem tabela

#### Scenario: Cidade padrão indisponível
- **WHEN** `GET /cities/default` responde HTTP 500 com `Problem`
- **THEN** a página exibe `ErrorMessage` com o `detail`

### Requirement: Exclusão com confirmação na página
Ao acionar "Excluir" em uma linha, o front-end SHALL exibir uma confirmação na própria página (sem `window.confirm`) e SHALL enviar `DELETE /api/v1/events/{id}` somente após a confirmação, atualizando a tabela em seguida.

#### Scenario: Excluir confirmado
- **WHEN** o usuário aciona "Excluir" e depois "Confirmar", e a API responde HTTP 204
- **THEN** o evento some da tabela

#### Scenario: Excluir cancelado
- **WHEN** o usuário aciona "Excluir" e depois "Cancelar"
- **THEN** nenhuma requisição `DELETE` é enviada e a linha permanece

#### Scenario: Evento já removido
- **WHEN** a confirmação é enviada e a API responde HTTP 404 com `Problem`
- **THEN** a página exibe o `detail` do `Problem` e atualiza a tabela

### Requirement: Formulário único de evento
O front-end SHALL oferecer `/admin/eventos/novo` e `/admin/eventos/:id/editar` com o mesmo formulário, com os campos título, slug (opcional), categoria, local (opcional), descrição, status, classificação etária, gratuito, preço mínimo, preço máximo, link de ingresso, imagem (URL) e sessões; na edição SHALL carregar o evento de `GET /api/v1/events/{id}`.

#### Scenario: Formulário de criação
- **WHEN** o usuário acessa `/admin/eventos/novo`
- **THEN** o formulário abre vazio, com status `DRAFT` e nenhuma sessão

#### Scenario: Formulário de edição
- **WHEN** o usuário acessa `/admin/eventos/{id}/editar` e a API responde HTTP 200
- **THEN** todos os campos aparecem preenchidos com os valores do evento

#### Scenario: Evento a editar não existe
- **WHEN** `GET /events/{id}` responde HTTP 404 com `Problem`
- **THEN** a página exibe `ErrorMessage` com o `detail` e não exibe o formulário

#### Scenario: Evento de outra cidade
- **WHEN** o evento carregado tem `cityId` diferente do id da cidade padrão
- **THEN** o formulário não é exibido e a página mostra "Este evento pertence a outra cidade e não pode ser editado aqui."

### Requirement: Opções de categoria e de local
O seletor de categoria SHALL listar as categorias de `GET /api/v1/categories`, com as subcategorias indentadas sob a categoria pai, e o seletor de local SHALL listar os locais de `GET /api/v1/venues` da cidade padrão (`size=100`), com a opção de não informar local.

#### Scenario: Subcategorias indentadas
- **WHEN** a árvore de categorias tem uma raiz com duas filhas
- **THEN** o select mostra a raiz e, abaixo dela, as filhas com recuo visual, todas selecionáveis

#### Scenario: Falha ao carregar as opções
- **WHEN** `GET /categories` ou `GET /venues` responde HTTP 500 com `Problem`
- **THEN** o formulário exibe `ErrorMessage` com o `detail` e não permite enviar

### Requirement: Preço em reais e gratuidade
O formulário SHALL receber os preços em reais (aceitando vírgula ou ponto decimal, até 2 casas) e enviá-los como `priceMinCents` e `priceMaxCents` inteiros; marcar "Gratuito" SHALL enviar `isFree=true` e desabilitar os campos de preço, que são enviados nulos.

#### Scenario: Conversão para centavos
- **WHEN** o usuário informa mínimo "25,50" e máximo "80"
- **THEN** a requisição envia `priceMinCents=2550` e `priceMaxCents=8000`

#### Scenario: Gratuito
- **WHEN** o usuário marca "Gratuito"
- **THEN** os campos de preço ficam desabilitados e a requisição envia `isFree=true` com preços nulos

#### Scenario: Valor de preço inválido
- **WHEN** o usuário informa "abc" ou "10,999" como preço
- **THEN** o campo mostra erro e nenhuma requisição é enviada

### Requirement: Sessões no fuso da cidade
O formulário SHALL manter uma lista dinâmica de sessões (adicionar e remover), cada uma com início (obrigatório), fim (opcional) e situação (`SCHEDULED`, `CANCELLED` ou `SOLD_OUT`); data e hora são digitadas no fuso da cidade padrão e enviadas em ISO 8601 com offset, e na edição os valores existentes (inclusive fim e situação) são preservados.

#### Scenario: Conversão de fuso
- **WHEN** o fuso da cidade é `America/Recife` (UTC-3) e o usuário informa início `2099-03-01T17:00`
- **THEN** a requisição envia `startsAt` equivalente a `2099-03-01T20:00:00Z`

#### Scenario: Edição preserva a sessão
- **WHEN** o evento tem uma sessão com `endsAt` e status `SOLD_OUT` e o usuário edita só o título
- **THEN** o `PUT` reenvia essa sessão com o mesmo `startsAt`, `endsAt` e `status`

#### Scenario: Remover sessão
- **WHEN** o usuário remove uma sessão e salva
- **THEN** o `PUT` não contém essa sessão

#### Scenario: Início repetido
- **WHEN** duas sessões têm o mesmo início
- **THEN** o formulário mostra erro nas sessões repetidas e não envia

### Requirement: Validação no cliente espelhando o contrato
O formulário SHALL validar antes de enviar as regras do contrato: título obrigatório (não em branco, até 255 caracteres), categoria obrigatória, slug com padrão `^[a-z0-9]+(-[a-z0-9]+)*$` e até 255 caracteres, descrição até 20000, classificação até 10, links `http(s)` até 2048, preço mínimo menor ou igual ao máximo, preços não negativos, fim não anterior ao início, e ao menos uma sessão futura quando o status é `PUBLISHED`; a validação do servidor SHALL continuar sendo a autoridade.

#### Scenario: Campos obrigatórios
- **WHEN** o usuário envia o formulário sem título e sem categoria
- **THEN** os dois campos mostram erro e nenhuma requisição é enviada

#### Scenario: Preço mínimo maior que o máximo
- **WHEN** mínimo é "90" e máximo é "50"
- **THEN** o campo de preço máximo mostra erro e nada é enviado

#### Scenario: Publicado exige sessão futura
- **WHEN** o status é `PUBLISHED` e não há sessão futura
- **THEN** a lista de sessões mostra erro e nada é enviado

#### Scenario: Rascunho sem sessões
- **WHEN** o status é `DRAFT` e não há sessões
- **THEN** o formulário é enviado sem erro de sessões

#### Scenario: Servidor rejeita o que o cliente aceitou
- **WHEN** a validação do cliente passa e a API responde HTTP 422
- **THEN** os erros do servidor são exibidos, como no requisito de mapeamento de erros 422

### Requirement: Criação de evento
Ao enviar o formulário em `/admin/eventos/novo`, o front-end SHALL enviar `POST /api/v1/events` com `cityId` da cidade padrão e, em caso de HTTP 201, navegar para `/admin/eventos`.

#### Scenario: Criação bem-sucedida
- **WHEN** o formulário é válido e a API responde HTTP 201
- **THEN** o usuário volta a `/admin/eventos` e a tabela mostra o novo evento

#### Scenario: Slug já existente
- **WHEN** a API responde HTTP 409 com `Problem` (slug já existe na cidade)
- **THEN** o formulário permanece preenchido e mostra o `detail` no topo, sem o aviso de concorrência

#### Scenario: Envio duplicado
- **WHEN** o usuário aciona "Salvar" enquanto o envio está pendente
- **THEN** o botão fica desabilitado e só uma requisição é enviada

### Requirement: Edição com lock otimista
Ao enviar o formulário em `/admin/eventos/:id/editar`, o front-end SHALL enviar `PUT /api/v1/events/{id}` com o `version` carregado e todos os campos do formulário. Em HTTP 409, SHALL recarregar o evento e, se a `version` do servidor for diferente da enviada, exibir o aviso "O evento foi alterado por outra pessoa" com a ação "Recarregar" que substitui os valores do formulário pelos do servidor.

#### Scenario: Edição bem-sucedida
- **WHEN** o formulário é válido e a API responde HTTP 200
- **THEN** o usuário volta a `/admin/eventos`

#### Scenario: Conflito de concorrência
- **WHEN** a API responde HTTP 409 e o evento recarregado tem `version` diferente da enviada
- **THEN** o aviso "O evento foi alterado por outra pessoa" aparece com o botão "Recarregar", e os valores digitados são mantidos até o clique

#### Scenario: Recarregar
- **WHEN** o usuário aciona "Recarregar" após o aviso
- **THEN** o formulário passa a mostrar os valores do servidor e a nova `version` é usada no próximo envio

#### Scenario: Conflito que não é de versão
- **WHEN** a API responde HTTP 409 e a `version` do evento recarregado é igual à enviada (ex.: slug já existe)
- **THEN** o aviso de concorrência não aparece e o `detail` do `Problem` é exibido no topo

### Requirement: Mapeamento de erros 422 para o formulário
Em HTTP 422, o front-end SHALL associar cada item de `errors[]` ao campo correspondente pelo nome (`title`, `slug`, `categoryId`, `venueId`, `description`, `status`, `ageRating`, `priceMinCents`, `priceMaxCents`, `isFree`, `ticketUrl`, `imageUrl`, `occurrences`, `occurrences[i].startsAt`, `occurrences[i].endsAt`, `occurrences[i].status`) e SHALL exibir no topo, em `ErrorMessage`, o `detail` e os erros cujo `field` não corresponda a nenhum campo.

#### Scenario: Erro por campo
- **WHEN** a API responde HTTP 422 com `errors=[{field:"title", message:"..."}]`
- **THEN** a mensagem aparece junto ao campo título e o formulário permanece preenchido

#### Scenario: Erro em sessão
- **WHEN** `errors` contém `occurrences[1].endsAt`
- **THEN** a mensagem aparece na segunda sessão, no campo de fim

#### Scenario: Campo de preço
- **WHEN** `errors` contém `priceMaxCents`
- **THEN** a mensagem aparece junto ao preço máximo, que é o campo em reais

#### Scenario: Erro sem campo correspondente
- **WHEN** `errors` contém um `field` desconhecido (ex.: `cityId`)
- **THEN** a mensagem aparece no topo do formulário

### Requirement: Acesso discreto à área administrativa
O cabeçalho do layout SHALL exibir um link discreto "Admin" para `/admin/eventos`. Esta fase NÃO inclui autenticação nem controle de acesso às rotas `/admin`.

#### Scenario: Link no cabeçalho
- **WHEN** qualquer página é exibida no layout
- **THEN** o cabeçalho contém o link "Admin" apontando para `/admin/eventos`

#### Scenario: Rota administrativa não colide com rota de cidade
- **WHEN** o usuário acessa `/admin/eventos`
- **THEN** a tabela administrativa é exibida e não a lista pública de uma cidade `admin/eventos`

### Requirement: Administração sem cidade fixa no código
O código-fonte e os testes da área administrativa SHALL NOT citar uma cidade real; os testes usam MSW com a cidade fictícia `zz/cidade-teste`.

#### Scenario: Busca por literais de cidade
- **WHEN** `frontend/src` é inspecionado
- **THEN** não há ocorrência de "São Paulo" nem "sao-paulo" (exceto o arquivo gerado do contrato)

#### Scenario: Requisição sem handler
- **WHEN** um teste da administração faz uma requisição sem handler MSW
- **THEN** o teste falha
