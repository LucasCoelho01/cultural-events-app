package com.culturalevents.ingestion.application;

/** Erro de um item de uma execução; {@code code} é nulo quando a mensagem do agente não traz código. */
public record RunError(String code, String externalId, String detail) {
}
