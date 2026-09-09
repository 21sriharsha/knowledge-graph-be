package com.knowledge.platform.search.retrieval;

import com.knowledge.platform.search.model.dto.RetrievalCandidate;
import com.knowledge.platform.search.model.dto.SearchPlan;
import com.knowledge.platform.search.repository.ArticleSearchDocumentRepository;
import java.util.List;
import org.springframework.stereotype.Component;

/** PostgreSQL full-text retrieval. The lexical half of hybrid search. */
@Component
public class FullTextRetriever implements Retriever {

    private final ArticleSearchDocumentRepository searchDocuments;

    public FullTextRetriever(ArticleSearchDocumentRepository searchDocuments) {
        this.searchDocuments = searchDocuments;
    }

    @Override
    public boolean supports(SearchPlan plan) {
        return plan.includesLexical() && !plan.isFilterOnly();
    }

    @Override
    public List<RetrievalCandidate> retrieve(SearchPlan plan) {
        return searchDocuments.searchFullText(plan.queryText(), plan.filters(), plan.candidateLimit());
    }

    @Override
    public String name() {
        return "full-text";
    }
}
