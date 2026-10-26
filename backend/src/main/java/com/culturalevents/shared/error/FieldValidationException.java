package com.culturalevents.shared.error;

import java.util.List;

/** Uma ou mais violações de regra ligadas a campos da requisição; vira HTTP 422 com {@code errors[]}. */
public class FieldValidationException extends RuntimeException {

    private final List<FieldViolation> violations;

    public FieldValidationException(String field, String message) {
        this(List.of(new FieldViolation(field, message)));
    }

    public FieldValidationException(List<FieldViolation> violations) {
        super(violations.isEmpty() ? "validação falhou" : violations.getFirst().message());
        this.violations = List.copyOf(violations);
    }

    public List<FieldViolation> getViolations() {
        return violations;
    }
}
