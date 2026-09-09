package com.knowledge.platform.graph.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bounds on graph traversal.
 *
 * <p>Every one of these is a limit, not a tuning knob. A knowledge graph is densely connected, and an
 * unbounded traversal from a well-linked article reaches most of the corpus -- which is both a slow
 * query and a useless answer.
 *
 * @param maxDepth deepest neighbourhood a caller may request
 * @param maxNodes hard cap on nodes returned, whatever the depth
 * @param maxSuggestionsPerArticle how many inferred edges to keep per article
 */
@ConfigurationProperties(prefix = "knowledge.graph")
public record GraphProperties(int maxDepth, int maxNodes, int maxSuggestionsPerArticle) {

    public GraphProperties {
        maxDepth = maxDepth <= 0 ? 3 : maxDepth;
        maxNodes = maxNodes <= 0 ? 150 : maxNodes;
        maxSuggestionsPerArticle = maxSuggestionsPerArticle <= 0 ? 10 : maxSuggestionsPerArticle;
    }
}
