package com.culturalevents.shared.error;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.culturalevents.shared.api.model.FieldError;

/**
 * Handler global de exceções: converte exceções não capturadas em respostas RFC 7807
 * ({@link ProblemDetail}), sem nunca expor stack trace ao cliente.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "O corpo da requisição é malformado ou ilegível.");
        body.setTitle("Bad Request");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new FieldError(
                        fieldError.getField(),
                        fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "valor inválido"))
                .collect(Collectors.toList());
        return unprocessableEntity(errors);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return badRequest("Parâmetro de requisição inválido.");
    }

    /**
     * Violação em parâmetro de query/path (validação de método) é erro de requisição (400);
     * violação em outros elementos, como bean validation de corpo, é 422 com erros por campo.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex) {
        boolean parameterViolation = ex.getConstraintViolations().stream().anyMatch(this::isParameterViolation);
        if (parameterViolation) {
            String names = ex.getConstraintViolations().stream()
                    .map(violation -> lastNodeName(violation.getPropertyPath()))
                    .distinct()
                    .collect(Collectors.joining(", "));
            return badRequest("Parâmetro de requisição inválido: " + names + ".");
        }
        List<FieldError> errors = ex.getConstraintViolations().stream()
                .map(violation -> new FieldError(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()))
                .collect(Collectors.toList());
        return unprocessableEntity(errors);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Object> handleNotFound(NotFoundException ex) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        body.setTitle("Not Found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Object> handleConflict(ConflictException ex) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        body.setTitle("Conflict");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex) {
        log.error("Erro inesperado não tratado", ex);
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Ocorreu um erro inesperado. Tente novamente mais tarde.");
        body.setTitle("Internal Server Error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private boolean isParameterViolation(ConstraintViolation<?> violation) {
        for (Path.Node node : violation.getPropertyPath()) {
            if (node.getKind() == ElementKind.PARAMETER) {
                return true;
            }
        }
        return false;
    }

    private String lastNodeName(Path path) {
        String name = "";
        for (Path.Node node : path) {
            name = node.getName();
        }
        return name;
    }

    private ResponseEntity<Object> badRequest(String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        body.setTitle("Bad Request");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    private ResponseEntity<Object> unprocessableEntity(List<FieldError> errors) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, "A requisição contém campos inválidos.");
        body.setTitle("Unprocessable Entity");
        body.setProperty("errors", errors);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }
}
