package com.knowledge.platform.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.knowledge.platform.ai.model.dto.DateRange;
import com.knowledge.platform.ai.model.dto.QueryIntentType;
import com.knowledge.platform.ai.model.dto.SearchIntent;
import com.knowledge.platform.ai.service.TextEmbeddingModel;
import com.knowledge.platform.author.model.entity.Author;
import com.knowledge.platform.author.service.AuthorService;
import com.knowledge.platform.search.model.dto.SearchMode;
import com.knowledge.platform.search.model.dto.SearchPlan;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * The planner is where the architecture's central rule becomes enforceable: the model said what the
 * reader meant, and ordinary Java decides what the system does about it -- resolving names to
 * identifiers against real data, and constraining the model's suggestions by what the system can
 * actually do.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SearchPlannerTest {

    @Mock
    private AuthorService authorService;

    @Mock
    private TextEmbeddingModel embeddingModel;

    private SearchPlanner planner;

    @BeforeEach
    void setUp() {
        planner = new DefaultSearchPlannerImpl(authorService, embeddingModel,
                new SearchProperties(100, 50, 20, 500, 25));
        when(embeddingModel.isAvailable()).thenReturn(true);
        when(authorService.resolveByName(anyString())).thenReturn(List.of());
    }

    @Test
    @DisplayName("an author named in the query is resolved to an id by the application, not the model")
    void resolvesAuthorNamesToIdentifiers() {
        Author harsh = Author.create("harsh", "Harsh");
        when(authorService.resolveByName("Harsh")).thenReturn(List.of(harsh));

        SearchPlan plan = planner.plan(intent("Harsh", "kubernetes"), true);

        assertThat(plan.filters().authorIds()).containsExactly(harsh.getId());
        assertThat(plan.unsatisfiable()).isFalse();
    }

    @Test
    @DisplayName("an author the model invented resolves to nobody, so the plan matches nothing")
    void marksAPlanUnsatisfiableWhenAnAuthorCannotBeResolved() {
        when(authorService.resolveByName("Nobody")).thenReturn(List.of());

        SearchPlan plan = planner.plan(intent("Nobody", "kubernetes"), true);

        // Not simply dropping the filter: returning the whole corpus would look to the reader like
        // the platform ignored what they asked for.
        assertThat(plan.unsatisfiable()).isTrue();
    }

    @Test
    void doesNotMarkAPlanUnsatisfiableWhenNoAuthorWasRequested() {
        SearchPlan plan = planner.plan(intent(null, "kubernetes"), false);

        assertThat(plan.unsatisfiable()).isFalse();
        assertThat(plan.filters().authorIds()).isEmpty();
    }

    @Test
    @DisplayName("a SEMANTIC intent degrades to LEXICAL when no embedding provider is available")
    void constrainsTheModeByWhatTheSystemCanDo() {
        when(embeddingModel.isAvailable()).thenReturn(false);

        assertThat(planner.plan(intentWithMode(com.knowledge.platform.ai.model.dto.SearchMode.SEMANTIC), true).mode())
                .isEqualTo(SearchMode.LEXICAL);
        assertThat(planner.plan(intentWithMode(com.knowledge.platform.ai.model.dto.SearchMode.HYBRID), true).mode())
                .isEqualTo(SearchMode.LEXICAL);
    }

    @Test
    void keepsHybridWhenEmbeddingsAreAvailable() {
        assertThat(planner.plan(intentWithMode(com.knowledge.platform.ai.model.dto.SearchMode.HYBRID), true).mode())
                .isEqualTo(SearchMode.HYBRID);
    }

    @Test
    @DisplayName("a LEXICAL intent is never upgraded, even when embeddings are available")
    void neverUpgradesAnExplicitLexicalRequest() {
        assertThat(planner.plan(intentWithMode(com.knowledge.platform.ai.model.dto.SearchMode.LEXICAL), true).mode())
                .isEqualTo(SearchMode.LEXICAL);
    }

    @Test
    void convertsADateRangeToInclusiveInstantBounds() {
        SearchIntent intent = new SearchIntent(QueryIntentType.ARTICLE_SEARCH, null,
                List.of(), List.of(), "kubernetes",
                new DateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)),
                com.knowledge.platform.ai.model.dto.SearchMode.HYBRID);

        SearchPlan plan = planner.plan(intent, true);

        assertThat(plan.filters().publishedAfter()).isNotNull();
        assertThat(plan.filters().publishedBefore()).isNotNull();
        // The upper bound covers the whole final day, not midnight at its start.
        assertThat(plan.filters().publishedBefore()).isAfter(plan.filters().publishedAfter());
        assertThat(plan.filters().publishedBefore().toString()).startsWith("2025-12-31T23:59");
    }

    @Test
    void carriesTopicAndTagFiltersThrough() {
        SearchIntent intent = new SearchIntent(QueryIntentType.ARTICLE_SEARCH, null,
                List.of("kubernetes"), List.of("networking"), "cni",
                DateRange.unbounded(), com.knowledge.platform.ai.model.dto.SearchMode.HYBRID);

        SearchPlan plan = planner.plan(intent, true);

        assertThat(plan.filters().topicSlugs()).containsExactly("kubernetes");
        assertThat(plan.filters().tagSlugs()).containsExactly("networking");
    }

    @Test
    @DisplayName("a query with no text is a browse: retrieval runs on filters alone")
    void identifiesFilterOnlyPlans() {
        Author harsh = Author.create("harsh", "Harsh");
        when(authorService.resolveByName("Harsh")).thenReturn(List.of(harsh));

        SearchPlan plan = planner.plan(intent("Harsh", ""), true);

        assertThat(plan.isFilterOnly()).isTrue();
    }

    private SearchIntent intent(String author, String queryText) {
        return new SearchIntent(QueryIntentType.ARTICLE_SEARCH, author, List.of(), List.of(),
                queryText, DateRange.unbounded(),
                com.knowledge.platform.ai.model.dto.SearchMode.HYBRID);
    }

    private SearchIntent intentWithMode(com.knowledge.platform.ai.model.dto.SearchMode mode) {
        return new SearchIntent(QueryIntentType.ARTICLE_SEARCH, null, List.of(), List.of(),
                "kubernetes", DateRange.unbounded(), mode);
    }
}
