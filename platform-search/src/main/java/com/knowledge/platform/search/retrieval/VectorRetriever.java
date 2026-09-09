package com.knowledge.platform.search.retrieval;

import com.knowledge.platform.ai.service.TextEmbeddingModel;
import com.knowledge.platform.search.model.dto.RetrievalCandidate;
import com.knowledge.platform.search.model.dto.SearchPlan;
import com.knowledge.platform.search.repository.ArticleEmbeddingRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * pgvector similarity retrieval. The semantic half of hybrid search.
 *
 * <p>Requires the embedding provider to be up, because the <em>query</em> has to be embedded before
 * it can be compared. When it is not, this retriever contributes nothing and search continues on
 * lexical results alone -- degraded recall rather than a failed request.
 */
@Component
public class VectorRetriever implements Retriever {

    private final ArticleEmbeddingRepository embeddings;
    private final TextEmbeddingModel embeddingModel;

    public VectorRetriever(
            ArticleEmbeddingRepository embeddings, TextEmbeddingModel embeddingModel) {
        this.embeddings = embeddings;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public boolean supports(SearchPlan plan) {
        return plan.includesSemantic() && !plan.isFilterOnly() && embeddingModel.isAvailable();
    }

    @Override
    public List<RetrievalCandidate> retrieve(SearchPlan plan) {
        Optional<float[]> queryVector = embeddingModel.embed(plan.queryText());
        return queryVector
                .map(vector -> embeddings.searchSimilar(vector, plan.filters(), plan.candidateLimit()))
                .orElseGet(List::of);
    }

    @Override
    public String name() {
        return "vector";
    }
}
