package com.culturalevents.locations.api;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.culturalevents.locations.application.DefaultCityMisconfiguredException;

/** Erros específicos de localidades; precede o handler global para manter o detalhe da configuração. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class LocationsExceptionHandler {

    @ExceptionHandler(DefaultCityMisconfiguredException.class)
    ResponseEntity<Object> handleDefaultCityMisconfigured(DefaultCityMisconfiguredException ex) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
        body.setTitle("Internal Server Error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
