package com.knowledge.platform.search.service;



/**
 * Decides whether a query needs the SLM at all.
 *
 * <p>The North Star is explicit that the SLM must be optional and that simple queries should bypass
 * it. This is where that is enforced, and it is worth being strict: an inference call adds hundreds
 * of milliseconds to a request that a reader experiences as a search box being slow, and most
 * searches are two or three words with nothing to interpret.
 *
 * <p>A query is sent to the model only when it looks like natural language -- it is long enough to
 * contain structure, and it contains the kind of words that signal a relationship the model could
 * extract ("by", "about", "written", "related to"). A query that is already explicit field syntax
 * ({@code by:harsh}) needs no interpretation either; the heuristic analyzer handles it exactly.
 */
public interface QueryAnalyzer {

    /**
     * @return true when the query is worth an inference call
     */
    boolean warrantsQueryUnderstanding(String query);
}
