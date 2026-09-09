package com.knowledge.platform.content.model.response;

import com.knowledge.platform.content.model.entity.Article;
import com.knowledge.platform.content.model.entity.PublicationState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The article representation returned by studio endpoints.
 *
 * <p>Distinct from the delivery module's read model: this is the editorial view, showing publication
 * state and source binding. The public read path returns
 * {@code com.knowledge.platform.delivery.model.dto.ArticleReadModel} instead, which carries the
 * rendered block tree and navigation but none of the editorial metadata.
 */
public record ArticleResponse(
        UUID id,
        String slug,
        String title,
        String summary,
        PublicationState publicationState,
        UUID authorId,
        List<String> tags,
        List<String> topics,
        int wordCount,
        int readingTimeMinutes,
        int revisionNumber,
        String sourcePath,
        String sourceRevision,
        Instant publishedAt,
        Instant updatedAt) {

    public static ArticleResponse from(Article article) {
        return new ArticleResponse(
                article.getId(),
                article.getSlug(),
                article.getTitle(),
                article.getSummary(),
                article.getPublicationState(),
                article.getAuthorId(),
                article.getTags().stream().map(tag -> tag.getSlug()).sorted().toList(),
                article.getTopics().stream().map(topic -> topic.getSlug()).sorted().toList(),
                article.getWordCount(),
                article.getReadingTimeMinutes(),
                article.getRevisionNumber(),
                article.getSourcePath(),
                article.getSourceRevision(),
                article.getPublishedAt(),
                article.getUpdatedAt());
    }
}
