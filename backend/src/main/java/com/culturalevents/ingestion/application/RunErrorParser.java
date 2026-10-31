package com.culturalevents.ingestion.application;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Interpreta a coluna {@code errors} (lista de {@code {"external_id", "message"}}). A mensagem de um item que falha
 * tem a forma {@code CODIGO: detalhe}; erros globais usam {@code external_id = "-"} e mensagem sem código. Registros
 * malformados nunca derrubam a leitura: o que faltar vira {@code "-"}.
 */
final class RunErrorParser {

    private static final Pattern CODED_MESSAGE = Pattern.compile("^([A-Z][A-Z0-9_]+): (.*)$", Pattern.DOTALL);
    private static final String UNKNOWN = "-";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private RunErrorParser() {
    }

    static List<RunError> parse(String json) {
        JsonNode root;
        try {
            root = json == null ? null : MAPPER.readTree(json);
        } catch (RuntimeException ex) {
            return List.of();
        }
        if (root == null || !root.isArray()) {
            return List.of();
        }
        List<RunError> errors = new ArrayList<>();
        for (JsonNode item : root) {
            errors.add(parseItem(item));
        }
        return errors;
    }

    private static RunError parseItem(JsonNode item) {
        String externalId = text(item, "external_id");
        String message = text(item, "message");
        if (message == null) {
            return new RunError(null, externalId == null ? UNKNOWN : externalId, UNKNOWN);
        }
        Matcher matcher = CODED_MESSAGE.matcher(message);
        String externalIdOrDash = externalId == null ? UNKNOWN : externalId;
        if (matcher.matches()) {
            return new RunError(matcher.group(1), externalIdOrDash, matcher.group(2));
        }
        return new RunError(null, externalIdOrDash, message);
    }

    private static String text(JsonNode item, String field) {
        if (item == null || !item.isObject()) {
            return null;
        }
        JsonNode value = item.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }
}
