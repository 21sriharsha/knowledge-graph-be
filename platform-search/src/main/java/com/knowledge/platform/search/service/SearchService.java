package com.knowledge.platform.search.service;

import com.knowledge.platform.ai.model.dto.SearchIntent;
import com.knowledge.platform.search.model.dto.SearchSuggestion;
import com.knowledge.platform.search.model.response.SearchResponse;
import java.util.List;

/**
 * The search module's application boundary, and the whole flow in one place:
 *
 * <pre>
 *   query -> analyzer -> deterministic parse OR SLM -> SearchIntent
 *         -> planner -> retrievers -> ranker -> response
 * </pre>
 *
 * <p>Note that the analyzer chooses <em>which</em> interpreter runs, not whether one runs at all.
 * A query that does not warrant an inference call is still parsed deterministically, so explicit
 * field syntax is honoured either way.
 *
 * <p>Read this method top to bottom and the architecture's central claim is visible: the model
 * contributes exactly one thing, a {@link SearchIntent}, and every subsequent step is deterministic
 * Java. Nothing here can consult the model again, and nothing downstream of the planner knows the
 * model exists.
 */
public interface SearchService {

    SearchResponse search(String query, int page, int requestedSize, Boolean understandOverride);

    /**
     * Suggestions for a partially typed query.
     *
     * <p>Not a shortened search. Retrieval above is lexical and semantic over stemmed whole words,
     * which by construction matches nothing until a word is finished -- the exact window in which a
     * typeahead has to be useful. Suggestions therefore match prefixes and substrings of titles and
     * author names, deterministically and without the model: there is nothing for an interpreter to
     * interpret in three characters, and it would spend an inference call per keystroke finding out.
     */
    List<SearchSuggestion> suggest(String query, int requestedLimit);
}
