package com.knowledge.platform.delivery.model.dto;

/**
 * A pointer to another article, carrying just enough to render a link.
 *
 * <p>Used for related content, backlinks and previous/next navigation. Deliberately not the full
 * article: a read model containing six neighbours' bodies would be an order of magnitude larger for
 * no benefit, and would need invalidating whenever any neighbour changed.
 */
public record ArticleReference(String slug, String title, String summary, String authorName) {
}
