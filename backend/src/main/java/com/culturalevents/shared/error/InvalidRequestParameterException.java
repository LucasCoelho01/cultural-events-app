package com.culturalevents.shared.error;

/** Parâmetro de query com valor inválido que só a regra de negócio detecta (ex.: depois do trim); vira HTTP 400. */
public class InvalidRequestParameterException extends RuntimeException {

    private final String parameter;

    public InvalidRequestParameterException(String parameter) {
        super("Parâmetro de requisição inválido: " + parameter);
        this.parameter = parameter;
    }

    public String getParameter() {
        return parameter;
    }
}
