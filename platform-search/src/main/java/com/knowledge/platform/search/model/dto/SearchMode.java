package com.knowledge.platform.search.model.dto;

/**
 * Which retrieval families a plan will use.
 *
 * <p>Distinct from {@link com.knowledge.platform.ai.model.dto.SearchMode}, which is what the model
 * <em>suggested</em>. This is what the planner <em>decided</em>, after accounting for whether
 * embeddings are actually available. Collapsing the two would let a model's suggestion silently
 * become a retrieval decision, which is exactly the boundary this architecture exists to keep.
 */
public enum SearchMode {
    LEXICAL,
    SEMANTIC,
    HYBRID
}
