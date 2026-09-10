package com.knowledge.platform.search.service;

import com.knowledge.platform.ai.model.dto.SearchIntent;
import com.knowledge.platform.ai.adapter.heuristic.HeuristicQueryUnderstandingModelImpl;
import com.knowledge.platform.ai.service.QueryUnderstandingModel;
import com.knowledge.platform.search.model.dto.RetrievalCandidate;
import com.knowledge.platform.search.model.dto.SearchHit;
import com.knowledge.platform.search.model.dto.SearchPlan;
import com.knowledge.platform.search.model.dto.SearchSuggestion;
import com.knowledge.platform.search.model.response.SearchResponse;
import com.knowledge.platform.search.ranking.ResultRanker;
import com.knowledge.platform.search.repository.ArticleFactsRepository;
import com.knowledge.platform.search.repository.ArticleSuggestionRepository;
import com.knowledge.platform.search.retrieval.Retriever;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default SearchService.
 *
 * <p>See {@link SearchService} for what this provides and why it exists.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class DefaultSearchServiceImpl implements SearchService {

    private final QueryAnalyzer queryAnalyzer;
    private final QueryUnderstandingModel queryUnderstandingModel;
    private final QueryUnderstandingModel deterministicModel;
    private final SearchPlanner searchPlanner;
    private final List<Retriever> retrievers;
    private final ResultRanker resultRanker;
    private final ArticleFactsRepository articleFacts;
    private final ArticleSuggestionRepository articleSuggestions;
    private final SearchProperties properties;
    private final MeterRegistry meterRegistry;

    public DefaultSearchServiceImpl(
            QueryAnalyzer queryAnalyzer,
            QueryUnderstandingModel queryUnderstandingModel,
            @Qualifier(HeuristicQueryUnderstandingModelImpl.BEAN_NAME)
            QueryUnderstandingModel deterministicModel,
            SearchPlanner searchPlanner,
            List<Retriever> retrievers,
            ResultRanker resultRanker,
            ArticleFactsRepository articleFacts,
            ArticleSuggestionRepository articleSuggestions,
            SearchProperties properties,
            MeterRegistry meterRegistry) {
        this.queryAnalyzer = queryAnalyzer;
        this.queryUnderstandingModel = queryUnderstandingModel;
        this.deterministicModel = deterministicModel;
        this.searchPlanner = searchPlanner;
        this.retrievers = retrievers;
        this.resultRanker = resultRanker;
        this.articleFacts = articleFacts;
        this.articleSuggestions = articleSuggestions;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public SearchResponse search(String query, int page, int requestedSize, Boolean understandOverride) {
        long startedAt = System.nanoTime();
        Timer.Sample sample = Timer.start(meterRegistry);

        String trimmed = query == null ? "" : query.trim();
        if (trimmed.length() > properties.maxQueryLength()) {
            throw new IllegalArgumentException(
                    "Query exceeds " + properties.maxQueryLength() + " characters");
        }
        int size = Math.clamp(requestedSize <= 0 ? properties.defaultPageSize() : requestedSize,
                1, properties.maxPageSize());

        Understanding understanding = understand(trimmed, understandOverride);
        SearchPlan plan = searchPlanner.plan(understanding.intent(), understanding.usedModel());

        if (plan.unsatisfiable()) {
            // A filter that resolved to nothing means the answer is genuinely nothing. Returning the
            // whole corpus instead would look to the reader like their filter was ignored.
            sample.stop(meterRegistry.timer("knowledge.search.duration", "outcome", "unsatisfiable"));
            return emptyResponse(plan, understanding, page, size, startedAt);
        }

        List<RetrievalCandidate> candidates = retrieve(plan);
        Map<UUID, ResultRanker.ArticleFacts> facts = articleFacts.findFacts(
                candidates.stream().map(RetrievalCandidate::articleId).collect(Collectors.toSet()));

        List<SearchHit> ranked = resultRanker.rank(plan, candidates, facts);
        List<SearchHit> pageOfHits = paginate(ranked, page, size);

        sample.stop(meterRegistry.timer("knowledge.search.duration",
                "outcome", "success", "mode", plan.mode().name()));

        return new SearchResponse(
                pageOfHits,
                ranked.size(),
                page,
                size,
                plan.mode(),
                plan.usedQueryUnderstanding(),
                interpretationOf(understanding.intent(), plan),
                elapsedMillis(startedAt));
    }

    /**
     * Consults the SLM only when the analyzer says the query warrants it, and falls back to a literal
     * intent whenever understanding is unavailable.
     *
     * <p>Both paths produce a valid {@link SearchIntent}, so the rest of the method has no notion of
     * "the model was down" -- the degradation is fully absorbed here.
     */
    private Understanding understand(String query, Boolean override) {
        if (query.isBlank()) {
            return new Understanding(SearchIntent.literal(query), false);
        }
        boolean shouldUnderstand = override != null
                ? override
                : queryAnalyzer.warrantsQueryUnderstanding(query);
        if (!shouldUnderstand) {
            // Bypassing the SLM is not the same as understanding nothing. Explicit field syntax --
            // by:, tag:, topic:, quoted phrases -- is parsed deterministically here, which is the
            // whole reason the heuristic analyzer exists as more than a fallback. Falling through to
            // a literal intent would silently drop the filters the reader typed and match their
            // syntax as free text instead.
            meterRegistry.counter("knowledge.search.understanding", "path", "deterministic").increment();
            return new Understanding(
                    deterministicModel.understand(query).orElseGet(() -> SearchIntent.literal(query)),
                    false);
        }

        Optional<SearchIntent> interpreted = queryUnderstandingModel.understand(query);
        if (interpreted.isEmpty()) {
            // The model was asked and did not answer -- unavailable, too slow, or its output failed
            // validation. Deterministic parsing rather than a literal intent, so the explicit
            // filters a reader typed still work when the model is down; and usedModel stays false,
            // because reporting otherwise would tell them their query was interpreted when it was
            // not.
            meterRegistry.counter("knowledge.search.understanding", "path", "unavailable").increment();
            log.debug("Query understanding produced nothing for '{}'; using deterministic parsing", query);
            return new Understanding(
                    deterministicModel.understand(query).orElseGet(() -> SearchIntent.literal(query)),
                    false);
        }
        meterRegistry.counter("knowledge.search.understanding", "path", "model").increment();
        return new Understanding(interpreted.get(), true);
    }

    private List<RetrievalCandidate> retrieve(SearchPlan plan) {
        List<RetrievalCandidate> candidates = new ArrayList<>();
        for (Retriever retriever : retrievers) {
            if (!retriever.supports(plan)) {
                continue;
            }
            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                List<RetrievalCandidate> retrieved = retriever.retrieve(plan);
                candidates.addAll(retrieved);
                sample.stop(meterRegistry.timer("knowledge.search.retrieval",
                        "retriever", retriever.name(), "outcome", "success"));
            } catch (RuntimeException e) {
                // One retrieval family failing degrades recall; it must not fail the search. A
                // pgvector index problem should still return lexical results.
                sample.stop(meterRegistry.timer("knowledge.search.retrieval",
                        "retriever", retriever.name(), "outcome", "failure"));
                log.error("Retriever '{}' failed; continuing without its results", retriever.name(), e);
            }
        }
        return candidates;
    }

    private List<SearchHit> paginate(List<SearchHit> ranked, int page, int size) {
        int from = Math.max(0, page) * size;
        if (from >= ranked.size()) {
            return List.of();
        }
        return ranked.subList(from, Math.min(from + size, ranked.size()));
    }

    @Override
    public List<SearchSuggestion> suggest(String query, int requestedLimit) {
        String term = query == null ? "" : query.trim();
        if (term.length() < MIN_SUGGESTION_LENGTH) {
            // One character matches most of the corpus, which is the same as matching nothing
            // useful. Answering with an empty list is cheaper and more honest than a random slice.
            return List.of();
        }
        int limit = Math.clamp(requestedLimit, 1, MAX_SUGGESTIONS);
        return articleSuggestions.findMatching(term, limit);
    }

    private SearchResponse emptyResponse(
            SearchPlan plan, Understanding understanding, int page, int size, long startedAt) {
        return new SearchResponse(List.of(), 0, page, size, plan.mode(),
                plan.usedQueryUnderstanding(), interpretationOf(understanding.intent(), plan),
                elapsedMillis(startedAt));
    }

    private SearchResponse.Interpretation interpretationOf(SearchIntent intent, SearchPlan plan) {
        return new SearchResponse.Interpretation(
                plan.queryText(),
                intent.author(),
                intent.topics(),
                intent.tags(),
                !plan.filters().authorIds().isEmpty());
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    /** Below this, a suggestion list is a list of everything. */
    private static final int MIN_SUGGESTION_LENGTH = 2;

    /** A typeahead the reader must scroll has stopped being a shortcut. */
    private static final int MAX_SUGGESTIONS = 10;

    /** What the understanding step produced, and whether a model was actually involved. */
    private record Understanding(SearchIntent intent, boolean usedModel) {
    }
}
