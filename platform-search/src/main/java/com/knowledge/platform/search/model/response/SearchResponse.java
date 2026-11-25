package com.knowledge.platform.search.model.response;

import com.knowledge.platform.search.model.dto.SearchHit;
import com.knowledge.platform.search.model.dto.SearchMode;
import java.util.List;

/**
 * A page of ranked results, with the reasoning that produced them.
 *
 * <p>{@code interpretation} and {@code mode} are returned deliberately. A reader who searched for
 * "kubernetes networking by harsh" and got nothing should be able to see that the platform read it as
 * an author filter for someone it could not find -- rather than concluding the search is broken. It
 * is also what makes the search path debuggable in production without a log dive.
 */
public record SearchResponse(
        List<SearchHit> results,
        long totalResults,
        int page,
        int size,
        SearchMode mode,
        boolean usedQueryUnderstanding,
        Interpretation interpretation,
        long tookMillis) {

    public SearchResponse {
        results = results == null ? List.of() : List.copyOf(results);
    }

    /** How the query was understood, in the reader's terms rather than the model's. */
    public record Interpretation(
            String queryText,
            String author,
            List<String> topics,
            List<String> tags,
            boolean authorResolved,
            java.time.Instant publishedAfter,
            java.time.Instant publishedBefore) {

        public Interpretation {
            topics = topics == null ? List.of() : List.copyOf(topics);
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }
}
