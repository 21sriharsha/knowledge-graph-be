package com.knowledge.platform.search.retrieval;

import com.knowledge.platform.search.model.dto.RetrievalCandidate;
import com.knowledge.platform.search.model.dto.SearchPlan;
import java.util.List;

/**
 * One retrieval family.
 *
 * <p>A retriever proposes candidates and scores them on its own scale. It does not rank, does not
 * merge, and does not know that other retrievers exist -- combining their output is the ranker's job,
 * and keeping that separate is what allows a retrieval family to be added or removed without
 * touching how results are ordered.
 */
public interface Retriever {

    /** Whether this retriever has anything to contribute to the plan. */
    boolean supports(SearchPlan plan);

    /** Candidates for the plan, scored on this retriever's own scale. */
    List<RetrievalCandidate> retrieve(SearchPlan plan);

    /** For metrics and diagnostics. */
    String name();
}
