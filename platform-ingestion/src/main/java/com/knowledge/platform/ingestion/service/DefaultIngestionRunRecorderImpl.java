package com.knowledge.platform.ingestion.service;

import com.knowledge.platform.common.exception.NotFoundException;
import com.knowledge.platform.ingestion.model.entity.IngestionEvent;
import com.knowledge.platform.ingestion.model.entity.IngestionRun;
import com.knowledge.platform.ingestion.model.entity.IngestionSeverity;
import com.knowledge.platform.ingestion.pipeline.IngestionContext;
import com.knowledge.platform.ingestion.repository.IngestionEventRepository;
import com.knowledge.platform.ingestion.repository.IngestionRunRepository;
import com.knowledge.platform.source.model.dto.SourceSyncRequestedEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default IngestionRunRecorder.
 *
 * <p>See {@link IngestionRunRecorder} for what this provides and why it exists.
 */
@Service
public class DefaultIngestionRunRecorderImpl implements IngestionRunRecorder {

    private final IngestionRunRepository runs;
    private final IngestionEventRepository events;

    public DefaultIngestionRunRecorderImpl(IngestionRunRepository runs, IngestionEventRepository events) {
        this.runs = runs;
        this.events = events;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IngestionRun begin(UUID repositoryId, SourceSyncRequestedEvent event) {
        return runs.save(IngestionRun.start(repositoryId, event.trigger(), event.revision()));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IngestionRun save(IngestionRun run) {
        return runs.save(run);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDiagnostics(IngestionContext context) {
        if (!context.diagnostics().isEmpty()) {
            events.saveAll(context.diagnostics());
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordEvent(IngestionRun run, String sourcePath, IngestionSeverity severity,
            String code, String message) {
        events.save(new IngestionEvent(run.getId(), sourcePath, null, severity, code, message));
    }

    @Override
    @Transactional(readOnly = true)
    public IngestionRun requireRun(UUID id) {
        return runs.findById(id).orElseThrow(() -> NotFoundException.of("Ingestion run", id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<IngestionEvent> diagnosticsFor(UUID runId) {
        return events.findByRunIdOrderByOccurredAtAsc(runId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IngestionRun> recentRuns(int limit) {
        return runs.findAllByOrderByStartedAtDesc(PageRequest.of(0, limit));
    }

    @Override
    @Transactional(readOnly = true)
    public List<IngestionRun> runsForRepository(UUID repositoryId, int limit) {
        return runs.findByRepositoryIdOrderByStartedAtDesc(repositoryId, PageRequest.of(0, limit));
    }
}
