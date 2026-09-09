package com.knowledge.platform.search.service;

import com.knowledge.platform.content.model.entity.Article;
import java.util.UUID;

/**
 * Maintains the derived search representations.
 *
 * <p>The two halves are treated very differently on purpose:
 *
 * <ul>
 *   <li><b>The lexical document is written inline.</b> It is a single upsert against PostgreSQL, and
 *       an article that is published but not findable by its own title is a visible defect.
 *   <li><b>The embedding is deferred to a job.</b> It requires an inference call that can take
 *       seconds and can fail. The reliability rule is explicit that an article must stay publishable
 *       when embedding generation fails, so it happens after the fact, with retries, and its absence
 *       degrades search rather than blocking publication.
 * </ul>
 */
public interface SearchIndexService {

    /**
     * Writes the lexical search document for an article.
     *
     * @param authorName flattened into the indexed metadata so that "kubernetes harsh" matches
     *     lexically even when the query never becomes an author filter
     */
    void index(Article article, String plainText, String authorName);

    /**
     * Schedules embedding generation, unless the current content is already embedded.
     *
     * <p>The hash check is what makes re-ingestion of unchanged content free rather than merely
     * harmless: an inference call is the most expensive thing in the pipeline, and skipping it is the
     * difference between a no-op sync being instant and being slow.
     */
    void scheduleEmbedding(UUID articleId, String plainText, String contentHash);

    /** Generates and stores an embedding. Called by the job, and directly by tests. */
    boolean generateEmbedding(UUID articleId, String plainText, String contentHash);

    void remove(UUID articleId);

    boolean isIndexedAt(UUID articleId, String contentHash);
}
