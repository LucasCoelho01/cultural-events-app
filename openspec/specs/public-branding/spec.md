# public-branding Specification

## Purpose
Define a identidade visual do front-end público: logo, favicon, paleta, tipografia, placeholder de imagem e requisitos de acessibilidade visual, herdados também pelo cabeçalho do admin.

## Requirements

### Requirement: Logo no cabeçalho como link para a página inicial
O cabeçalho SHALL exibir o logo (ícone e nome) como link com nome acessível "Cultural Events". Em rota de cidade `/:uf/:cidade...` o link aponta para `/:uf/:cidade`; fora dela (ex.: `/admin/...`, `/`) aponta para `/`.

#### Scenario: Logo em rota de cidade
- **WHEN** o usuário está em `/zz/cidade-teste/eventos/algum-evento`
- **THEN** o link do logo aponta para `/zz/cidade-teste`

#### Scenario: Logo fora de rota de cidade
- **WHEN** o usuário está em `/admin/eventos`
- **THEN** o link do logo aponta para `/`

#### Scenario: Cidade inexistente
- **WHEN** a API responde HTTP 404 com `Problem` para a cidade da rota
- **THEN** o logo continua visível no cabeçalho, sem quebrá-lo, e a página exibe `ErrorMessage`

### Requirement: Favicon da marca
A aplicação SHALL declarar o ícone do logo (`logo-mark.svg`) como favicon do documento.

#### Scenario: Favicon declarado
- **WHEN** o documento HTML é carregado
- **THEN** há um `link rel="icon"` apontando para `/logo-mark.svg`

#### Scenario: Arquivo do favicon ausente
- **WHEN** `/logo-mark.svg` não pode ser carregado
- **THEN** a página funciona normalmente, sem erro visível ao usuário

### Requirement: Tema com a paleta e a fonte da marca
O tema SHALL expor como tokens as cores roxo `#7C3AED`, rosa `#EC4899` e laranja `#F59E0B` e SHALL usar a fonte Poppins com fallback para fontes de sistema.

#### Scenario: Tokens disponíveis
- **WHEN** um componente usa as classes de cor da marca
- **THEN** elas resolvem para as três cores da paleta

#### Scenario: Fonte indisponível
- **WHEN** o Google Fonts não carrega (offline ou bloqueado)
- **THEN** o texto é exibido na fonte de sistema, sem perda de conteúdo

### Requirement: Placeholder de imagem
Toda imagem de evento SHALL ser substituída por um placeholder em degradê com o ícone do logo quando `imageUrl` for nulo, vazio ou falhar ao carregar, mantendo o espaço reservado. Imagens SHALL ter `loading="lazy"` e `alt` igual ao título do evento.

#### Scenario: Evento sem imagem
- **WHEN** `imageUrl` é nulo
- **THEN** o placeholder é exibido no lugar da imagem, sem elemento `img` quebrado

#### Scenario: Imagem falha ao carregar
- **WHEN** o navegador dispara erro de carregamento da `imageUrl`
- **THEN** o placeholder substitui a imagem e o layout não muda de tamanho

#### Scenario: Imagem válida
- **WHEN** `imageUrl` carrega
- **THEN** o `img` tem `loading="lazy"` e `alt` igual ao título do evento

### Requirement: Acessibilidade visual
A interface pública SHALL manter contraste mínimo AA (4.5:1 para texto normal), foco de teclado visível em links, botões e campos, e SHALL transmitir categoria, status de sessão e erros também por texto, não só por cor.

#### Scenario: Foco por teclado
- **WHEN** o usuário navega com Tab até um link ou botão
- **THEN** há um indicador de foco visível

#### Scenario: Chip de categoria
- **WHEN** o card mostra a categoria com cor própria
- **THEN** o nome da categoria aparece como texto no chip

#### Scenario: Texto sobre degradê sem contraste
- **WHEN** um texto seria exibido sobre o degradê da marca sem atingir contraste AA
- **THEN** o texto fica sobre fundo sólido que atinge AA
