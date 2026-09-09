package com.knowledge.platform.search.model.dto;

import java.util.UUID;

/**
 * One article proposed by one retriever, with that retriever's own score.
 *
 * <p>Scores are <em>not</em> comparable across retrievers -- {@code ts_rank_cd} and cosine similarity
 * live on unrelated scales -- which is precisely why they are kept separate here and combined only by
 * the ranker. Adding them together at retrieval time would silently make whichever scale happens to
 * be larger the dominant signal.
 */
public record RetrievalCandidate(UUID articleId, double score, Source source) {

    /** Which retrieval family proposed a candidate. */
    public enum Source {
        FULL_TEXT,
        VECTOR,
        METADATA
    }
}
