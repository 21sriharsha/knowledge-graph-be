package com.knowledge.platform.delivery.service;

import com.knowledge.platform.common.model.Slug;
import com.knowledge.platform.content.model.entity.Article;
import com.knowledge.platform.delivery.model.dto.ArticleReadModel;
import java.util.UUID;

/**
 * Serves and maintains article read models.
 *
 * <p>Three tiers, answering different questions (North Star section 14):
 *
 * <ol>
 *   <li><b>Caffeine</b> -- "can I avoid touching the database at all?"
 *   <li><b>The read_model table</b> -- "can I avoid re-assembling this from normalized tables?"
 *   <li><b>The assembler</b> -- the expensive path, taken only when the first two miss.
 * </ol>
 *
 * <p>A cold process therefore costs one indexed read per article rather than a full re-assembly with
 * its Markdown parse, graph walk and neighbour queries. The application stays correct with both tiers
 * empty; they are optimizations, and the canonical article is the only source of truth.
 */
public interface ReadModelService {

    /**
     * The public article read path.
     *
     * <p>Only published articles are served, and an unpublished one is reported as absent rather than
     * forbidden -- a 403 would confirm the slug to anyone guessing.
     */
    ArticleReadModel articleBySlug(Slug slug);

    /**
     * Rebuilds and stores the read model for an article.
     *
     * <p>Called by ingestion after canonical content changes, so that the first reader of a
     * newly-published article gets a materialized model rather than paying to build it.
     */
    ArticleReadModel materialize(Article article);

    void remove(UUID articleId);
}
