package com.knowledge.platform.graph.service;

import com.knowledge.platform.content.model.entity.Article;
import java.util.List;
import java.util.UUID;

/**
 * Derives suggested relationships from shared taxonomy.
 *
 * <p>Deliberately not an AI feature. Shared tags and topics are an explicit, explainable signal that
 * the author already provided, and computing it costs a query rather than an inference call. The
 * semantic-similarity kind exists in the model for a future pass over embeddings, but nothing here
 * needs a model to work.
 *
 * <p>Scores are the Jaccard overlap of the two articles' taxonomies, which keeps them comparable
 * across articles: an article with thirty tags does not out-score a focused one simply by having more
 * chances to overlap.
 */
public interface RelationshipSuggestionService {

    /**
     * Rebuilds the suggestions for one article against a set of candidates.
     *
     * <p>Replace rather than merge, because suggestions are derived data: an article whose tags
     * changed should lose the suggestions those tags produced.
     */
    int rebuildFor(Article article, List<Article> candidates);

    void removeFor(UUID articleId);
}
