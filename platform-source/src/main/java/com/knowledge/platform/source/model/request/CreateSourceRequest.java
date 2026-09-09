package com.knowledge.platform.source.model.request;

import com.knowledge.platform.source.model.entity.SourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Studio payload for connecting a repository.
 *
 * <p>The token and webhook secret are write-only: they are accepted here and never returned by any
 * endpoint. {@link com.knowledge.platform.source.model.response.SourceResponse} reports only whether
 * each is set.
 */
public record CreateSourceRequest(
        @NotNull SourceType sourceType,
        @NotBlank @Size(max = 200) String displayName,
        @NotBlank @Size(max = 200) String owner,
        @NotBlank @Size(max = 200) String repository,
        @Size(max = 200) String project,
        @Size(max = 200) String defaultBranch,
        @Size(max = 500) String contentPath,
        @Size(max = 500) String apiBaseUrl,
        @Size(max = 500) String accessToken,
        @Size(max = 500) String webhookSecret,

        /**
         * The author this repository belongs to.
         *
         * <p>For the one-repository-per-author topology this is the point: the repository declares
         * its owner once, and articles inside it need no {@code author:} frontmatter at all. The
         * identity is created on first connection if it does not exist.
         *
         * <p>Frontmatter still wins where a file supplies it, so a guest post inside someone's
         * repository is still attributed correctly. Leave this blank and attribution falls back to
         * the connection's display name, which is implicit and changes if the connection is renamed.
         */
        @Size(max = 200) String ownerAuthorName,
        @Size(max = 320) String ownerAuthorEmail) {
}
