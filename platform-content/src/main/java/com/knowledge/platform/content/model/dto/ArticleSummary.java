package com.knowledge.platform.content.model.dto;

import com.knowledge.platform.content.model.entity.Article;
import java.time.Instant;
import java.util.UUID;

/**
 * The article fields other modules embed in listings, search results and related-content sections.
 *
 * <p>Never carries the body. A related-articles list of six would otherwise pull six full Markdown
 * documents into memory to render six titles.
 */
public record ArticleSummary(
        UUID id,
        String slug,
        String title,
        String summary,
        UUID authorId,
        Instant publishedAt,
        int readingTimeMinutes) {

    public static ArticleSummary from(Article article) {
        return new ArticleSummary(
                article.getId(),
                article.getSlug(),
                article.getTitle(),
                article.getSummary(),
                article.getAuthorId(),
                article.getPublishedAt(),
                article.getReadingTimeMinutes());
    }
}
