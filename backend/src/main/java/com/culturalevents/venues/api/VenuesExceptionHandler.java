package com.culturalevents.venues.api;

import java.util.List;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.culturalevents.shared.api.model.FieldError;
import com.culturalevents.venues.application.VenueValidationException;

/** Erros específicos de locais; precede o handler global para devolver 422 com {@code errors[]}. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class VenuesExceptionHandler {

    @ExceptionHandler(VenueValidationException.class)
    ResponseEntity<Object> handleValidation(VenueValidationException ex) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, "A requisição contém campos inválidos.");
        body.setTitle("Unprocessable Entity");
        body.setProperty("errors", List.of(new FieldError(ex.getField(), ex.getMessage())));
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }
}
