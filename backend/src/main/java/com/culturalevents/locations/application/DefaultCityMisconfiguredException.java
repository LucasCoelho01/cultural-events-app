package com.culturalevents.locations.application;

/** A propriedade app.default-city está malformada ou aponta para cidade inexistente/inativa. */
public class DefaultCityMisconfiguredException extends RuntimeException {

    public DefaultCityMisconfiguredException(String message) {
        super(message);
    }
}
