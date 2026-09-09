package com.knowledge.platform.source.model.response;

import com.knowledge.platform.source.model.entity.SourceRepository;
import com.knowledge.platform.source.model.entity.SourceType;
import java.time.Instant;
import java.util.UUID;

/**
 * A connected repository as the studio sees it.
 *
 * <p>Credentials appear only as booleans. There is no code path that returns a stored token, not
 * even to the author who supplied it -- an API that can read a secret back is an API that can leak
 * one, and the operator can always set a new value.
 */
public record SourceResponse(
        UUID id,
        SourceType sourceType,
        String displayName,
        String owner,
        String repository,
        String project,
        String defaultBranch,
        String contentPath,
        String apiBaseUrl,
        UUID ownerAuthorId,
        boolean active,
        boolean accessTokenConfigured,
        boolean webhookSecretConfigured,
        String lastSyncedRevision,
        Instant lastSyncedAt,
        Instant createdAt) {

    public static SourceResponse from(SourceRepository repository) {
        return new SourceResponse(
                repository.getId(),
                repository.getSourceType(),
                repository.getDisplayName(),
                repository.getOwner(),
                repository.getRepository(),
                repository.getProject(),
                repository.getDefaultBranch(),
                repository.getContentPath(),
                repository.getApiBaseUrl(),
                repository.getOwnerAuthorId(),
                repository.isActive(),
                repository.getAccessTokenCiphertext() != null,
                repository.hasWebhookSecret(),
                repository.getLastSyncedRevision(),
                repository.getLastSyncedAt(),
                repository.getCreatedAt());
    }
}
