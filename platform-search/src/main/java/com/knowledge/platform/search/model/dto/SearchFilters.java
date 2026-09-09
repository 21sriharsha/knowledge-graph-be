package com.knowledge.platform.search.model.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Resolved metadata constraints for a search.
 *
 * <p>Note the types: author is a resolved {@code UUID}, not the name the model produced. Resolution
 * from "Harsh" to an author id happens in the planner, using the author module's own service. This is
 * the concrete form of the rule that the SLM never selects records -- by the time anything reaches
 * retrieval, every identifier has been resolved by application code against real data.
 */
public record SearchFilters(
        List<UUID> authorIds,
        List<String> topicSlugs,
        List<String> tagSlugs,
        Instant publishedAfter,
        Instant publishedBefore) {

    public SearchFilters {
        authorIds = authorIds == null ? List.of() : List.copyOf(authorIds);
        topicSlugs = topicSlugs == null ? List.of() : List.copyOf(topicSlugs);
        tagSlugs = tagSlugs == null ? List.of() : List.copyOf(tagSlugs);
    }

    public static SearchFilters none() {
        return new SearchFilters(List.of(), List.of(), List.of(), null, null);
    }

    public boolean isEmpty() {
        return authorIds.isEmpty() && topicSlugs.isEmpty() && tagSlugs.isEmpty()
                && publishedAfter == null && publishedBefore == null;
    }

    /**
     * Whether the query named an author that resolved to nobody.
     *
     * <p>Distinguishing "no author filter" from "an author filter matching nothing" matters: the
     * first should return everything, the second should return nothing. Treating them alike is how a
     * search for a misspelt name returns the entire corpus.
     */
    public boolean hasUnsatisfiableAuthorFilter(boolean authorWasRequested) {
        return authorWasRequested && authorIds.isEmpty();
    }
}
