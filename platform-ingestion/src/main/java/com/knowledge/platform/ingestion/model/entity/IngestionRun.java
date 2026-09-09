package com.knowledge.platform.ingestion.model.entity;

import com.knowledge.platform.source.model.dto.SourceSyncRequestedEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One materialization attempt.
 *
 * <p>The run is created before any work starts and closed whatever happens, so an operator can always
 * answer "did the sync run, and what did it do" -- including for a run that crashed, which shows as
 * RUNNING with no finish time rather than leaving no trace at all.
 */
@Entity
@Table(name = "runs", schema = "ingestion")
public class IngestionRun {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "correlation_id", nullable = false, unique = true, updatable = false, length = 64)
    private String correlationId;

    @Column(name = "repository_id")
    private UUID repositoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 32)
    private SourceSyncRequestedEvent.Trigger triggerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 32)
    private IngestionState state;

    @Column(name = "requested_revision", length = 200)
    private String requestedRevision;

    @Column(name = "documents_total", nullable = false)
    private int documentsTotal;

    @Column(name = "documents_succeeded", nullable = false)
    private int documentsSucceeded;

    @Column(name = "documents_failed", nullable = false)
    private int documentsFailed;

    @Column(name = "documents_skipped", nullable = false)
    private int documentsSkipped;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected IngestionRun() {
    }

    private IngestionRun(UUID repositoryId, SourceSyncRequestedEvent.Trigger trigger, String revision) {
        this.id = UUID.randomUUID();
        // Derived from the run id so that the log line written before the insert commits already
        // carries the identifier the row will have.
        this.correlationId = this.id.toString().replace("-", "").substring(0, 32);
        this.repositoryId = repositoryId;
        this.triggerType = trigger;
        this.requestedRevision = revision;
        this.state = IngestionState.RUNNING;
        this.startedAt = Instant.now();
    }

    public static IngestionRun start(
            UUID repositoryId, SourceSyncRequestedEvent.Trigger trigger, String revision) {
        return new IngestionRun(repositoryId, trigger, revision);
    }

    public void recordDocument(DocumentOutcome outcome) {
        documentsTotal++;
        switch (outcome) {
            case SUCCEEDED -> documentsSucceeded++;
            case FAILED -> documentsFailed++;
            case SKIPPED -> documentsSkipped++;
        }
    }

    /**
     * Closes the run, deriving the state from what actually happened.
     *
     * <p>Two cases are easy to get wrong, and both are common in steady state:
     *
     * <ul>
     *   <li><b>A run that processed nothing is a success.</b> A webhook for a commit touching no
     *       Markdown is a legitimate no-op.
     *   <li><b>A skipped document is a good outcome, not a missing one.</b> It means the content is
     *       already correctly materialized. Counting only {@code documentsSucceeded} as healthy would
     *       report a repeated sync of an unchanged repository -- the normal case -- as FAILED, which
     *       is both wrong and the kind of thing that trains operators to ignore the status.
     * </ul>
     */
    public void finish() {
        this.finishedAt = Instant.now();
        if (documentsFailed == 0) {
            this.state = IngestionState.SUCCEEDED;
            return;
        }
        int healthy = documentsSucceeded + documentsSkipped;
        this.state = healthy > 0 ? IngestionState.PARTIALLY_SUCCEEDED : IngestionState.FAILED;
    }

    /** Closes a run that could not proceed at all -- an unreachable provider, say. */
    public void fail() {
        this.finishedAt = Instant.now();
        this.state = IngestionState.FAILED;
    }

    /** What happened to one document within a run. */
    public enum DocumentOutcome {
        SUCCEEDED,
        FAILED,
        /** Unchanged since the last run, so no derived state needed rewriting. */
        SKIPPED
    }

    public UUID getId() {
        return id;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public UUID getRepositoryId() {
        return repositoryId;
    }

    public SourceSyncRequestedEvent.Trigger getTriggerType() {
        return triggerType;
    }

    public IngestionState getState() {
        return state;
    }

    public String getRequestedRevision() {
        return requestedRevision;
    }

    public int getDocumentsTotal() {
        return documentsTotal;
    }

    public int getDocumentsSucceeded() {
        return documentsSucceeded;
    }

    public int getDocumentsFailed() {
        return documentsFailed;
    }

    public int getDocumentsSkipped() {
        return documentsSkipped;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
