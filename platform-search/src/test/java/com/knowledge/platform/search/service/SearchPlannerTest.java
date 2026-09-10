package com.knowledge.platform.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.knowledge.platform.ai.model.dto.DateRange;
import com.knowledge.platform.ai.model.dto.QueryIntentType;
import com.knowledge.platform.ai.model.dto.SearchIntent;
import com.knowledge.platform.ai.service.TextEmbeddingModel;
import com.knowledge.platform.content.repository.TagRepository;
import com.knowledge.platform.content.repository.TopicRepository;
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

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private TagRepository tagRepository;

    private SearchPlanner planner;

    @BeforeEach
    void setUp() {
        planner = new DefaultSearchPlannerImpl(authorService, embeddingModel,
                topicRepository, tagRepository,
                new SearchProperties(100, 50, 20, 500, 25));
        // Nothing in the corpus by default. Only model-inferred taxonomy is checked against it, so
        // the deterministic tests below are unaffected -- which is the distinction worth asserting.
        lenient().when(topicRepository.findBySlugIn(any())).thenReturn(List.of());
        lenient().when(tagRepository.findBySlugIn(any())).thenReturn(List.of());
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
    @DisplayName("a lexical request the reader made explicitly is honoured")
    void honoursAnExplicitLexicalRequest() {
        // A quoted phrase or a pasted error string means exactly itself. Widening it would answer
        // a question nobody asked.
        assertThat(planner.plan(intentWithMode(com.knowledge.platform.ai.model.dto.SearchMode.LEXICAL), false).mode())
                .isEqualTo(SearchMode.LEXICAL);
    }

    @Test
    @DisplayName("a lexical choice the model guessed is widened to hybrid")
    void widensAModelChosenLexicalRequest() {
        // This assertion is the reverse of what it used to be, and it was changed because running
        // the model disproved the original. Asked "what has Anita written about postgres",
        // qwen2.5:1.5b answered LEXICAL; "postgres" and "PostgreSQL" stem to different lexemes, so
        // her two PostgreSQL articles matched nothing and a correctly resolved author filter
        // produced an empty page.
        //
        // Letting a model narrow retrieval has no upside: the extra retriever costs about 24ms and
        // only adds candidates, and the ranker already weighs lexical against semantic. Letting it
        // narrow risks exactly that failure.
        assertThat(planner.plan(intentWithMode(com.knowledge.platform.ai.model.dto.SearchMode.LEXICAL), true).mode())
                .isEqualTo(SearchMode.HYBRID);
    }

    @Test
    void convertsADateRangeToInclusiveInstantBounds() {
        SearchIntent intent = new SearchIntent(QueryIntentType.ARTICLE_SEARCH, null,
                List.of(), List.of(), "kubernetes",
                new DateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)),
                com.knowledge.platform.ai.model.dto.SearchMode.HYBRID);

        // Deterministic path: the reader gave the dates, so they are honoured verbatim.
        SearchPlan plan = planner.plan(intent, false);

        assertThat(plan.filters().publishedAfter()).isNotNull();
        assertThat(plan.filters().publishedBefore()).isNotNull();
        // The upper bound covers the whole final day, not midnight at its start.
        assertThat(plan.filters().publishedBefore()).isAfter(plan.filters().publishedAfter());
        assertThat(plan.filters().publishedBefore().toString()).startsWith("2025-12-31T23:59");
    }

    @Test
    @DisplayName("filters the reader typed are carried through exactly, known or not")
    void carriesExplicitTopicAndTagFiltersThrough() {
        SearchIntent intent = new SearchIntent(QueryIntentType.ARTICLE_SEARCH, null,
                List.of("kubernetes"), List.of("networking"), "cni",
                DateRange.unbounded(), com.knowledge.platform.ai.model.dto.SearchMode.HYBRID);

        // Deterministic path: they asked for topic:kubernetes, so that is what is searched for.
        // Quietly substituting something else would answer a different question.
        SearchPlan plan = planner.plan(intent, false);

        assertThat(plan.filters().topicSlugs()).containsExactly("kubernetes");
        assertThat(plan.filters().tagSlugs()).containsExactly("networking");
    }

    @Test
    @DisplayName("taxonomy the model invented is dropped rather than left to erase the result")
    void dropsInferredTaxonomyTheCorpusDoesNotHave() {
        SearchIntent intent = new SearchIntent(QueryIntentType.ARTICLE_SEARCH, null,
                List.of("database", "software development"), List.of("postgres"), "postgres",
                DateRange.unbounded(), com.knowledge.platform.ai.model.dto.SearchMode.HYBRID);

        // Exactly what qwen2.5:1.5b produced for "what has Anita written about postgres". No
        // article carries those topics, so as a filter they can only match nothing -- and they did,
        // erasing a correctly resolved author filter.
        SearchPlan plan = planner.plan(intent, true);

        assertThat(plan.filters().topicSlugs()).isEmpty();
        assertThat(plan.filters().tagSlugs()).isEmpty();
    }

    @Test
    @DisplayName("a resolved author's name is not also required in the article text")
    void removesAResolvedAuthorNameFromTheMatchedText() {
        com.knowledge.platform.author.model.entity.Author anita =
                org.mockito.Mockito.mock(com.knowledge.platform.author.model.entity.Author.class);
        when(anita.getId()).thenReturn(java.util.UUID.randomUUID());
        when(authorService.resolveByName("Anita")).thenReturn(List.of(anita));
        SearchIntent intent = new SearchIntent(QueryIntentType.ARTICLE_SEARCH, "Anita",
                List.of(), List.of(), "Anita about postgres",
                DateRange.unbounded(), com.knowledge.platform.ai.model.dto.SearchMode.HYBRID);

        SearchPlan plan = planner.plan(intent, true);

        // Counting the same signal twice can only subtract: full-text matching is conjunctive, so
        // requiring "Anita" in the prose excludes every article she wrote but did not sign inside.
        assertThat(plan.queryText()).isEqualTo("about postgres");
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

    @Test
    @DisplayName("a date range the model invented is dropped when the query mentions no time")
    void dropsAnInferredDateRangeWithNoTemporalQuery() {
        SearchIntent intent = new SearchIntent(QueryIntentType.ARTICLE_SEARCH, null,
                List.of(), List.of(), "postgres",
                new DateRange(LocalDate.of(2022, 1, 1), LocalDate.of(2022, 12, 31)),
                com.knowledge.platform.ai.model.dto.SearchMode.HYBRID);

        // Exactly what qwen2.5:1.5b returned for "what has Anita written about postgres". Every
        // article here is from 2026, so this silently excluded all of them while the author, the
        // tag and the text all matched -- and nothing in the response said why.
        SearchPlan plan = planner.plan(intent, true);

        assertThat(plan.filters().publishedAfter()).isNull();
        assertThat(plan.filters().publishedBefore()).isNull();
    }

    @Test
    @DisplayName("a date range is honoured when the query actually mentions a time")
    void keepsAnInferredDateRangeWhenTheQueryIsTemporal() {
        SearchIntent intent = new SearchIntent(QueryIntentType.ARTICLE_SEARCH, null,
                List.of(), List.of(), "postgres articles from 2022",
                new DateRange(LocalDate.of(2022, 1, 1), LocalDate.of(2022, 12, 31)),
                com.knowledge.platform.ai.model.dto.SearchMode.HYBRID);

        SearchPlan plan = planner.plan(intent, true);

        assertThat(plan.filters().publishedAfter()).isNotNull();
        assertThat(plan.filters().publishedBefore()).isNotNull();
    }
}
