package com.knowledge.platform.delivery.model.dto;

import java.util.List;

/**
 * Everything the public tag page needs.
 *
 * <p>Shaped exactly like {@link TopicReadModel} rather than reusing it. Tags and topics are different
 * concepts -- a topic is a curated subject area with a description, a tag is a label an author typed
 * -- and collapsing them into one type would make the page unable to say which it is showing.
 */
public record TagReadModel(String slug, String name, List<ArticleReference> articles, long articleCount) {

    public TagReadModel {
        articles = articles == null ? List.of() : List.copyOf(articles);
    }
}
