package com.knowledge.platform.search.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The analyzer is what keeps the SLM optional. Every query it sends to the model costs a reader
 * hundreds of milliseconds in a search box, so the bar for sending one is deliberately high.
 */
class QueryAnalyzerTest {

    private final QueryAnalyzer analyzer = new DefaultQueryAnalyzerImpl();

    @ParameterizedTest
    @DisplayName("natural-language questions are worth interpreting")
    @ValueSource(strings = {
            "What has Harsh written about Kubernetes networking",
            "articles about postgres indexing written by someone",
            "how do I compare pgvector and elasticsearch",
            "what is the difference between HNSW and IVFFlat",
            "recent articles on database replication"
    })
    void sendsRelationalQueriesToTheModel(String query) {
        assertThat(analyzer.warrantsQueryUnderstanding(query)).isTrue();
    }

    @ParameterizedTest
    @DisplayName("short keyword queries have no structure to interpret")
    @ValueSource(strings = {"kubernetes", "postgres indexing", "hnsw index tuning"})
    void bypassesTheModelForShortQueries(String query) {
        assertThat(analyzer.warrantsQueryUnderstanding(query)).isFalse();
    }

    @Test
    @DisplayName("explicit field syntax is already precise; a model could only degrade it")
    void bypassesTheModelForExplicitFieldSyntax() {
        assertThat(analyzer.warrantsQueryUnderstanding("kubernetes networking by:harsh")).isFalse();
        assertThat(analyzer.warrantsQueryUnderstanding("indexes tag:postgres topic:databases")).isFalse();
    }

    @Test
    @DisplayName("a long keyword list is still just keywords")
    void bypassesTheModelForLongQueriesWithNoRelationalWords() {
        assertThat(analyzer.warrantsQueryUnderstanding(
                "postgres pgvector hnsw ivfflat cosine similarity index tuning")).isFalse();
    }

    @Test
    void bypassesTheModelForEmptyInput() {
        assertThat(analyzer.warrantsQueryUnderstanding(null)).isFalse();
        assertThat(analyzer.warrantsQueryUnderstanding("   ")).isFalse();
    }
}
