## MODIFIED Requirements

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
