package com.knowledge.platform.search.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import com.knowledge.platform.search.model.dto.RetrievalCandidate;
import com.knowledge.platform.search.model.dto.SearchFilters;
import com.knowledge.platform.search.model.dto.SearchHit;
import com.knowledge.platform.search.model.dto.SearchMode;
import com.knowledge.platform.search.model.dto.SearchPlan;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ranking is the last step, and the one with the most ways to be quietly wrong. Two properties carry
 * the weight: scores from different retrievers must be normalized before they are combined, and the
 * output must be deterministic for a given query, corpus and weight set.
 */
class ResultRankerTest {

    private static final UUID ARTICLE_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ARTICLE_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID AUTHOR = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private final ResultRanker ranker = new ResultRanker(new RankingProperties(
            1.0, 0.8, 0.15, 0.10, 0.05, 0.10, Duration.ofDays(540)));

    @Test
    @DisplayName("ts_rank_cd and cosine similarity live on different scales, so both are normalized")
    void normalizesEachRetrieverAgainstItsOwnMaximum() {
        // ts_rank_cd is unbounded above; cosine similarity is capped at 1. Combined raw, the lexical
        // scale would dominate for reasons unrelated to relevance.
        List<RetrievalCandidate> candidates = List.of(
                new RetrievalCandidate(ARTICLE_A, 42.0, RetrievalCandidate.Source.FULL_TEXT),
                new RetrievalCandidate(ARTICLE_B, 21.0, RetrievalCandidate.Source.FULL_TEXT),
                new RetrievalCandidate(ARTICLE_B, 0.95, RetrievalCandidate.Source.VECTOR));

        List<SearchHit> hits = ranker.rank(plan(SearchFilters.none()), candidates, facts());

        SearchHit a = hitFor(hits, ARTICLE_A);
        SearchHit b = hitFor(hits, ARTICLE_B);

        // A was the top lexical result, so its normalized lexical contribution is the full weight.
        assertThat(a.explanation().lexical()).isEqualTo(1.0);
        // B scored half of A lexically, and its vector score was that retriever's own maximum.
        assertThat(b.explanation().lexical()).isEqualTo(0.5);
        assertThat(b.explanation().semantic()).isEqualTo(0.8);
    }

    @Test
    @DisplayName("an article found by both retrievers outranks one found by only the stronger")
    void rewardsAgreementBetweenRetrievers() {
        List<RetrievalCandidate> candidates = List.of(
                new RetrievalCandidate(ARTICLE_A, 10.0, RetrievalCandidate.Source.FULL_TEXT),
                new RetrievalCandidate(ARTICLE_B, 9.0, RetrievalCandidate.Source.FULL_TEXT),
                new RetrievalCandidate(ARTICLE_B, 1.0, RetrievalCandidate.Source.VECTOR));

        List<SearchHit> hits = ranker.rank(plan(SearchFilters.none()), candidates, facts());

        assertThat(hits).first().extracting(SearchHit::articleId).isEqualTo(ARTICLE_B);
        assertThat(hitFor(hits, ARTICLE_B).explanation().matchedBy())
                .containsExactlyInAnyOrder("FULL_TEXT", "VECTOR");
    }

    @Test
    void appliesTheAuthorBoostOnlyToTheMatchingAuthor() {
        SearchFilters filters = new SearchFilters(List.of(AUTHOR), List.of(), List.of(), null, null);
        List<RetrievalCandidate> candidates = List.of(
                new RetrievalCandidate(ARTICLE_A, 1.0, RetrievalCandidate.Source.FULL_TEXT),
                new RetrievalCandidate(ARTICLE_B, 1.0, RetrievalCandidate.Source.FULL_TEXT));

        List<SearchHit> hits = ranker.rank(plan(filters), candidates, facts());

        assertThat(hitFor(hits, ARTICLE_A).explanation().authorMatch()).isEqualTo(0.15);
        assertThat(hitFor(hits, ARTICLE_B).explanation().authorMatch()).isZero();
    }

    @Test
    void appliesTopicAndTagBoostsPerMatch() {
        SearchFilters filters = new SearchFilters(
                List.of(), List.of("kubernetes", "networking"), List.of("cni"), null, null);
        List<RetrievalCandidate> candidates =
                List.of(new RetrievalCandidate(ARTICLE_A, 1.0, RetrievalCandidate.Source.FULL_TEXT));

        List<SearchHit> hits = ranker.rank(plan(filters), candidates, facts());

        // Article A carries both topics and the tag.
        assertThat(hitFor(hits, ARTICLE_A).explanation().topicMatch()).isEqualTo(0.20);
        assertThat(hitFor(hits, ARTICLE_A).explanation().tagMatch()).isEqualTo(0.05);
    }

    @Test
    @DisplayName("recency decays smoothly rather than in buckets that jump at a boundary")
    void decaysRecencyOverTheConfiguredHalfLife() {
        Map<UUID, ResultRanker.ArticleFacts> facts = new HashMap<>();
        facts.put(ARTICLE_A, factsFor(ARTICLE_A, "a", AUTHOR, Instant.now()));
        facts.put(ARTICLE_B, factsFor(ARTICLE_B, "b", AUTHOR,
                Instant.now().minus(540, ChronoUnit.DAYS)));

        List<RetrievalCandidate> candidates = List.of(
                new RetrievalCandidate(ARTICLE_A, 1.0, RetrievalCandidate.Source.FULL_TEXT),
                new RetrievalCandidate(ARTICLE_B, 1.0, RetrievalCandidate.Source.FULL_TEXT));

        List<SearchHit> hits = ranker.rank(plan(SearchFilters.none()), candidates, facts);

        assertThat(hitFor(hits, ARTICLE_A).explanation().recency()).isEqualTo(0.10);
        // One half-life old: half the contribution, not zero.
        assertThat(hitFor(hits, ARTICLE_B).explanation().recency()).isCloseTo(0.05,
                org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("the explanation sums to the score, so a reader can be told why a result ranked")
    void explainsTheScoreItProduced() {
        SearchFilters filters = new SearchFilters(List.of(AUTHOR), List.of("kubernetes"), List.of(), null, null);
        List<RetrievalCandidate> candidates = List.of(
                new RetrievalCandidate(ARTICLE_A, 5.0, RetrievalCandidate.Source.FULL_TEXT),
                new RetrievalCandidate(ARTICLE_A, 0.8, RetrievalCandidate.Source.VECTOR));

        SearchHit hit = hitFor(ranker.rank(plan(filters), candidates, facts()), ARTICLE_A);
        SearchHit.ScoreExplanation e = hit.explanation();

        assertThat(e.lexical() + e.semantic() + e.authorMatch() + e.topicMatch()
                + e.tagMatch() + e.recency())
                .isCloseTo(hit.score(), org.assertj.core.data.Offset.offset(0.000001));
    }

    @Test
    @DisplayName("an article unpublished between retrieval and fact lookup is dropped, not shown broken")
    void dropsCandidatesWithNoFacts() {
        List<RetrievalCandidate> candidates = List.of(
                new RetrievalCandidate(ARTICLE_A, 1.0, RetrievalCandidate.Source.FULL_TEXT),
                new RetrievalCandidate(UUID.randomUUID(), 1.0, RetrievalCandidate.Source.FULL_TEXT));

        List<SearchHit> hits = ranker.rank(plan(SearchFilters.none()), candidates, facts());

        assertThat(hits).hasSize(1).first().extracting(SearchHit::articleId).isEqualTo(ARTICLE_A);
    }

    @Test
    @DisplayName("equal scores order identically on every request, so pagination is stable")
    void isDeterministicForTiedScores() {
        Map<UUID, ResultRanker.ArticleFacts> facts = new HashMap<>();
        Instant published = Instant.now().minus(10, ChronoUnit.DAYS);
        facts.put(ARTICLE_A, factsFor(ARTICLE_A, "a", AUTHOR, published));
        facts.put(ARTICLE_B, factsFor(ARTICLE_B, "b", AUTHOR, published));

        List<RetrievalCandidate> candidates = List.of(
                new RetrievalCandidate(ARTICLE_A, 1.0, RetrievalCandidate.Source.FULL_TEXT),
                new RetrievalCandidate(ARTICLE_B, 1.0, RetrievalCandidate.Source.FULL_TEXT));

        List<UUID> first = ranker.rank(plan(SearchFilters.none()), candidates, facts)
                .stream().map(SearchHit::articleId).toList();
        List<UUID> second = ranker.rank(plan(SearchFilters.none()), candidates, facts)
                .stream().map(SearchHit::articleId).toList();

        assertThat(first).isEqualTo(second);
    }

    @Test
    void returnsNothingForNoCandidates() {
        assertThat(ranker.rank(plan(SearchFilters.none()), List.of(), Map.of())).isEmpty();
    }

    private SearchPlan plan(SearchFilters filters) {
        return new SearchPlan("kubernetes networking", filters, SearchMode.HYBRID, 100, false, true);
    }

    private Map<UUID, ResultRanker.ArticleFacts> facts() {
        Map<UUID, ResultRanker.ArticleFacts> facts = new HashMap<>();
        Instant published = Instant.now();
        facts.put(ARTICLE_A, factsFor(ARTICLE_A, "article-a", AUTHOR, published));
        facts.put(ARTICLE_B, factsFor(ARTICLE_B, "article-b",
                UUID.fromString("00000000-0000-0000-0000-0000000000bb"), published));
        return facts;
    }

    private ResultRanker.ArticleFacts factsFor(
            UUID id, String slug, UUID authorId, Instant publishedAt) {
        return new ResultRanker.ArticleFacts(id, slug, "Title " + slug, "Summary", authorId,
                "harsh", "Harsh", List.of("kubernetes", "networking"), List.of("cni"),
                publishedAt, 5);
    }

    private SearchHit hitFor(List<SearchHit> hits, UUID articleId) {
        return hits.stream().filter(hit -> hit.articleId().equals(articleId)).findFirst().orElseThrow();
    }
}
