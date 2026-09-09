package com.knowledge.platform.ingestion.model.response;

import com.knowledge.platform.ingestion.model.entity.IngestionEvent;
import com.knowledge.platform.ingestion.model.entity.IngestionRun;
import com.knowledge.platform.ingestion.model.entity.IngestionSeverity;
import com.knowledge.platform.ingestion.model.entity.IngestionState;
import com.knowledge.platform.source.model.dto.SourceSyncRequestedEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An ingestion run and its diagnostics.
 *
 * <p>The diagnostics are the point of this endpoint. An author whose article did not appear, or
 * appeared with a broken link, gets a specific answer here rather than being told the sync
 * "succeeded" with no further detail.
 */
public record IngestionRunResponse(
        UUID id,
        String correlationId,
        UUID repositoryId,
        SourceSyncRequestedEvent.Trigger trigger,
        IngestionState state,
        String requestedRevision,
        int documentsTotal,
        int documentsSucceeded,
        int documentsSkipped,
        int documentsFailed,
        Instant startedAt,
        Instant finishedAt,
        List<Diagnostic> diagnostics) {

    public static IngestionRunResponse from(IngestionRun run, List<IngestionEvent> events) {
        return new IngestionRunResponse(
                run.getId(),
                run.getCorrelationId(),
                run.getRepositoryId(),
                run.getTriggerType(),
                run.getState(),
                run.getRequestedRevision(),
                run.getDocumentsTotal(),
                run.getDocumentsSucceeded(),
                run.getDocumentsSkipped(),
                run.getDocumentsFailed(),
                run.getStartedAt(),
                run.getFinishedAt(),
                events.stream().map(Diagnostic::from).toList());
    }

    /** One diagnostic. {@code code} is stable for a UI to branch on; {@code message} is for humans. */
    public record Diagnostic(
            String sourcePath,
            String articleSlug,
            IngestionSeverity severity,
            String code,
            String message,
            Instant occurredAt) {

        static Diagnostic from(IngestionEvent event) {
            return new Diagnostic(
                    event.getSourcePath(), event.getArticleSlug(), event.getSeverity(),
                    event.getCode(), event.getMessage(), event.getOccurredAt());
        }
    }
}
