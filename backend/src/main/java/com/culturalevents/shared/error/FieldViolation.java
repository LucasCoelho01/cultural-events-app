package com.culturalevents.shared.error;

/** Violação de regra associada a um campo da requisição (nome do campo e mensagem). */
public record FieldViolation(String field, String message) {
}
