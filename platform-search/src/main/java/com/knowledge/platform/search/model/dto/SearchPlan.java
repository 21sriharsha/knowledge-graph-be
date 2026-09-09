package com.knowledge.platform.search.model.dto;

import com.knowledge.platform.ai.model.dto.SearchIntent;

/**
 * How the system will retrieve results. The output of the planner, and the input to the retrievers.
 *
 * <p>This is the architecture's central separation made concrete. The SLM answers "what does the user
 * mean" and produces a {@link SearchIntent}; the planner answers "how should the system retrieve it"
 * and produces this. Java code decides which retrievers run, what they filter on, and how many
 * candidates each may return.
 *
 * @param queryText the text to match, after filter phrasing has been stripped
 * @param filters resolved metadata constraints
 * @param mode which retrieval families to run
 * @param candidateLimit how many candidates each retriever may contribute before ranking
 * @param unsatisfiable true when a filter resolved to nothing, so retrieval can be skipped entirely
 * @param usedQueryUnderstanding whether an SLM contributed, reported back for observability
 */
public record SearchPlan(
        String queryText,
        SearchFilters filters,
        SearchMode mode,
        int candidateLimit,
        boolean unsatisfiable,
        boolean usedQueryUnderstanding) {

    public boolean includesLexical() {
        return mode == SearchMode.LEXICAL || mode == SearchMode.HYBRID;
    }

    public boolean includesSemantic() {
        return mode == SearchMode.SEMANTIC || mode == SearchMode.HYBRID;
    }

    /** A query with no text is a pure browse: filters alone decide the result set. */
    public boolean isFilterOnly() {
        return queryText == null || queryText.isBlank();
    }
}
