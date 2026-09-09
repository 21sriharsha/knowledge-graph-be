package com.knowledge.platform.ingestion.delegate;

import com.knowledge.platform.ingestion.model.entity.IngestionRun;
import com.knowledge.platform.ingestion.model.response.IngestionRunResponse;
import com.knowledge.platform.ingestion.pipeline.IngestionChain;
import com.knowledge.platform.ingestion.service.IngestionRunRecorder;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Translates between the studio HTTP contract and the ingestion domain. */
@Component
public class IngestionDelegate {

    private final IngestionRunRecorder runRecorder;
    private final IngestionChain ingestionChain;

    public IngestionDelegate(IngestionRunRecorder runRecorder, IngestionChain ingestionChain) {
        this.runRecorder = runRecorder;
        this.ingestionChain = ingestionChain;
    }

    public IngestionRunResponse get(UUID id) {
        IngestionRun run = runRecorder.requireRun(id);
        return IngestionRunResponse.from(run, runRecorder.diagnosticsFor(id));
    }

    /** Recent runs without their diagnostics; a listing does not need every document's detail. */
    public List<IngestionRunResponse> recent(int limit) {
        return runRecorder.recentRuns(limit).stream()
                .map(run -> IngestionRunResponse.from(run, List.of()))
                .toList();
    }

    public List<IngestionRunResponse> forRepository(UUID repositoryId, int limit) {
        return runRecorder.runsForRepository(repositoryId, limit).stream()
                .map(run -> IngestionRunResponse.from(run, List.of()))
                .toList();
    }

    /** The configured pipeline, in execution order. */
    public List<String> pipeline() {
        return ingestionChain.handlerNames();
    }
}
