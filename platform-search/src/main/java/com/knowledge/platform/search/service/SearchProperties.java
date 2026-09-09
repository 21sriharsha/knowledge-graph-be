package com.knowledge.platform.search.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bounds on search execution.
 *
 * @param candidateLimit how many candidates each retriever may contribute. Over-fetching relative to
 *     the page size is intentional -- the ranker needs a pool to reorder, or hybrid ranking would only
 *     ever reshuffle the lexical retriever's top page.
 * @param maxPageSize hard cap on what a caller may request
 * @param defaultPageSize page size when none is given
 * @param maxQueryLength queries longer than this are rejected rather than truncated
 * @param embeddingBatchSize how many missing embeddings one backfill pass may schedule. Each
 *     is an inference call, so draining a large backlog in one pass would saturate the bounded job
 *     executor and starve the ingestion work readers are waiting on.
 */
@ConfigurationProperties(prefix = "knowledge.search")
public record SearchProperties(
        int candidateLimit, int maxPageSize, int defaultPageSize, int maxQueryLength,
        int embeddingBatchSize) {

    public SearchProperties {
        candidateLimit = candidateLimit <= 0 ? 100 : candidateLimit;
        maxPageSize = maxPageSize <= 0 ? 50 : maxPageSize;
        defaultPageSize = defaultPageSize <= 0 ? 20 : defaultPageSize;
        maxQueryLength = maxQueryLength <= 0 ? 500 : maxQueryLength;
        embeddingBatchSize = embeddingBatchSize <= 0 ? 25 : embeddingBatchSize;
    }
}
