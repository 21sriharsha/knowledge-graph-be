package com.knowledge.platform.search.ranking;

import com.knowledge.platform.search.model.dto.RetrievalCandidate;
import com.knowledge.platform.search.model.dto.SearchHit;
import com.knowledge.platform.search.model.dto.SearchPlan;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Combines the retrievers' candidates into one ordered result set.
 *
 * <p>Two things make this correct rather than merely plausible:
 *
 * <p><b>Scores are normalized per retriever before they are combined.</b> {@code ts_rank_cd} has no
 * fixed upper bound and cosine similarity lives in [0, 1]; adding them raw would let the lexical
 * scale dominate for reasons that have nothing to do with relevance. Each retriever's scores are
 * scaled against that retriever's own maximum for this query, so the configured weights mean what
 * they say.
 *
 * <p><b>Ranking is deterministic.</b> Given the same query, corpus and weights the output order is
 * fixed, with article id as the final tiebreaker so that equal scores do not produce a different page
 * on every request.
 *
 * <p>This is also where the AI boundary ends for good: the model never reaches this code, and nothing
 * here consults it. The SLM interpreted; this ranks.
 */
@Component
public class ResultRanker {

    private final RankingProperties properties;

    public ResultRanker(RankingProperties properties) {
        this.properties = properties;
    }

    /**
     * Ranks candidates.
     *
     * @param candidates every retriever's output, un-merged
     * @param articleFacts the metadata each candidate needs for boosting, keyed by article id
     */
    public List<SearchHit> rank(
            SearchPlan plan, List<RetrievalCandidate> candidates, Map<UUID, ArticleFacts> articleFacts) {

        Map<RetrievalCandidate.Source, Double> maxima = maximaBySource(candidates);
        Map<UUID, Map<RetrievalCandidate.Source, Double>> normalized = new HashMap<>();
        Map<UUID, Set<String>> matchedBy = new HashMap<>();

        for (RetrievalCandidate candidate : candidates) {
            double max = maxima.getOrDefault(candidate.source(), 1.0);
            double scaled = max <= 0 ? 0 : candidate.score() / max;
            normalized
                    .computeIfAbsent(candidate.articleId(), id -> new EnumMap<>(RetrievalCandidate.Source.class))
                    // An article can be proposed twice by the same retriever only through a bug, but
                    // keeping the better score is the safe merge either way.
                    .merge(candidate.source(), scaled, Math::max);
            matchedBy.computeIfAbsent(candidate.articleId(), id -> new LinkedHashSet<>())
                    .add(candidate.source().name());
        }

        Instant now = Instant.now();
        List<SearchHit> hits = new ArrayList<>();
        for (Map.Entry<UUID, Map<RetrievalCandidate.Source, Double>> entry : normalized.entrySet()) {
            ArticleFacts facts = articleFacts.get(entry.getKey());
            if (facts == null) {
                // The article was retrieved but is no longer readable -- unpublished between the
                // retrieval query and the fact lookup. Dropping it is correct; showing a result that
                // 404s is worse than showing one fewer result.
                continue;
            }
            hits.add(score(plan, facts, entry.getValue(), matchedBy.get(entry.getKey()), now));
        }

        hits.sort(Comparator.comparingDouble(SearchHit::score).reversed()
                .thenComparing(hit -> hit.articleId().toString()));
        return hits;
    }

    private SearchHit score(
            SearchPlan plan,
            ArticleFacts facts,
            Map<RetrievalCandidate.Source, Double> normalized,
            Set<String> matchedBy,
            Instant now) {

        double lexical = properties.lexicalWeight()
                * normalized.getOrDefault(RetrievalCandidate.Source.FULL_TEXT,
                        normalized.getOrDefault(RetrievalCandidate.Source.METADATA, 0.0));
        double semantic = properties.semanticWeight()
                * normalized.getOrDefault(RetrievalCandidate.Source.VECTOR, 0.0);

        double authorMatch = plan.filters().authorIds().contains(facts.authorId())
                ? properties.authorMatchBoost() : 0.0;
        double topicMatch = properties.topicMatchBoost()
                * countMatches(plan.filters().topicSlugs(), facts.topicSlugs());
        double tagMatch = properties.tagMatchBoost()
                * countMatches(plan.filters().tagSlugs(), facts.tagSlugs());
        double recency = properties.recencyWeight() * recencyScore(facts.publishedAt(), now);

        double total = lexical + semantic + authorMatch + topicMatch + tagMatch + recency;

        return new SearchHit(
                facts.articleId(), facts.slug(), facts.title(), facts.summary(),
                facts.authorSlug(), facts.authorName(), facts.topicSlugs(), facts.tagSlugs(),
                facts.publishedAt(), facts.readingTimeMinutes(), total,
                new SearchHit.ScoreExplanation(lexical, semantic, authorMatch, topicMatch, tagMatch,
                        recency, List.copyOf(matchedBy)));
    }

    /**
     * Exponential decay with a configured half-life, so recency contributes on a smooth curve rather
     * than through age buckets that make a result jump position at an arbitrary boundary.
     */
    private double recencyScore(Instant publishedAt, Instant now) {
        if (publishedAt == null) {
            return 0.0;
        }
        long ageDays = Duration.between(publishedAt, now).toDays();
        if (ageDays <= 0) {
            return 1.0;
        }
        double halfLifeDays = Math.max(1, properties.recencyHalfLife().toDays());
        return Math.pow(0.5, ageDays / halfLifeDays);
    }

    private long countMatches(List<String> requested, List<String> actual) {
        if (requested.isEmpty() || actual.isEmpty()) {
            return 0;
        }
        return requested.stream().filter(actual::contains).count();
    }

    private Map<RetrievalCandidate.Source, Double> maximaBySource(List<RetrievalCandidate> candidates) {
        Map<RetrievalCandidate.Source, Double> maxima =
                new EnumMap<>(RetrievalCandidate.Source.class);
        for (RetrievalCandidate candidate : candidates) {
            maxima.merge(candidate.source(), candidate.score(), Math::max);
        }
        return maxima;
    }

    /**
     * The article metadata ranking needs, fetched once per query rather than per candidate.
     *
     * <p>Declared here, next to its only consumer, rather than in a shared DTO package: it exists to
     * serve ranking and would be a meaningless type anywhere else.
     */
    public record ArticleFacts(
            UUID articleId,
            String slug,
            String title,
            String summary,
            UUID authorId,
            String authorSlug,
            String authorName,
            List<String> topicSlugs,
            List<String> tagSlugs,
            Instant publishedAt,
            int readingTimeMinutes) {

        public ArticleFacts {
            topicSlugs = topicSlugs == null ? List.of() : List.copyOf(topicSlugs);
            tagSlugs = tagSlugs == null ? List.of() : List.copyOf(tagSlugs);
        }
    }
}
