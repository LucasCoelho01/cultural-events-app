package com.culturalevents.ingestion.domain;

import java.time.Duration;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Execução do agente de ingestão. Somente leitura: quem grava em {@code ingestion_run} é o agente.
 * {@code errors} é o JSON bruto da coluna, interpretado pela camada de aplicação.
 */
@Entity
@Immutable
@Table(name = "ingestion_run")
public class IngestionRun {

    @Id
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private IngestionRunStatus status;

    @Column(name = "created", nullable = false)
    private int created;

    @Column(name = "updated", nullable = false)
    private int updated;

    @Column(name = "unchanged", nullable = false)
    private int unchanged;

    @Column(name = "failed", nullable = false)
    private int failed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "errors", nullable = false)
    private String errors;

    protected IngestionRun() {
    }

    public Long getId() {
        return id;
    }

    public Source getSource() {
        return source;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }

    public IngestionRunStatus getStatus() {
        return status;
    }

    public int getCreated() {
        return created;
    }

    public int getUpdated() {
        return updated;
    }

    public int getUnchanged() {
        return unchanged;
    }

    public int getFailed() {
        return failed;
    }

    /** JSON bruto da coluna {@code errors}. */
    public String getErrors() {
        return errors;
    }

    /** Segundos entre início e fim; {@code null} enquanto a execução não terminou. */
    public Long getDurationSeconds() {
        return finishedAt == null ? null : Duration.between(startedAt, finishedAt).toSeconds();
    }
}
