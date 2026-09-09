package com.knowledge.platform.ingestion.service;

import com.knowledge.platform.ingestion.model.entity.IngestionEvent;
import com.knowledge.platform.ingestion.model.entity.IngestionRun;
import com.knowledge.platform.ingestion.model.entity.IngestionSeverity;
import com.knowledge.platform.ingestion.pipeline.IngestionContext;
import com.knowledge.platform.source.model.dto.SourceSyncRequestedEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists ingestion runs and their diagnostics.
 *
 * <p>A separate collaborator rather than private methods on {@link IngestionService}, because Spring's
 * {@code @Transactional} works through a proxy: a self-invoked method never passes through it, so the
 * annotation would be silently inert. Splitting the responsibility out makes the transactions real,
 * and it is a cleaner separation anyway -- one class drives the pipeline, one records what happened.
 *
 * <p>{@code REQUIRES_NEW} on the diagnostic writes is deliberate. Diagnostics must survive even when
 * the surrounding work fails; recording them in the same transaction as a failing document would roll
 * back the very explanation of why it failed.
 */
public interface IngestionRunRecorder {

    IngestionRun begin(UUID repositoryId, SourceSyncRequestedEvent event);

    IngestionRun save(IngestionRun run);

    void recordDiagnostics(IngestionContext context);

    void recordEvent(IngestionRun run, String sourcePath, IngestionSeverity severity,
            String code, String message);

    IngestionRun requireRun(UUID id);

    List<IngestionEvent> diagnosticsFor(UUID runId);

    List<IngestionRun> recentRuns(int limit);

    List<IngestionRun> runsForRepository(UUID repositoryId, int limit);
}
