package com.knowledge.platform.search.model.request;

/**
 * A search as the caller expressed it.
 *
 * @param query free text, possibly natural language
 * @param understand override for whether the SLM is consulted. Null lets the analyzer decide, which
 *     is the normal case; an explicit value exists so a client can force deterministic retrieval and
 *     so tests can pin the behaviour without reasoning about the heuristic.
 */
public record SearchRequest(String query, Integer page, Integer size, Boolean understand) {
}
