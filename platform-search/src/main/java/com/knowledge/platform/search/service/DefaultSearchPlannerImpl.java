package com.knowledge.platform.search.service;

import com.knowledge.platform.ai.model.dto.SearchIntent;
import com.knowledge.platform.ai.service.TextEmbeddingModel;
import com.knowledge.platform.author.model.entity.Author;
import com.knowledge.platform.author.service.AuthorService;
import com.knowledge.platform.search.model.dto.SearchFilters;
import com.knowledge.platform.search.model.dto.SearchMode;
import com.knowledge.platform.search.model.dto.SearchPlan;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Default SearchPlanner.
 *
 * <p>See {@link SearchPlanner} for what this provides and why it exists.
 */
@Service
public class DefaultSearchPlannerImpl implements SearchPlanner {

    private final AuthorService authorService;
    private final TextEmbeddingModel embeddingModel;
    private final SearchProperties properties;

    public DefaultSearchPlannerImpl(
            AuthorService authorService,
            TextEmbeddingModel embeddingModel,
            SearchProperties properties) {
        this.authorService = authorService;
        this.embeddingModel = embeddingModel;
        this.properties = properties;
    }

    @Override
    public SearchPlan plan(SearchIntent intent, boolean usedQueryUnderstanding) {
        boolean authorRequested = intent.author() != null && !intent.author().isBlank();
        List<UUID> authorIds = authorRequested ? resolveAuthors(intent.author()) : List.of();

        SearchFilters filters = new SearchFilters(
                authorIds,
                intent.topics(),
                intent.tags(),
                toInstantStartOfDay(intent.dateRange().from()),
                toInstantEndOfDay(intent.dateRange().to()));

        boolean unsatisfiable = filters.hasUnsatisfiableAuthorFilter(authorRequested);

        return new SearchPlan(
                intent.queryText(),
                filters,
                resolveMode(intent),
                properties.candidateLimit(),
                unsatisfiable,
                usedQueryUnderstanding);
    }

    private List<UUID> resolveAuthors(String name) {
        return authorService.resolveByName(name).stream().map(Author::getId).toList();
    }

    /**
     * The plan's mode, constrained by what the system can currently do.
     *
     * <p>A HYBRID or SEMANTIC intent degrades to LEXICAL when no embedding provider is available.
     * Planning semantic retrieval that cannot run would produce an empty contribution and, worse, a
     * ranking whose semantic weight silently applied to nothing.
     */
    private SearchMode resolveMode(SearchIntent intent) {
        boolean semanticAvailable = embeddingModel.isAvailable();
        return switch (intent.mode()) {
            case LEXICAL -> SearchMode.LEXICAL;
            case SEMANTIC -> semanticAvailable ? SearchMode.SEMANTIC : SearchMode.LEXICAL;
            case HYBRID -> semanticAvailable ? SearchMode.HYBRID : SearchMode.LEXICAL;
        };
    }

    /** A date range from a query is a day, not an instant; the bounds cover the whole day in UTC. */
    private Instant toInstantStartOfDay(java.time.LocalDate date) {
        return date == null ? null : date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private Instant toInstantEndOfDay(java.time.LocalDate date) {
        return date == null ? null : date.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);
    }
}
