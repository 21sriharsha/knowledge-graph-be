package com.knowledge.platform.ingestion.delegate;

import com.knowledge.platform.author.model.dto.StudioPrincipal;
import com.knowledge.platform.author.service.StudioPrincipalResolver;
import com.knowledge.platform.common.exception.NotFoundException;
import com.knowledge.platform.ingestion.model.entity.IngestionRun;
import com.knowledge.platform.ingestion.model.response.IngestionRunResponse;
import com.knowledge.platform.ingestion.pipeline.IngestionChain;
import com.knowledge.platform.ingestion.service.IngestionRunRecorder;
import com.knowledge.platform.source.model.entity.SourceRepository;
import com.knowledge.platform.source.service.SourceService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Translates between the studio HTTP contract and the ingestion domain.
 *
 * <p>A run inherits the visibility of the repository it ran against. That is not a formality: run
 * diagnostics quote source paths and error messages, so an unscoped listing would describe the shape
 * of somebody else's repository to anyone with a studio login.
 */
@Component
public class IngestionDelegate {

    private final IngestionRunRecorder runRecorder;
    private final IngestionChain ingestionChain;
    private final SourceService sourceService;
    private final StudioPrincipalResolver principals;

    public IngestionDelegate(
            IngestionRunRecorder runRecorder,
            IngestionChain ingestionChain,
            SourceService sourceService,
            StudioPrincipalResolver principals) {
        this.runRecorder = runRecorder;
        this.ingestionChain = ingestionChain;
        this.sourceService = sourceService;
        this.principals = principals;
    }

    public IngestionRunResponse get(UUID id) {
        IngestionRun run = runRecorder.requireRun(id);
        if (!maySee(run, principals.require())) {
            // Reported as absent, like every other resource somebody else owns.
            throw NotFoundException.of("Ingestion run", id);
        }
        return IngestionRunResponse.from(run, runRecorder.diagnosticsFor(id));
    }

    /** Recent runs without their diagnostics; a listing does not need every document's detail. */
    public List<IngestionRunResponse> recent(int limit) {
        StudioPrincipal principal = principals.require();
        return runRecorder.recentRuns(limit).stream()
                .filter(run -> maySee(run, principal))
                .map(run -> IngestionRunResponse.from(run, List.of()))
                .toList();
    }

    public List<IngestionRunResponse> forRepository(UUID repositoryId, int limit) {
        // Ownership of the repository is the question; asking it first means an unowned id is
        // indistinguishable from one that does not exist.
        sourceService.requireOwned(repositoryId, principals.require());
        return runRecorder.runsForRepository(repositoryId, limit).stream()
                .map(run -> IngestionRunResponse.from(run, List.of()))
                .toList();
    }

    /**
     * A run is visible to whoever owns the repository it ran against.
     *
     * <p>A run whose repository has since been deleted is visible only to an administrator: there is
     * no owner left to inherit from, and unowned means nobody's rather than everybody's.
     */
    private boolean maySee(IngestionRun run, StudioPrincipal principal) {
        if (principal.isAdmin()) {
            return true;
        }
        if (run.getRepositoryId() == null) {
            return false;
        }
        return sourceService.findById(run.getRepositoryId())
                .map(SourceRepository::getOwnerAuthorId)
                .filter(principal::canActOnBehalfOf)
                .isPresent();
    }

    /** The configured pipeline, in execution order. */
    public List<String> pipeline() {
        return ingestionChain.handlerNames();
    }
}
