package com.knowledge.platform.delivery.model.dto;

import java.time.Instant;
import java.util.List;

/** Everything the public author page needs. */
public record AuthorReadModel(
        String slug,
        String displayName,
        String biography,
        String avatarUrl,
        List<ArticleReference> articles,
        List<ArticleReadModel.TaxonomyReference> topics,
        long articleCount,
        Instant joinedAt) {

    public AuthorReadModel {
        articles = articles == null ? List.of() : List.copyOf(articles);
        topics = topics == null ? List.of() : List.copyOf(topics);
    }
}
