package com.knowledge.platform.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.knowledge.platform.ai.model.dto.DateRange;
import com.knowledge.platform.ai.model.dto.QueryIntentType;
import com.knowledge.platform.ai.model.dto.SearchIntent;
import com.knowledge.platform.ai.model.dto.SearchMode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Model output is untrusted input. These tests describe the specific ways a language model produces
 * unusable structure, and what the validator does about each.
 */
class SearchIntentValidatorTest {

    private final SearchIntentValidator validator = new DefaultSearchIntentValidatorImpl();

    @Test
    void rejectsNullOutput() {
        assertThat(validator.validate(null, "kubernetes")).isEmpty();
    }

    @Test
    @DisplayName("an empty queryText falls back to the reader's original query")
    void fallsBackToTheOriginalQuery() {
        SearchIntent raw = new SearchIntent(QueryIntentType.ARTICLE_SEARCH, null, List.of(), List.of(),
                "   ", DateRange.unbounded(), SearchMode.HYBRID);

        assertThat(validator.validate(raw, "kubernetes networking"))
                .get().extracting(SearchIntent::queryText).isEqualTo("kubernetes networking");
    }

    @Test
    void rejectsWhenNeitherTheModelNorTheQueryHasUsableText() {
        SearchIntent raw = new SearchIntent(QueryIntentType.ARTICLE_SEARCH, null, List.of(), List.of(),
                null, DateRange.unbounded(), SearchMode.HYBRID);

        assertThat(validator.validate(raw, "  ")).isEmpty();
    }

    @Test
    @DisplayName("a model returning forty topics is bounded, not trusted")
    void boundsFacetCount() {
        List<String> manyTopics = IntStream.range(0, 40).mapToObj(i -> "topic" + i).toList();
        SearchIntent raw = new SearchIntent(QueryIntentType.ARTICLE_SEARCH, null, manyTopics,
                List.of(), "query", DateRange.unbounded(), SearchMode.HYBRID);

        assertThat(validator.validate(raw, "query"))
                .get().extracting(SearchIntent::topics)
                .satisfies(topics -> assertThat((List<?>) topics).hasSize(8));
    }

    @Test
    @DisplayName("case-varying duplicates would double-count in ranking, so they are collapsed")
    void deduplicatesFacetsCaseInsensitively() {
        SearchIntent raw = new SearchIntent(QueryIntentType.ARTICLE_SEARCH, null,
                Arrays.asList("Kubernetes", "kubernetes", "KUBERNETES"), List.of(),
                "query", DateRange.unbounded(), SearchMode.HYBRID);

        assertThat(validator.validate(raw, "query"))
                .get().extracting(SearchIntent::topics)
                .isEqualTo(List.of("kubernetes"));
    }

    @Test
    void discardsBlankAndOverlongFacets() {
        SearchIntent raw = new SearchIntent(QueryIntentType.ARTICLE_SEARCH, null,
                Arrays.asList("valid", "   ", "x".repeat(200), null), List.of(),
                "query", DateRange.unbounded(), SearchMode.HYBRID);

        assertThat(validator.validate(raw, "query"))
                .get().extracting(SearchIntent::topics).isEqualTo(List.of("valid"));
    }

    @Test
    @DisplayName("an inverted date range would match nothing, so it is dropped rather than applied")
    void discardsInvertedDateRanges() {
        SearchIntent raw = new SearchIntent(QueryIntentType.ARTICLE_SEARCH, null, List.of(), List.of(),
                "query", new DateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2024, 1, 1)),
                SearchMode.HYBRID);

        assertThat(validator.validate(raw, "query"))
                .get().extracting(SearchIntent::dateRange)
                .satisfies(range -> assertThat(((DateRange) range).isUnbounded()).isTrue());
    }

    @Test
    void keepsAValidDateRange() {
        DateRange range = new DateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1));
        SearchIntent raw = new SearchIntent(QueryIntentType.ARTICLE_SEARCH, null, List.of(), List.of(),
                "query", range, SearchMode.HYBRID);

        assertThat(validator.validate(raw, "query")).get()
                .extracting(SearchIntent::dateRange).isEqualTo(range);
    }

    @Test
    void defaultsMissingEnumsRatherThanFailing() {
        SearchIntent raw = new SearchIntent(null, null, List.of(), List.of(),
                "query", null, null);

        Optional<SearchIntent> validated = validator.validate(raw, "query");
        assertThat(validated).get().satisfies(intent -> {
            assertThat(intent.intent()).isEqualTo(QueryIntentType.ARTICLE_SEARCH);
            assertThat(intent.mode()).isEqualTo(SearchMode.HYBRID);
            assertThat(intent.dateRange().isUnbounded()).isTrue();
        });
    }

    @Test
    @DisplayName("a model echoing the whole prompt back is truncated, not passed to the database")
    void truncatesRunawayQueryText() {
        SearchIntent raw = new SearchIntent(QueryIntentType.ARTICLE_SEARCH, null, List.of(), List.of(),
                "x".repeat(5000), DateRange.unbounded(), SearchMode.HYBRID);

        assertThat(validator.validate(raw, "query"))
                .get().extracting(intent -> intent.queryText().length())
                .isEqualTo(500);
    }
}
