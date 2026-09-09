package com.knowledge.platform.search.model.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One ranked result.
 *
 * <p>{@code explanation} is part of the contract, not a debugging aid. The North Star asks that a
 * reader be able to understand why a result is relevant, and that is only possible if the per-signal
 * contributions survive ranking instead of being collapsed into a single opaque number.
 */
public record SearchHit(
        UUID articleId,
        String slug,
        String title,
        String summary,
        String authorSlug,
        String authorName,
        List<String> topics,
        List<String> tags,
        Instant publishedAt,
        int readingTimeMinutes,
        double score,
        ScoreExplanation explanation) {

    /**
     * The contributions that produced a hit's score.
     *
     * <p>Each field is the weighted contribution, not the raw signal, so they sum to {@link #score}.
     */
    public record ScoreExplanation(
            double lexical,
            double semantic,
            double authorMatch,
            double topicMatch,
            double tagMatch,
            double recency,
            List<String> matchedBy) {

        public ScoreExplanation {
            matchedBy = matchedBy == null ? List.of() : List.copyOf(matchedBy);
        }
    }
}
