package com.knowledge.platform.search.model.dto;

/**
 * One row of a search box's suggestion list.
 *
 * <p>Deliberately thinner than {@link SearchHit}: a suggestion is an offer to navigate, not a
 * result. It carries enough to tell two similar titles apart and nothing about scoring -- there is
 * no ranking to explain, because suggestions are matched, not ranked.
 *
 * @param topic the article's primary topic, or null when it has none
 */
public record SearchSuggestion(String slug, String title, String authorName, String topic) {
}
