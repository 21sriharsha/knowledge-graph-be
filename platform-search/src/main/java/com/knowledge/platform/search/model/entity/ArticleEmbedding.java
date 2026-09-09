package com.knowledge.platform.search.model.entity;

import java.util.UUID;

/**
 * The derived semantic representation of an article, as stored.
 *
 * @param model which embedding model produced the vector; a corpus embedded by two different models
 *     is not comparable, so this is recorded rather than assumed
 * @param contentHash what was embedded, making a stale vector detectable when an article is edited
 */
public record ArticleEmbedding(UUID articleId, String model, float[] embedding, String contentHash) {

    public int dimensions() {
        return embedding == null ? 0 : embedding.length;
    }
}
