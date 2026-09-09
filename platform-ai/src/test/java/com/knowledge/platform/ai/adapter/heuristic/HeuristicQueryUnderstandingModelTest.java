package com.knowledge.platform.ai.adapter.heuristic;

import static org.assertj.core.api.Assertions.assertThat;

import com.knowledge.platform.ai.model.dto.QueryIntentType;
import com.knowledge.platform.ai.model.dto.SearchIntent;
import com.knowledge.platform.ai.model.dto.SearchMode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The deterministic analyzer is both the fallback when the SLM is unavailable and the only
 * implementation when AI is switched off, so its behaviour is a contract rather than a convenience.
 */
class HeuristicQueryUnderstandingModelTest {

    private final HeuristicQueryUnderstandingModelImpl model = new HeuristicQueryUnderstandingModelImpl();

    @Test
    void extractsExplicitAuthorSyntax() {
        SearchIntent intent = model.understand("kubernetes networking by:harsh").orElseThrow();

        assertThat(intent.author()).isEqualTo("harsh");
        assertThat(intent.queryText()).isEqualTo("kubernetes networking");
    }

    @Test
    void extractsExplicitTagAndTopicSyntax() {
        SearchIntent intent = model.understand("indexes tag:postgres topic:databases").orElseThrow();

        assertThat(intent.tags()).containsExactly("postgres");
        assertThat(intent.topics()).containsExactly("databases");
        assertThat(intent.queryText()).isEqualTo("indexes");
    }

    @Test
    void extractsHashTagShorthand() {
        SearchIntent intent = model.understand("vector search #pgvector").orElseThrow();

        assertThat(intent.tags()).containsExactly("pgvector");
    }

    @Test
    @DisplayName("a trailing capitalised name is read as an author")
    void extractsTrailingNaturalLanguageAuthor() {
        SearchIntent intent = model.understand("Kubernetes networking by Harsh").orElseThrow();

        assertThat(intent.author()).isEqualTo("Harsh");
        assertThat(intent.queryText()).isEqualTo("Kubernetes networking");
    }

    @Test
    @DisplayName("'indexed by postgres' is not an author; lowercase words are not names")
    void doesNotMistakeAPrepositionForAnAuthor() {
        SearchIntent intent = model.understand("documents indexed by postgres").orElseThrow();

        assertThat(intent.author()).isNull();
    }

    @Test
    @DisplayName("a quoted phrase is an exact-match request, so semantic retrieval is wrong for it")
    void selectsLexicalModeForQuotedPhrases() {
        assertThat(model.understand("\"connection refused\"").orElseThrow().mode())
                .isEqualTo(SearchMode.LEXICAL);
    }

    @Test
    void defaultsToHybridMode() {
        assertThat(model.understand("kubernetes networking").orElseThrow().mode())
                .isEqualTo(SearchMode.HYBRID);
    }

    @Test
    @DisplayName("a query that is only an author filter is an author search, not a text search")
    void classifiesFilterOnlyQueries() {
        assertThat(model.understand("by:harsh").orElseThrow().intent())
                .isEqualTo(QueryIntentType.AUTHOR_SEARCH);
        assertThat(model.understand("tag:pgvector").orElseThrow().intent())
                .isEqualTo(QueryIntentType.TOPIC_SEARCH);
        assertThat(model.understand("kubernetes").orElseThrow().intent())
                .isEqualTo(QueryIntentType.ARTICLE_SEARCH);
    }

    @Test
    void returnsEmptyForABlankQuery() {
        assertThat(model.understand("   ")).isEmpty();
        assertThat(model.understand(null)).isEmpty();
    }

    @Test
    @DisplayName("a query made entirely of filters still carries text, so retrieval has something to do")
    void keepsQueryTextWhenEverythingWasAFilter() {
        SearchIntent intent = model.understand("by:harsh tag:postgres").orElseThrow();

        assertThat(intent.queryText()).isNotBlank();
        assertThat(intent.author()).isEqualTo("harsh");
        assertThat(intent.tags()).isEqualTo(List.of("postgres"));
    }
}
