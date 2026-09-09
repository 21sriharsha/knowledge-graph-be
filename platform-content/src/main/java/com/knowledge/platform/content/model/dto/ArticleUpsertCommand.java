package com.knowledge.platform.content.model.dto;

import java.util.List;
import java.util.UUID;

/**
 * Everything needed to store one revision of an article.
 *
 * <p>A command object rather than a long parameter list, because ingestion assembles these fields
 * across several pipeline handlers before any of them is written. It also keeps the content module's
 * write API stable as the pipeline grows.
 *
 * @param sourceCoordinate where the document came from; null for content authored in the studio
 */
public record ArticleUpsertCommand(
        String requestedSlug,
        String title,
        String summary,
        String canonicalMarkdown,
        String contentHash,
        UUID authorId,
        List<String> tagNames,
        List<String> topicNames,
        int wordCount,
        int readingTimeMinutes,
        boolean publish,
        SourceCoordinate sourceCoordinate) {

    public ArticleUpsertCommand {
        tagNames = tagNames == null ? List.of() : List.copyOf(tagNames);
        topicNames = topicNames == null ? List.of() : List.copyOf(topicNames);
    }

    /** The repository and path a document came from: an article's stable identity across renames. */
    public record SourceCoordinate(UUID repositoryId, String sourcePath, String sourceRevision) {
    }
}
