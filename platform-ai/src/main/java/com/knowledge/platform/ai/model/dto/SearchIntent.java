package com.knowledge.platform.ai.model.dto;

import java.util.List;

/**
 * The structured interpretation of a natural-language query.
 *
 * <p>This is the entire contract between the AI module and the rest of the system, and the reason
 * the architecture's central rule -- <em>the SLM interprets, search retrieves</em> -- is enforceable
 * rather than aspirational. The model produces this record and nothing else: no SQL, no repository
 * access, no ranking, no choice of index. Everything downstream is deterministic Java operating on
 * these fields.
 *
 * <p>Every instance reaching the planner has been through
 * {@link com.knowledge.platform.ai.service.SearchIntentValidator}, because model output is untrusted
 * input.
 *
 * @param intent what kind of thing is being looked for
 * @param author an author named in the query, as written; resolution to an identity is the
 *     application's job, not the model's
 * @param topics subject areas mentioned
 * @param tags specific labels mentioned
 * @param queryText the part of the query to actually match against text, with filter phrasing
 *     stripped -- "articles by Harsh about Kubernetes networking" reduces to "Kubernetes networking"
 * @param dateRange an optional publication window
 * @param mode which retrieval families to draw on
 */
public record SearchIntent(
        QueryIntentType intent,
        String author,
        List<String> topics,
        List<String> tags,
        String queryText,
        DateRange dateRange,
        SearchMode mode) {

    public SearchIntent {
        // Null elements are stripped rather than copied. This record is the deserialization target
        // for language-model output, and a JSON list containing a null would make List.copyOf throw
        // before the validator ever saw it -- turning a recoverable bad response into a failed search.
        topics = immutableWithoutNulls(topics);
        tags = immutableWithoutNulls(tags);
        dateRange = dateRange == null ? DateRange.unbounded() : dateRange;
    }

    private static List<String> immutableWithoutNulls(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().filter(java.util.Objects::nonNull).toList();
    }

    /**
     * The intent for a query nothing was understood about: match the raw text, filter on nothing.
     *
     * <p>This is what the search path uses when the SLM is disabled, unavailable, or produced
     * something that failed validation -- which is why an unavailable model degrades search quality
     * rather than breaking search.
     */
    public static SearchIntent literal(String queryText) {
        return new SearchIntent(QueryIntentType.ARTICLE_SEARCH, null, List.of(), List.of(),
                queryText, DateRange.unbounded(), SearchMode.HYBRID);
    }

    /** Whether any structured filter was extracted, as opposed to plain text to match. */
    public boolean hasFilters() {
        return author != null || !topics.isEmpty() || !tags.isEmpty() || !dateRange().isUnbounded();
    }
}
