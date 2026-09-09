package com.knowledge.platform.search.retrieval;

import com.knowledge.platform.search.model.dto.RetrievalCandidate;
import com.knowledge.platform.search.model.dto.SearchPlan;
import com.knowledge.platform.search.repository.ArticleSearchDocumentRepository;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Filter-only retrieval, for queries that name constraints but no text: "everything by Harsh",
 * "articles tagged pgvector".
 *
 * <p>Without this, such a query would reach the lexical retriever, match nothing, and return an
 * empty page -- which is the wrong answer to a perfectly well-formed browse request.
 */
@Component
public class MetadataRetriever implements Retriever {

    private final ArticleSearchDocumentRepository searchDocuments;

    public MetadataRetriever(ArticleSearchDocumentRepository searchDocuments) {
        this.searchDocuments = searchDocuments;
    }

    @Override
    public boolean supports(SearchPlan plan) {
        return plan.isFilterOnly() && !plan.filters().isEmpty();
    }

    @Override
    public List<RetrievalCandidate> retrieve(SearchPlan plan) {
        return searchDocuments.searchByMetadata(plan.filters(), plan.candidateLimit());
    }

    @Override
    public String name() {
        return "metadata";
    }
}
