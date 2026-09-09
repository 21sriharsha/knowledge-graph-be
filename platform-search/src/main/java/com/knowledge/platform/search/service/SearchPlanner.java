package com.knowledge.platform.search.service;

import com.knowledge.platform.ai.model.dto.SearchIntent;
import com.knowledge.platform.search.model.dto.SearchPlan;

/**
 * Turns an interpreted query into a retrieval plan.
 *
 * <p>This class is the architecture's load-bearing separation. The SLM said what the user meant; this
 * decides what the system does about it, in ordinary Java, against real data. Specifically:
 *
 * <ul>
 *   <li><b>Names become identifiers here.</b> The model returns the string "Harsh"; this resolves it
 *       through the author module and produces author ids. The model never selects a record, so a
 *       hallucinated author cannot filter on anything -- it resolves to nobody.
 *   <li><b>An unresolvable filter makes the plan unsatisfiable</b>, rather than being dropped.
 *       Silently discarding a filter for a misspelt author would return the whole corpus, which looks
 *       to a reader like the platform ignoring what they asked.
 *   <li><b>The mode the model suggested is a suggestion.</b> If embeddings are unavailable, a
 *       SEMANTIC intent is planned as LEXICAL. Capability, not the model, decides what actually runs.
 * </ul>
 */
public interface SearchPlanner {

    SearchPlan plan(SearchIntent intent, boolean usedQueryUnderstanding);
}
