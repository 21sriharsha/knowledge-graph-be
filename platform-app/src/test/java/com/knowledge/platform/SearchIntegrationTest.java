package com.knowledge.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.knowledge.platform.author.model.entity.Author;
import com.knowledge.platform.author.service.AuthorService;
import com.knowledge.platform.common.model.Slug;
import com.knowledge.platform.content.model.dto.ArticleUpsertCommand;
import com.knowledge.platform.content.model.entity.Article;
import com.knowledge.platform.content.service.ArticleService;
import com.knowledge.platform.search.model.dto.SearchHit;
import com.knowledge.platform.search.model.dto.SearchMode;
import com.knowledge.platform.search.model.dto.SearchSuggestion;
import com.knowledge.platform.search.model.response.SearchResponse;
import com.knowledge.platform.search.service.SearchIndexService;
import com.knowledge.platform.search.service.SearchService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Search against real PostgreSQL full-text search.
 *
 * <p>This has to be an integration test. Every interesting part -- the generated {@code tsvector} and
 * its field weighting, {@code websearch_to_tsquery}'s handling of quoted phrases and exclusions, and
 * {@code ts_rank_cd}'s cover-density scoring -- lives in the database. A test with a stubbed
 * repository would assert that the Java plumbing runs, which is the part least likely to be wrong.
 *
 * <p>AI is disabled in this profile, so the deterministic path is what runs: the analyzer decides
 * whether a query needs interpreting, the heuristic model interprets it, and retrieval is lexical.
 * That is the configuration the reliability rules say must always work.
 */
@EnabledIf(value = "com.knowledge.platform.AbstractPostgresIntegrationTest#containerRuntimeAvailable",
        disabledReason = "No container runtime; set DOCKER_HOST for rootless Podman")
class SearchIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private SearchService searchService;

    @Autowired
    private ArticleService articleService;

    @Autowired
    private AuthorService authorService;

    @Autowired
    private SearchIndexService searchIndexService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Author harsh;
    private Author other;

    @BeforeEach
    void seedCorpus() {
        jdbcTemplate.execute("""
                truncate table read_model.article_read_models, read_model.routes,
                               search.article_embeddings, search.article_search_documents,
                               graph.suggested_relationships, graph.article_links,
                               content.article_topics, content.article_tags,
                               content.article_revisions, content.articles,
                               content.topics, content.tags,
                               ingestion.events, ingestion.runs,
                               source.webhook_deliveries, source.repositories,
                               author.authors
                restart identity cascade
                """);

        harsh = authorService.findOrCreateByName("Harsh", "harsh@example.com");
        other = authorService.findOrCreateByName("Priya", "priya@example.com");

        publish("Kubernetes Networking", harsh,
                "Pods receive routable addresses and the CNI plugin implements that contract.",
                List.of("kubernetes"), List.of("cni", "networking"));
        publish("PostgreSQL Indexes", harsh,
                "B-tree indexes accelerate equality and range predicates on ordered columns.",
                List.of("postgres"), List.of("indexing"));
        publish("Service Mesh Basics", other,
                "A service mesh moves retries and timeouts out of application code into a sidecar.",
                List.of("kubernetes"), List.of("networking"));
    }

    private Article publish(String title, Author author, String body,
            List<String> topics, List<String> tags) {
        String markdown = "# " + title + "\n\n" + body + "\n";
        Article article = articleService.upsert(new ArticleUpsertCommand(
                null, title, body, markdown, "hash-" + title.hashCode(), author.getId(),
                tags, topics, body.split("\\s+").length, 1, true, null)).article();
        // Index directly: this test is about retrieval, not about the ingestion pipeline.
        searchIndexService.index(article, title + " " + body, author.getDisplayName());
        return article;
    }

    @Test
    @DisplayName("suggestions match a half-typed word, which full-text search cannot")
    void suggestsOnAPartialWord() {
        // The whole reason suggestions exist: to_tsvector stores whole stemmed lexemes, so the
        // lexical index has nothing to match until the reader finishes the word.
        assertThat(searchService.search("kube", 0, 10, false).results()).isEmpty();

        assertThat(searchService.suggest("kube", 6))
                .extracting(SearchSuggestion::slug)
                .containsExactly("kubernetes-networking");
    }

    @Test
    @DisplayName("a title starting with the term is offered before one merely containing it")
    void ranksPrefixMatchesFirst() {
        publish("Advanced PostgreSQL Tuning", harsh,
                "Planner statistics decide whether an index is used at all.",
                List.of("postgres"), List.of("tuning"));

        assertThat(searchService.suggest("postgres", 6))
                .extracting(SearchSuggestion::title)
                .containsExactly("PostgreSQL Indexes", "Advanced PostgreSQL Tuning");
    }

    @Test
    @DisplayName("an author's name surfaces their articles")
    void suggestsByAuthorName() {
        assertThat(searchService.suggest("priya", 6))
                .extracting(SearchSuggestion::slug)
                .containsExactly("service-mesh-basics");
    }

    @Test
    @DisplayName("one character is not a query")
    void refusesToGuessFromASingleCharacter() {
        assertThat(searchService.suggest("k", 6)).isEmpty();
        assertThat(searchService.suggest("  ", 6)).isEmpty();
    }

    @Test
    @DisplayName("a LIKE wildcard is matched literally, not as a wildcard")
    void treatsWildcardsAsText() {
        // Typing "%" must not quietly match the entire corpus.
        assertThat(searchService.suggest("%s", 6)).isEmpty();
    }

    @Test
    @DisplayName("unpublished articles are never suggested")
    void suggestsOnlyPublishedArticles() {
        Article draft = publish("Kubernetes Autoscaling", harsh,
                "The horizontal pod autoscaler reacts to observed metrics.",
                List.of("kubernetes"), List.of("scaling"));
        articleService.unpublish(new Slug(draft.getSlug()));

        assertThat(searchService.suggest("kubernetes", 6))
                .extracting(SearchSuggestion::slug)
                .doesNotContain("kubernetes-autoscaling");
    }

    @Test
    @DisplayName("a keyword query finds the article about it")
    void findsByKeyword() {
        SearchResponse response = searchService.search("kubernetes networking", 0, 10, null);

        assertThat(response.results()).isNotEmpty();
        assertThat(response.results().getFirst().slug()).isEqualTo("kubernetes-networking");
    }

    @Test
    @DisplayName("with AI disabled the mode is lexical and no model was consulted")
    void runsDeterministicallyWithoutAi() {
        SearchResponse response = searchService.search("kubernetes networking", 0, 10, null);

        assertThat(response.mode()).isEqualTo(SearchMode.LEXICAL);
        assertThat(response.usedQueryUnderstanding()).isFalse();
    }

    @Test
    @DisplayName("title matches outrank body matches, which is the schema's field weighting at work")
    void weightsTitleAboveBody() {
        // "Service Mesh Basics" mentions neither word in its title; the networking article does.
        SearchResponse response = searchService.search("networking", 0, 10, null);

        assertThat(response.results().getFirst().slug()).isEqualTo("kubernetes-networking");
    }

    @Test
    @DisplayName("an author filter narrows the corpus rather than merely boosting")
    void appliesAnAuthorFilter() {
        SearchResponse response = searchService.search("by:harsh networking", 0, 10, null);

        assertThat(response.results())
                .isNotEmpty()
                .allSatisfy(hit -> assertThat(hit.authorSlug()).isEqualTo("harsh"));
    }

    @Test
    @DisplayName("a filter matching nobody returns nothing, not the whole corpus")
    void returnsNothingForAnUnresolvableAuthor() {
        SearchResponse response = searchService.search("by:nobody networking", 0, 10, null);

        assertThat(response.results()).isEmpty();
        assertThat(response.totalResults()).isZero();
        assertThat(response.interpretation().authorResolved()).isFalse();
    }

    @Test
    void filtersByTag() {
        SearchResponse response = searchService.search("tag:indexing postgres", 0, 10, null);

        assertThat(response.results()).singleElement()
                .extracting(SearchHit::slug).isEqualTo("postgresql-indexes");
    }

    @Test
    @DisplayName("a query that is only filters is a browse, and still returns results")
    void supportsFilterOnlyQueries() {
        SearchResponse response = searchService.search("by:harsh", 0, 10, null);

        assertThat(response.results())
                .hasSize(2)
                .allSatisfy(hit -> assertThat(hit.authorSlug()).isEqualTo("harsh"));
    }

    @Test
    @DisplayName("the author's name is indexed, so naming them in free text matches without a filter")
    void matchesAnAuthorNameLexically() {
        SearchResponse response = searchService.search("Priya", 0, 10, null);

        assertThat(response.results()).singleElement()
                .extracting(SearchHit::slug).isEqualTo("service-mesh-basics");
    }

    @Test
    @DisplayName("every hit explains its score, so relevance is answerable rather than opaque")
    void explainsEveryHit() {
        SearchResponse response = searchService.search("kubernetes", 0, 10, null);

        assertThat(response.results()).isNotEmpty().allSatisfy(hit -> {
            assertThat(hit.explanation()).isNotNull();
            assertThat(hit.explanation().matchedBy()).isNotEmpty();
            assertThat(hit.explanation().lexical()
                    + hit.explanation().semantic()
                    + hit.explanation().authorMatch()
                    + hit.explanation().topicMatch()
                    + hit.explanation().tagMatch()
                    + hit.explanation().recency())
                    .isCloseTo(hit.score(), org.assertj.core.data.Offset.offset(0.000001));
        });
    }

    @Test
    @DisplayName("unpublished articles are not retrievable")
    void excludesUnpublishedContent() {
        articleService.unpublish(Slug.of("postgresql-indexes"));

        SearchResponse response = searchService.search("postgres b-tree", 0, 10, null);

        assertThat(response.results()).isEmpty();
    }

    @Test
    @DisplayName("results are paged, and the total counts everything that matched")
    void pagesResults() {
        SearchResponse first = searchService.search("kubernetes", 0, 1, null);

        assertThat(first.results()).hasSize(1);
        assertThat(first.totalResults()).isGreaterThan(1);
    }

    @Test
    @DisplayName("a nonsense query returns nothing rather than failing")
    void returnsNothingForAnUnmatchedQuery() {
        SearchResponse response = searchService.search("quantum entanglement recipes", 0, 10, null);

        assertThat(response.results()).isEmpty();
    }

    @Test
    @DisplayName("websearch_to_tsquery accepts punctuation that would break to_tsquery")
    void toleratesArbitraryInput() {
        SearchResponse response = searchService.search("kubernetes & | ! networking ()", 0, 10, null);

        assertThat(response).isNotNull();
    }

    @Test
    void reportsHowLongTheSearchTook() {
        SearchResponse response = searchService.search("kubernetes", 0, 10, null);

        assertThat(response.tookMillis()).isGreaterThanOrEqualTo(0);
    }
}
