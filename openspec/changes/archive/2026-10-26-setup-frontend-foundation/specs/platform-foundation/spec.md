## ADDED Requirements

### Requirement: Build e qualidade do módulo frontend
O projeto `frontend/` SHALL ser um app Vite + React + TypeScript com `strict` habilitado, gerenciado por npm sob Node 24, com React Router, TanStack Query e Tailwind CSS, e SHALL fornecer os scripts `dev`, `build`, `test`, `lint` e `format`, usando ESLint, Prettier e Vitest com React Testing Library.

#### Scenario: Build local bem-sucedido
- **WHEN** `npm run build` é executado em `frontend/` após `npm install`
- **THEN** a geração de tipos roda antes, o `tsc` em modo strict passa e o build do Vite termina com sucesso

#### Scenario: Build falha com erro de tipo
- **WHEN** o código contém um erro de tipo (ex.: uso de campo inexistente em um tipo gerado)
- **THEN** `npm run build` termina com código de saída diferente de zero, indicando arquivo e linha do erro

#### Scenario: Suíte de testes falha com teste quebrado
- **WHEN** `npm test` é executado e um teste falha
- **THEN** o Vitest termina com código de saída diferente de zero, listando o teste que falhou

### Requirement: Cliente HTTP contract-first gerado do OpenAPI
O front-end SHALL gerar os tipos TypeScript de `../api/openapi.yaml` com `openapi-typescript` via o script npm `generate:api`, executado automaticamente antes de `dev`, `build` e `test`, com o arquivo gerado fora do controle de versão, e SHALL chamar a API por `openapi-fetch` tipado por esses tipos, com wrappers finos de TanStack Query por recurso em `src/api/`.

#### Scenario: Tipos gerados antes dos scripts principais
- **WHEN** `npm run dev`, `npm run build` ou `npm test` é executado em um clone limpo
- **THEN** `generate:api` roda primeiro e produz o arquivo de tipos, que é ignorado pelo git

#### Scenario: Geração falha com contrato inválido
- **WHEN** `api/openapi.yaml` está ausente ou é um documento OpenAPI inválido
- **THEN** `generate:api` termina com código diferente de zero e o script que o disparou (`dev`, `build` ou `test`) não prossegue

#### Scenario: Chamada tipada pelo contrato
- **WHEN** um wrapper em `src/api/` consome `GET /cities/default`
- **THEN** o dado retornado é tipado como o schema `City` e o erro como o schema `Problem`, sem tipos escritos à mão

### Requirement: Proxy de desenvolvimento para a API
O servidor de desenvolvimento do Vite SHALL encaminhar requisições `/api` para `http://localhost:8080`, de modo que o front-end chame a API em mesma origem e o back-end não precise configurar CORS.

#### Scenario: Requisição /api encaminhada ao back-end
- **WHEN** o app em `npm run dev` requisita `/api/v1/cities/default` com o back-end rodando em `localhost:8080`
- **THEN** o Vite encaminha a requisição ao back-end e devolve sua resposta sem exigir cabeçalhos CORS

#### Scenario: Back-end indisponível
- **WHEN** o back-end não está rodando em `localhost:8080`
- **THEN** o proxy responde com erro de gateway (HTTP 5xx) e o app exibe o componente de erro padrão em vez de uma tela em branco

### Requirement: Layout base e tratamento padrão de erros
O front-end SHALL renderizar um layout com cabeçalho (nome do app e cidade atual obtida de `GET /api/v1/cities/default`), área de conteúdo e uma página 404 para rotas desconhecidas, e SHALL fornecer um componente de erro padrão que exibe o campo `detail` de um `Problem` (RFC 7807) e recorre a uma mensagem genérica quando a resposta não for um `Problem`.

#### Scenario: Cabeçalho exibe a cidade atual
- **WHEN** o layout é renderizado e `GET /api/v1/cities/default` responde HTTP 200 com uma `City`
- **THEN** o cabeçalho exibe o nome do app e o nome e a UF da cidade retornada

#### Scenario: Erro Problem exibe o detail
- **WHEN** uma chamada responde HTTP 500 com corpo `Problem` contendo `detail`
- **THEN** o componente de erro padrão exibe o texto de `detail`

#### Scenario: Erro sem corpo Problem usa mensagem genérica
- **WHEN** a chamada falha por rede ou responde com corpo que não é um `Problem`
- **THEN** o componente de erro padrão exibe uma mensagem genérica e não expõe detalhes técnicos

#### Scenario: Rota desconhecida exibe 404
- **WHEN** o usuário acessa um caminho que não corresponde a nenhuma rota (ex.: `/a/b/c/d`)
- **THEN** a página 404 é exibida dentro do layout

### Requirement: Rotas por cidade sem cidade fixa no código
O front-end SHALL expor rotas no formato `/:uf/:cidade` (ex.: `/sp/sao-paulo`) e SHALL redirecionar `/` para a rota da cidade padrão obtida de `GET /api/v1/cities/default`, sem que nenhum código-fonte, teste ou configuração do front-end presuma uma cidade específica.

#### Scenario: Raiz redireciona para a cidade padrão
- **WHEN** o usuário acessa `/` e a API responde com a cidade padrão de `uf` e `slug` definidos
- **THEN** o app navega para `/{uf em minúsculas}/{slug}` substituindo a entrada do histórico

#### Scenario: Falha ao obter a cidade padrão
- **WHEN** o usuário acessa `/` e `GET /api/v1/cities/default` responde HTTP 500 com um `Problem`
- **THEN** não há redirecionamento e o componente de erro padrão exibe o `detail` do `Problem`

#### Scenario: Cidade da rota não existe
- **WHEN** o usuário acessa `/xx/inexistente` e `GET /api/v1/cities/xx/inexistente` responde HTTP 404 com um `Problem`
- **THEN** o componente de erro padrão exibe o `detail` do `Problem`

#### Scenario: Nenhuma referência fixa à cidade
- **WHEN** o código-fonte de `frontend/src` é inspecionado
- **THEN** não há ocorrência literal de "São Paulo" nem "sao-paulo"; os testes usam cidades fictícias fornecidas pelo mock da API
