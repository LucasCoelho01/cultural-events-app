package com.culturalevents.shared.persistence;

import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hibernate.exception.ConstraintViolationException;

/** Extrai SQLSTATE e nome da constraint de uma falha de gravação, percorrendo a cadeia de causas. */
public final class ConstraintViolations {

    private static final Pattern NAME_IN_MESSAGE = Pattern.compile("constraint \"([^\"]+)\"");

    private ConstraintViolations() {
    }

    /** SQLSTATE da primeira {@link SQLException} da cadeia, ou {@code null}. */
    public static String sqlState(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                return sql.getSQLState();
            }
        }
        return null;
    }

    /**
     * Nome da constraint violada, ou {@code null}. Vem do Hibernate quando ele o extrai da mensagem; senão, do texto
     * padrão {@code constraint "nome"} da mensagem do Postgres (que os {@code RAISE} dos triggers reproduzem).
     */
    public static String constraintName(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                return violation.getConstraintName();
            }
        }
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getMessage() != null) {
                Matcher matcher = NAME_IN_MESSAGE.matcher(sql.getMessage());
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        }
        return null;
    }
}
