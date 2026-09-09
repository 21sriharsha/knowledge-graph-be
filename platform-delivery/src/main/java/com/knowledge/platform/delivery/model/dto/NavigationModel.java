package com.knowledge.platform.delivery.model.dto;

import java.util.List;

/**
 * Site-wide navigation, precomputed.
 *
 * <p>Rendered into every page's chrome, so it is the single most-requested thing in the application
 * and the one most worth never recomputing per request.
 */
public record NavigationModel(
        List<NavigationEntry> topics, List<NavigationEntry> authors, long publishedArticleCount) {

    public NavigationModel {
        topics = topics == null ? List.of() : List.copyOf(topics);
        authors = authors == null ? List.of() : List.copyOf(authors);
    }

    public record NavigationEntry(String label, String slug, String path, long articleCount) {
    }
}
