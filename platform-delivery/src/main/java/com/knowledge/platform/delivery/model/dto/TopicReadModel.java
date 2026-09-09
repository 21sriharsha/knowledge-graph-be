package com.knowledge.platform.delivery.model.dto;

import java.util.List;

/** Everything the public topic landing page needs. */
public record TopicReadModel(
        String slug,
        String name,
        String description,
        List<ArticleReference> articles,
        long articleCount) {

    public TopicReadModel {
        articles = articles == null ? List.of() : List.copyOf(articles);
    }
}
