package com.knowledge.platform.source.model.dto;

import com.knowledge.platform.source.model.entity.SourceType;

/**
 * Everything an adapter needs to talk to one repository, with the credential already decrypted.
 *
 * <p>Passed transiently and never persisted or logged. It exists so adapters never receive a
 * {@code SourceRepository} entity: an adapter that could see the entity could also mutate it, and
 * would end up holding a JPA object across an HTTP call.
 */
public record RepositoryDescriptor(
        SourceType sourceType,
        String owner,
        String repository,
        String project,
        String defaultBranch,
        String contentPath,
        String apiBaseUrl,
        String accessToken) {

    /** Keeps the token out of logs, stack traces and actuator dumps. */
    @Override
    public String toString() {
        return "RepositoryDescriptor[" + sourceType + " " + owner + "/" + repository
                + (project == null ? "" : " project=" + project) + "]";
    }
}
