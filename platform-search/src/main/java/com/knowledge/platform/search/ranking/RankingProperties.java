package com.knowledge.platform.search.ranking;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ranking weights.
 *
 * <p>Configuration rather than constants scattered through the ranker, because relevance tuning is an
 * ongoing empirical exercise and every change should be a config diff that can be reviewed, deployed
 * and reverted on its own. It also makes ranking reproducible: given a query, a corpus and these
 * weights, the output is deterministic.
 *
 * @param lexicalWeight weight on normalized full-text rank
 * @param semanticWeight weight on cosine similarity
 * @param authorMatchBoost added when the article's author was named in the query
 * @param topicMatchBoost added per matched topic
 * @param tagMatchBoost added per matched tag
 * @param recencyWeight weight on a decay curve over publication age
 * @param recencyHalfLife age at which the recency contribution halves
 */
@ConfigurationProperties(prefix = "knowledge.search.ranking")
public record RankingProperties(
        double lexicalWeight,
        double semanticWeight,
        double authorMatchBoost,
        double topicMatchBoost,
        double tagMatchBoost,
        double recencyWeight,
        Duration recencyHalfLife) {

    public RankingProperties {
        // Defaults favour lexical slightly over semantic: on a technical corpus an exact term match
        // is usually the stronger signal, and semantic retrieval earns its place mainly by finding
        // what lexical search misses entirely rather than by outranking it.
        lexicalWeight = lexicalWeight == 0 ? 1.0 : lexicalWeight;
        semanticWeight = semanticWeight == 0 ? 0.8 : semanticWeight;
        authorMatchBoost = authorMatchBoost == 0 ? 0.15 : authorMatchBoost;
        topicMatchBoost = topicMatchBoost == 0 ? 0.10 : topicMatchBoost;
        tagMatchBoost = tagMatchBoost == 0 ? 0.05 : tagMatchBoost;
        recencyWeight = recencyWeight == 0 ? 0.10 : recencyWeight;
        // Durable technical knowledge does not go stale quickly, so recency is a tiebreaker with a
        // long half-life, not a primary signal.
        recencyHalfLife = recencyHalfLife == null ? Duration.ofDays(540) : recencyHalfLife;
    }
}
