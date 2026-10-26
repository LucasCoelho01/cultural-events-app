# Spec Delta

## ADDED Requirements

### Requirement: Cidade de local com eventos não pode mudar
O banco SHALL rejeitar a alteração de `city_id` de um `venue` referenciado por algum `event`, e a API SHALL responder HTTP 409 em `PUT /api/v1/venues/{id}` que tente trocar a cidade de um local com eventos, mantendo o local inalterado.

#### Scenario: Troca de cidade de local com eventos retorna 409
- **WHEN** o cliente envia `PUT /api/v1/venues/{id}` com `cityId` diferente do atual para um local que tem ao menos um evento
- **THEN** a API responde HTTP 409 com `Problem` (`status=409`) e o local permanece na cidade original

#### Scenario: Troca de cidade de local sem eventos é aceita
- **WHEN** o cliente envia `PUT /api/v1/venues/{id}` com `cityId` de outra cidade ativa para um local sem eventos
- **THEN** a API responde HTTP 200 com o novo `cityId`

#### Scenario: Banco rejeita a troca por UPDATE direto
- **WHEN** um script faz `UPDATE venue SET city_id = <outra cidade>` num local referenciado por um evento
- **THEN** o banco rejeita o UPDATE com violação de restrição (SQLSTATE `23001`) e o local permanece inalterado

## MODIFIED Requirements

### Requirement: Remoção de local
A API SHALL expor `DELETE /api/v1/venues/{id}` respondendo HTTP 204 sem corpo quando o local for removido, HTTP 404 quando não existir e HTTP 409 quando houver evento ligado ao local.

#### Scenario: Remoção bem-sucedida
- **WHEN** o cliente envia `DELETE /api/v1/venues/{id}` para um local existente sem eventos
- **THEN** a API responde HTTP 204 e um `GET` posterior do mesmo `id` responde HTTP 404

#### Scenario: Local inexistente retorna 404
- **WHEN** o cliente envia `DELETE /api/v1/venues/{id}` com UUID inexistente, ou repete um `DELETE` já concluído
- **THEN** a API responde HTTP 404 com `Problem` (`status=404`)

#### Scenario: Local com evento ligado retorna 409
- **WHEN** o cliente envia `DELETE /api/v1/venues/{id}` para um local referenciado por ao menos um evento, em qualquer `status`
- **THEN** a API responde HTTP 409 com `Problem` (`status=409`) e o local permanece cadastrado

#### Scenario: Remoção volta a ser possível sem eventos
- **WHEN** o último evento ligado ao local é removido e o cliente envia `DELETE /api/v1/venues/{id}`
- **THEN** a API responde HTTP 204
