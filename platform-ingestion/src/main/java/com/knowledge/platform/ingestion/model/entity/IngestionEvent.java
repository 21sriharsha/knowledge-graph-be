package com.knowledge.platform.ingestion.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A structured ingestion diagnostic.
 *
 * <p>These are the answer to "why does my article not look how I expect". An unresolved
 * {@code [[link]]}, a missing title, dropped raw HTML: all recorded here against the run and the
 * source path, rather than logged and forgotten.
 */
@Entity
@Table(name = "events", schema = "ingestion")
public class IngestionEvent {

    /** Codes are stable identifiers a UI can branch on; the message is for humans. */
    public static final String CODE_UNRESOLVED_LINK = "UNRESOLVED_LINK";
    public static final String CODE_MISSING_TITLE = "MISSING_TITLE";
    public static final String CODE_RAW_HTML_DROPPED = "RAW_HTML_DROPPED";
    public static final String CODE_PARSE_FAILED = "PARSE_FAILED";
    public static final String CODE_PERSIST_FAILED = "PERSIST_FAILED";
    public static final String CODE_UNCHANGED = "UNCHANGED";
    public static final String CODE_PROVIDER_ERROR = "PROVIDER_ERROR";
    public static final String CODE_HANDLER_FAILED = "HANDLER_FAILED";
    public static final String CODE_ARTICLE_REMOVED = "ARTICLE_REMOVED";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "run_id", nullable = false, updatable = false)
    private UUID runId;

    @Column(name = "source_path", length = 1000)
    private String sourcePath;

    @Column(name = "article_slug", length = 160)
    private String articleSlug;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private IngestionSeverity severity;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected IngestionEvent() {
    }

    public IngestionEvent(UUID runId, String sourcePath, String articleSlug,
            IngestionSeverity severity, String code, String message) {
        this.id = UUID.randomUUID();
        this.runId = runId;
        this.sourcePath = sourcePath;
        this.articleSlug = articleSlug;
        this.severity = severity;
        this.code = code;
        this.message = message;
        this.occurredAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getRunId() {
        return runId;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public String getArticleSlug() {
        return articleSlug;
    }

    public IngestionSeverity getSeverity() {
        return severity;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
