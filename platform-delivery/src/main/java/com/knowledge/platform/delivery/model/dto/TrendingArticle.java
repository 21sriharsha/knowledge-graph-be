package com.knowledge.platform.delivery.model.dto;

/**
 * An article and how often it was read in the requested window.
 *
 * <p>{@code views} is carried so the frontend can say <em>why</em> something is trending rather than
 * presenting an unexplained order. A ranked list that will not show its working asks the reader to
 * take it on faith.
 */
public record TrendingArticle(
        String slug, String title, String authorName, String topic, long views) {
}
