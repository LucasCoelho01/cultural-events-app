package com.culturalevents.venues.application;

/** Violação de regra de negócio ligada a um campo da requisição; vira HTTP 422 com {@code errors[]}. */
public class VenueValidationException extends RuntimeException {

    private final String field;

    public VenueValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
