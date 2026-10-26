# platform-foundation Specification

## Purpose
A capability `platform-foundation` fornece a base técnica compartilhada do back-end — build, contrato HTTP, tratamento de erros, paginação, health check, convenção de schema e regra arquitetural — sobre a qual todas as demais capabilities do sistema de eventos culturais são construídas.

## Requirements

### Requirement: Build do módulo backend com Maven Wrapper
O projeto `backend/` SHALL ser construído via Maven Wrapper, com Java 25 e Spring Boot 4.1.x, produzindo um artefato executável sem exigir Maven pré-instalado na máquina.

#### Scenario: Build local bem-sucedido
- **WHEN** `./mvnw clean verify` é executado dentro de `backend/`
- **THEN** o build termina com sucesso, compilando com Java 25 e empacotando o artefato Spring Boot 4.1.x

#### Scenario: Build falha com versão de Java incompatível
- **WHEN** o build é executado com um JDK diferente da versão configurada (25)
- **THEN** o build falha antes da compilação, reportando a versão de Java exigida

### Requirement: Geração de interfaces a partir do contrato OpenAPI
O backend SHALL gerar interfaces de controller e DTOs a partir de `api/openapi.yaml` durante o build Maven, usando `openapi-generator-maven-plugin` com gerador `spring`, `interfaceOnly=true` e `useTags=true`, com saída em `target/generated-sources`, sem que código gerado seja versionado no repositório.

#### Scenario: Geração bem-sucedida durante o build
- **WHEN** a fase de build que dispara a geração de código é executada (ex.: `./mvnw generate-sources`)
- **THEN** interfaces e DTOs correspondentes aos componentes de `api/openapi.yaml` são gerados em `target/generated-sources`

#### Scenario: Build falha com contrato OpenAPI inválido
- **WHEN** `api/openapi.yaml` contém um documento inválido (YAML malformado ou violação do schema OpenAPI 3.1)
- **THEN** a fase de geração de código falha e o build é interrompido antes da compilação

### Requirement: Contrato HTTP compartilhado — Problem e PageMetadata
`api/openapi.yaml` SHALL declarar, com servidor `/api/v1`, os componentes compartilhados `Problem` (RFC 7807: `type`, `title`, `status`, `detail`, `instance`, mais uma lista de erros por campo para 422) e `PageMetadata` (`page`, `size`, `totalElements`, `totalPages`), disponíveis para reuso por todo endpoint, de modo que paths de negócio declarados por cada capability reutilizem esses componentes em vez de definir formatos próprios de erro e paginação.

#### Scenario: Componentes compartilhados disponíveis no contrato
- **WHEN** `api/openapi.yaml` é validado como documento OpenAPI 3.1
- **THEN** os componentes `Problem` e `PageMetadata` existem em `components.schemas` com os campos especificados

#### Scenario: Contrato inválido é rejeitado
- **WHEN** o componente `Problem` ou `PageMetadata` está ausente, ou um de seus campos obrigatórios não está definido
- **THEN** a validação do contrato falha, indicando o componente e o campo ausente

#### Scenario: Path de negócio reutiliza os componentes compartilhados
- **WHEN** uma capability declara um path de listagem paginada ou de erro em `api/openapi.yaml`
- **THEN** a resposta paginada referencia `PageMetadata` e as respostas de erro referenciam `Problem` via `$ref`

### Requirement: Tratamento global de erros HTTP
A API SHALL tratar exceções não capturadas globalmente via um `@RestControllerAdvice` construído sobre `ProblemDetail`, convertendo-as em respostas RFC 7807 padronizadas, sem expor stack trace ao cliente.

#### Scenario: Requisição malformada retorna 400
- **WHEN** o cliente envia uma requisição com corpo malformado ou sintaxe inválida
- **THEN** a API responde HTTP 400 com um corpo `Problem` (`type`, `title`, `status=400`, `detail`)

#### Scenario: Recurso não encontrado retorna 404
- **WHEN** um handler lança a exceção compartilhada de "recurso não encontrado"
- **THEN** a API responde HTTP 404 com um corpo `Problem` indicando o recurso ausente

#### Scenario: Conflito de estado retorna 409
- **WHEN** um handler lança a exceção compartilhada de "conflito" (ex.: violação de estado concorrente ou duplicidade)
- **THEN** a API responde HTTP 409 com um corpo `Problem` descrevendo o conflito

#### Scenario: Erro de validação retorna 422 com erros por campo
- **WHEN** uma requisição falha na validação de bean validation (`@Valid`)
- **THEN** a API responde HTTP 422 com um corpo `Problem` contendo uma lista de erros por campo (nome do campo e mensagem) para cada violação

#### Scenario: Erro inesperado retorna 500 sem stack trace
- **WHEN** uma exceção não mapeada é lançada
- **THEN** a API responde HTTP 500 com um corpo `Problem` genérico, sem stack trace ou detalhes internos da exceção

### Requirement: Health check exposto
A API SHALL expor um endpoint de health check em `/actuator/health`, sem autenticação, reportando o status da aplicação para uso por orquestração e monitoramento.

#### Scenario: Health check bem-sucedido
- **WHEN** uma requisição GET é enviada a `/actuator/health` com todas as dependências configuradas (ex.: banco) disponíveis
- **THEN** a API responde HTTP 200 com `status: UP`

#### Scenario: Health check reporta indisponibilidade
- **WHEN** uma dependência verificada pelo health check (ex.: banco de dados) está indisponível
- **THEN** `/actuator/health` responde com status HTTP refletindo indisponibilidade (ex.: 503) e `status: DOWN`

### Requirement: Banco de dados local via Docker Compose
O projeto SHALL fornecer um `docker-compose.yml` na raiz do monorepo executando `postgres:16`, configurado via variáveis de ambiente (usuário, senha e banco, com valores padrão para uso local), persistindo dados em volume nomeado, e aplicando automaticamente os scripts de `database/migrations` na primeira inicialização via `/docker-entrypoint-initdb.d`.

#### Scenario: Banco sobe com migrations aplicadas
- **WHEN** `docker compose up -d` é executado em um ambiente limpo (sem volume existente)
- **THEN** o container Postgres inicializa e os scripts de `database/migrations` são aplicados automaticamente, deixando as extensions `pg_trgm` e `unaccent` habilitadas

#### Scenario: Falha ao aplicar migration com erro de sintaxe
- **WHEN** um script em `database/migrations` contém um erro de SQL
- **THEN** a inicialização do Postgres falha ou os logs do container reportam o erro, e o schema não é considerado aplicado com sucesso

### Requirement: Convenção de scripts de migration numerados
Os scripts de migration em `database/migrations` SHALL seguir o padrão de nome `V###__descricao.sql` com prefixo numérico sequencial de 3 dígitos, e NÃO SHALL ser editados após o merge — qualquer correção de schema exige um novo script com o próximo número.

#### Scenario: Primeira migration segue a convenção
- **WHEN** o diretório `database/migrations` é inspecionado
- **THEN** existe o arquivo `V001__enable_extensions.sql`, seguindo o padrão de 3 dígitos e descrição em snake_case

#### Scenario: Script fora da convenção é rejeitado
- **WHEN** um novo script de migration é adicionado sem o prefixo numérico de 3 dígitos (ex.: `enable_extensions.sql` ou `V1__x.sql`)
- **THEN** uma verificação automatizada do projeto identifica o arquivo como fora da convenção e falha

### Requirement: Configuração da aplicação via variáveis de ambiente
`application.yml` SHALL configurar o datasource via variáveis de ambiente, definir `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.open-in-view=false` e a propriedade `app.default-city=sp/sao-paulo` (formato `uf/cidade`), documentada como valor padrão apenas — nenhuma regra do sistema pode presumir uma única cidade.

#### Scenario: Contexto sobe com variáveis de ambiente configuradas
- **WHEN** as variáveis de ambiente do datasource (host, porta, usuário, senha, banco) são fornecidas
- **THEN** a aplicação Spring Boot inicializa com sucesso e conecta ao Postgres configurado

#### Scenario: Contexto falha ao iniciar com schema divergente
- **WHEN** o schema do banco (criado pelas migrations) está desatualizado ou divergente das entidades JPA mapeadas
- **THEN** a aplicação falha ao iniciar devido à validação `ddl-auto=validate`, reportando a inconsistência

### Requirement: Organização de pacotes por capability e regra arquitetural
O código do backend SHALL ser organizado por pacote de capability (`com.culturalevents.<capability>.{api,application,domain,infrastructure}`), mais um pacote `com.culturalevents.shared` para erros e paginação, e SHALL ser verificado por um teste ArchUnit automatizado garantindo, para qualquer capability presente ou futura que siga esse padrão de pacote, a regra de dependência `api -> application -> domain <- infrastructure` (domain não depende de api, application ou infrastructure).

#### Scenario: Convenção de pacotes verificável sem depender de uma capability concreta
- **WHEN** nenhum pacote de capability de negócio existe ainda no backend
- **THEN** o teste ArchUnit que verifica a regra de dependência continua passando (a regra não exige que nenhuma classe exista no padrão de pacote para ser válida)

#### Scenario: Teste ArchUnit falha ao detectar violação de dependência
- **WHEN** uma classe do pacote `domain` depende de uma classe dos pacotes `infrastructure`, `application` ou `api`
- **THEN** o teste ArchUnit falha, impedindo que o build seja considerado válido

### Requirement: Teste de integração base com Testcontainers
O projeto SHALL ter um teste de integração base que inicia um container Postgres via Testcontainers, aplica os scripts de `database/migrations` e sobe o contexto Spring com `ddl-auto=validate`, comprovando que o schema do banco e as entidades mapeadas são compatíveis.

#### Scenario: Teste de integração passa com schema e contexto compatíveis
- **WHEN** a suíte de testes de integração é executada
- **THEN** um container Postgres via Testcontainers é iniciado, as migrations de `database/migrations` são aplicadas, e o contexto Spring sobe com sucesso usando `ddl-auto=validate`

#### Scenario: Teste de integração falha com schema divergente
- **WHEN** existe uma entidade JPA mapeada sem a tabela ou coluna correspondente aplicada pelas migrations
- **THEN** o teste de integração falha ao subir o contexto, reportando a inconsistência de schema detectada por `ddl-auto=validate`

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
