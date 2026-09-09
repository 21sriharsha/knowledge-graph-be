package com.knowledge.platform.source.integration.adapter.azuredevops;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Azure DevOps REST API response shapes. Confined to the Azure DevOps adapter. */
final class AzureDevOpsApiModels {

    private AzureDevOpsApiModels() {
    }

    /** Azure DevOps wraps every collection response in {@code {count, value}}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Envelope<T>(int count, List<T> value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(String objectId, String gitObjectType, String path, Boolean isFolder, String content) {

        boolean isFile() {
            return "blob".equalsIgnoreCase(gitObjectType) && !Boolean.TRUE.equals(isFolder);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Commit(String commitId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PushPayload(String id, String eventType, PushResource resource) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PushResource(List<RefUpdate> refUpdates, List<Commit> commits) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RefUpdate(String name, String newObjectId, String oldObjectId) {
    }
}
