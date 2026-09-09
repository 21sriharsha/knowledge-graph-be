package com.knowledge.platform.source.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Studio payload for editing a connected repository.
 *
 * <p>Credentials are optional: omitting them leaves the stored values untouched, so an operator can
 * change a content path without re-entering a token they may not have kept.
 */
public record UpdateSourceRequest(
        @NotBlank @Size(max = 200) String displayName,
        @Size(max = 200) String defaultBranch,
        @Size(max = 500) String contentPath,
        @Size(max = 500) String apiBaseUrl,
        UUID ownerAuthorId,
        @Size(max = 500) String accessToken,
        @Size(max = 500) String webhookSecret) {
}
