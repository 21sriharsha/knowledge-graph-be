package com.knowledge.platform.source.delegate;

import com.knowledge.platform.source.model.dto.SourceSyncRequestedEvent;
import com.knowledge.platform.source.model.request.CreateSourceRequest;
import com.knowledge.platform.source.model.request.UpdateSourceRequest;
import com.knowledge.platform.source.model.response.SourceResponse;
import com.knowledge.platform.source.service.SourceService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Translates between the studio HTTP contract and the source domain. */
@Component
public class SourceDelegate {

    private final SourceService sourceService;

    public SourceDelegate(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    public List<SourceResponse> list() {
        return sourceService.findAll().stream().map(SourceResponse::from).toList();
    }

    public SourceResponse get(UUID id) {
        return SourceResponse.from(sourceService.requireById(id));
    }

    public SourceResponse connect(CreateSourceRequest request) {
        return SourceResponse.from(sourceService.connect(
                request.sourceType(),
                request.displayName(),
                request.owner(),
                request.repository(),
                request.project(),
                request.defaultBranch(),
                request.contentPath(),
                request.apiBaseUrl(),
                request.accessToken(),
                request.webhookSecret(),
                request.ownerAuthorName(),
                request.ownerAuthorEmail()));
    }

    public SourceResponse update(UUID id, UpdateSourceRequest request) {
        return SourceResponse.from(sourceService.update(
                id,
                request.displayName(),
                request.defaultBranch(),
                request.contentPath(),
                request.apiBaseUrl(),
                request.ownerAuthorId(),
                request.accessToken(),
                request.webhookSecret()));
    }

    public SourceResponse setActive(UUID id, boolean active) {
        return SourceResponse.from(sourceService.setActive(id, active));
    }

    public void requestSync(UUID id) {
        sourceService.requestSync(id, SourceSyncRequestedEvent.Trigger.MANUAL);
    }
}
